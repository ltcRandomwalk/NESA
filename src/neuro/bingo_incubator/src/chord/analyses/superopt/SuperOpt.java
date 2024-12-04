package chord.analyses.superopt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Mode;
import chord.project.analyses.parallelizer.ParallelAnalysis;
import chord.util.Utils;

/*
 * chord.superopt.dumprels       dump lib/app input rels being processed. (default: false)
 * chord.superopt.op             perform <op> (one of: sc, ac, baseline, mcmcf, prep, test, equiv   default: mcmcf)
 * chord.superopt.eta            eta value for active coarsening (default: 0.1)
 * chord.superopt.tgttime        time taken by baseline required for MCMC perf calculation in milli seconds
 * chord.superopt.rwttime        time taken by initial rewrite with which MCMC starts (in milli seconds)
 * chord.superopt.methods        comma-separated list of methods to optimize (with the format of m.toString()) default ""
 * chord.superopt.test.sound     true if the test phase has to be conservative to be sound (default: false)
 * chord.superopt.delratio       the ratio of deletes to mutations for mcmcf (default: 2)
 * chord.superopt.libprefix      specifies what class prefixes are construed as library classes (used in ../incrsolver)
 *                               (syntax of an example: "com\\.|sun\\.")
 * chord.superopt.usefilter      make the analyses see only the program points of reachable methods (default: false) 
 * chord.superopt.deletion       [aggressive|conservative] (default: aggressive)                              
 */

@Chord(name = "superopt-java")
public class SuperOpt extends ParallelAnalysis {
	
	private boolean dumpRels;
	private boolean sound;
	private int scTrial = 0;
	private int acTrial = 0;
	private ITask analysis;
	private ITask analysisCopyInp;
	private ITask analysisCopyOp;
	private ITask analysisCheck;
	private ITask analysisFilt;
	private ArrayList<String> inRelNames;
	private ArrayList<String> outRelNames;
	private double eta;
	private boolean doSC = false;
	private boolean doAC = false;
	private boolean doBaseline = false;
	private boolean doMCMCF = false;
	private boolean doPrep = false;
	private boolean doTest = false;
	private boolean doEquiv = false;
	private String methods;
	private String filtPrefix = "";
	private long timeT = 0;
	private long timeR = 0;
	private int delRatio = 0;
	private int numWorkers = 0;
	private String superoptPath = "../incubator/src/chord/analyses/superopt";
	private String deletionStrategy;
	
	
	public void commonInit() {
		dumpRels = Utils.buildBoolProperty("chord.superopt.dumprels", false);
		sound = Utils.buildBoolProperty("chord.superopt.test.sound", false);
		methods = System.getProperty("chord.superopt.methods", "");
		boolean useFilt = Utils.buildBoolProperty("chord.superopt.usefilter", false);
		String etaVal = System.getProperty("chord.superopt.eta", "0.1");
		eta = Double.parseDouble(etaVal);
		timeT = Long.getLong("chord.superopt.tgttime", 0);
		timeT = timeT * 1000000;
		timeR = Long.getLong("chord.superopt.rwttime", 0);
		timeR = timeR * 1000000;
		delRatio = Integer.getInteger("chord.superopt.delratio", 2);
		numWorkers = Integer.getInteger("chord.numworkers", 1);
		deletionStrategy = System.getProperty("chord.superopt.deletion", "aggressive");
		System.out.println("Deletion Strategy: " + deletionStrategy);
		
		if (!useFilt) {
			analysis = ClassicProject.g().getTask("fil-cipa-0cfa-dlog");
			analysisCopyInp = ClassicProject.g().getTask("0cfa-copy-inp-dlog");	
		} else {
			analysis = ClassicProject.g().getTask("fil-cipa-0cfa-filt-dlog");
			analysisCopyInp = ClassicProject.g().getTask("0cfa-copy-inp-filt-dlog");
			filtPrefix = "F_";
			FunctionOpt.setFiltPrefix(filtPrefix);
		}
		analysisCopyOp = ClassicProject.g().getTask("0cfa-copy-op-dlog");	
		analysisCheck = ClassicProject.g().getTask("0cfa-check-out-dlog");
		analysisFilt = ClassicProject.g().getTask("0cfa-filter-dlog");
		
		// run baseline
		if (!Config.reuseRels) {
			if (useFilt) {
				ITask cipa = ClassicProject.g().getTask("cipa-0cfa-dlog");
				ClassicProject.g().runTask(cipa);
			}
			long startTime = System.nanoTime();
			ClassicProject.g().runTask(analysis);
			long endTime = System.nanoTime();
			timeT = endTime - startTime;
			System.out.println("BASELINE (TARGET) TIME: " + timeT);
		}
		ClassicProject.g().runTask(analysisCopyInp);
		ClassicProject.g().runTask(analysisCopyOp);
		
		String inSpecFile = System.getenv("CHORD_MAIN") + File.separator + superoptPath + File.separator + "0cfa_in.txt";
		inRelNames = Util.getRelNames(inSpecFile);
		String outSpecFile = System.getenv("CHORD_MAIN") + File.separator + superoptPath + File.separator + "0cfa_out.txt";
		outRelNames = Util.getRelNames(outSpecFile);
		return;
	}
	
	
	@Override
	public void init () {
		masterFailureLimit = 2000;
		Mode md = getMode();
		
		String oper = System.getProperty("chord.superopt.op", "mcmcf");
		if (oper.equals("sc")) 
			doSC = true;
		else if (oper.equals("ac"))
			doAC = true;
		else if (oper.equals("mcmcf"))
			doMCMCF = true;
		else if (oper.equals("baseline"))
			doBaseline = true;
		else if (oper.equals("prep"))
			doPrep = true;
		else if (oper.equals("test"))
			doTest = true;
		else if (oper.equals("equiv"))
			doEquiv = true;
		
		if (md == Mode.MASTER) {
			commonInit();
			if (doBaseline) {
				ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
				rel.load();
				for (Object obj : rel.getAry1ValTuples()) {
					jq_Method m = (jq_Method)obj;
					ControlFlowGraph cfg = m.getCFG();
					System.out.println("*****************************************************");
					System.out.println("METHODCFG: " + m.getName());
					System.out.println("-----------------------------------------");
					System.out.println(cfg.fullDump());
				}
				FunctionOpt.inititalizeForFuncOpt(inRelNames);
				FunctionOpt.dumpLibMethods();
			}  else if (doTest) {
				runTest();
			} else if (doEquiv) {
				checkEquivalence();
			}
			Util.closeLogs();
		} else if (md == Mode.WORKER) {
			if (doSC || doAC || doMCMCF || doPrep) {
				commonInit();
			}
		}
		return;
	}

	
	public String apply (String line) {
		Mode mode = getMode();
		if (mode == Mode.WORKER) {
			if (doSC) {
				scanCoarsen();
			} else if (doAC) {
				activeCoarsen();
			} else if (doMCMCF) {
				doMCMCF(line);
			} else if (doPrep) {
				if (!Config.reuseRels) {
					System.out.println("Cannot use prep option without reuse rels turned on.");
					return "METH DUMMY DUMMY";
				}
				prepareForReuse();
			}
			Util.closeLogs();
		}
		return "METH DUMMY DUMMY";
	}
	
	public JobDispatcher getJobDispatcher() {
		FunctionOpt.inititalizeForFuncOpt(inRelNames);
		Set<jq_Method> s = FunctionOpt.getMethods(methods);
		int totalMeths = s.size();
		System.out.println("=============================================================================");
		System.out.println("getJobDispatcher: TotalMethods: " + totalMeths + "  numWorkers: " + numWorkers);
		int jobSz = totalMeths / numWorkers;
		int rem = totalMeths % numWorkers;
		ArrayList<ArrayList<String>> jobs = new ArrayList<ArrayList<String>>();
		int workerCnt = 0;
		int methCnt = 0;
		int jobLim = jobSz;
		ArrayList<String> job = new ArrayList<String>();
		for (jq_Method m : s) {
			if (methCnt < jobLim) {
				job.add(m.toString());
				methCnt++;
			} else {
				workerCnt++;
				jobs.add(job);
				job = new ArrayList<String>();
				job.add(m.toString());
				methCnt = 1;
				if (workerCnt + rem >= numWorkers) jobLim = jobSz + 1;
			}
		}
		jobs.add(job);
		return new SuperOptDispatcher(jobs);
	}
	
	
	public void scanCoarsen() {
		boolean success = true;
		boolean stopAtSuccess = false;
		ArrayList<String> delRelNames = Util.getDelRels(inRelNames);
		if (doSC) Util.loadPristineRelsToLists(delRelNames, dumpRels, true);
		int instToDelete = 0;
		ArrayList<Integer> delRelAccSz = Util.adjustStartNdxSC();
		int totalRelLen = Util.setMaxLibRelSzSC();
		System.out.println("SC start: totalRelLen: " + totalRelLen);
		ArrayList<ProgramRel> delRels = new ArrayList<ProgramRel>();
		for (String relName : delRelNames) {
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(filtPrefix + relName);
			delRels.add(rel);
		}
		while (Util.minSmallerThanMax() && instToDelete < totalRelLen) {
			scTrial++;	
			System.out.println("SC TRIAL NUM: " + scTrial);
			Util.deleteRelElem (delRels, delRelAccSz, instToDelete);
			ClassicProject.g().resetTaskDone(analysis);
			ClassicProject.g().setTaskDone(analysisFilt);
			ClassicProject.g().runTask(analysis);
			ClassicProject.g().setTaskDone(analysisCopyOp);
			ClassicProject.g().runTask(analysisCheck);
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("unequal_any");
			rel.load();
			if (rel.size() == 0) {
				delRelAccSz = Util.removeFromMax(delRelNames, delRelAccSz, instToDelete);
				totalRelLen--;
				success = true;
			} else {
				Util.addToMin(delRelNames, delRelAccSz, instToDelete);
				instToDelete++;
				success = false;
			}
			if (scTrial % 50 == 0) Util.flushLogs();
			File stopFile;
	    	stopFile = new File(Config.workDirName + File.separator + "stopSC");
	    	if (stopFile.exists()) {
	    		System.out.println("SC: STOPPING GRACEFULLY");
	    		stopFile.delete();
	    		break;
	    	}
	    	File firstFile;
	    	firstFile = new File(Config.workDirName + File.separator + "firstSC");
	    	if (firstFile.exists()) {
	    		System.out.println("SC: STOPPING AT THE NEXT SUCCESSFUL DELETION");
	    		firstFile.delete();
	    		stopAtSuccess = true;
	    	}
	    	if (success && stopAtSuccess) {
	    		Util.flushLogs();
	    		break;
	    	}
		}
		Util.saveFinalRel(delRels);
		ClassicProject.g().resetTaskDone(analysis);
		ClassicProject.g().setTaskDone(analysisFilt);
		long startTime = System.nanoTime();
		ClassicProject.g().runTask(analysis);
		long endTime = System.nanoTime();
		timeR = endTime - startTime;
		System.out.println("STARTING REWRITE TIME: " + timeR);
		ClassicProject.g().setTaskDone(analysisCopyOp);
		ClassicProject.g().runTask(analysisCheck);
	}
	
	public void activeCoarsen() {
		ArrayList<String> delRelNames = Util.getDelRels(inRelNames);
		if (doAC) Util.loadPristineRelsToLists(delRelNames, dumpRels, false);
		boolean success = true;
		boolean progress;
		boolean stopAtSuccess = false;
		int totalRelLen = Util.setMaxLibRelSz ();
		System.out.println("AC start: totalRelLen: " + totalRelLen);
		ArrayList<Integer> delRelAccSz = Util.adjustStartNdx();
		ArrayList<ProgramRel> delRels = new ArrayList<ProgramRel>();
		for (String relName : delRelNames) {
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(filtPrefix + relName);
			delRels.add(rel);
		}
		while (true) {
			acTrial++;	
			System.out.println("AC TRIAL NUM: " + acTrial);
			progress = Util.setCurrSample(eta, success);
			if (!progress) break;
			Util.deleteRelElemAC (delRels, delRelAccSz);
			ClassicProject.g().resetTaskDone(analysis);
			ClassicProject.g().setTaskDone(analysisFilt);
			ClassicProject.g().runTask(analysis);
			ClassicProject.g().setTaskDone(analysisCopyOp);
			ClassicProject.g().runTask(analysisCheck);
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("unequal_any");
			rel.load();
			if (rel.size() == 0) {
				Util.removeFromMaxAC(delRelNames, delRelAccSz);
				delRelAccSz = Util.adjustStartNdx();
				totalRelLen = Util.setMaxLibRelSz();
				System.out.println("AC TOTAL REL LEN: " + totalRelLen);
				Util.flushLogs();
				success = true;
			} else {
				success = false;
			}
			if (acTrial % 50 == 0) Util.flushLogs();
			
			File stopFile;
	    	stopFile = new File(Config.workDirName + File.separator + "stopAC");
	    	if (stopFile.exists()) {
	    		System.out.println("AC: STOPPING GRACEFULLY");
	    		stopFile.delete();
	    		break;
	    	}
	    	File firstFile;
	    	firstFile = new File(Config.workDirName + File.separator + "firstAC");
	    	if (firstFile.exists()) {
	    		System.out.println("AC: STOPPING AT THE NEXT SUCCESSFUL DELETION");
	    		firstFile.delete();
	    		stopAtSuccess = true;
	    	}
	    	if (success && stopAtSuccess) {
	    		Util.flushLogs();
	    		break;
	    	}
		}
		Util.initializeForSC();
		scanCoarsen();
	}
	
	public void doMCMCF(String line) {
		FunctionOpt.inititalizeForFuncOpt(inRelNames);
		FunctionOpt.loadVarTypeChk();
		{
			double perf = Util.calcPerf(timeT, timeR);
			double cost = perf; // eq = 0 
			Util.storeAcceptedRewriteCost(cost);
		}
		ArrayList<String> inDelRelNames = FunctionOpt.getDelRels(inRelNames, deletionStrategy);
		System.out.println("RELS SLATED FOR DELETION: " + inDelRelNames);
		boolean breakOut = false;
		boolean toDelete = false;
		boolean stopAtSuccess = false;
		String[] parts = line.split(" ");
		System.out.println("MCMCF INPUT: " + line);
		assert (parts.length == 3);
		for (jq_Method m : FunctionOpt.getMethods(parts[1])) {
			int rejectCnt = 0;
			int methDelRatio = delRatio;
			for (int trial = 0; trial < 1000; trial++) {
				System.out.println("MCMCF TRIAL NUM: " + trial + "  METHOD: " + m.toString());
				if (trial % methDelRatio != 0) toDelete = true;
				boolean modified;
				if (toDelete)
					modified = FunctionOpt.fdel(m, inDelRelNames);
				else
					modified = FunctionOpt.mcmcf(m, inRelNames);
				if (!modified) methDelRatio = 1100;
				if (rejectCnt >= 100) break;
				ClassicProject.g().resetTaskDone(analysis);
				ClassicProject.g().setTaskDone(analysisFilt);
				long startTime = System.nanoTime(); 
				ClassicProject.g().runTask(analysis);
				long endTime = System.nanoTime();
				long timeR = endTime - startTime;
				ClassicProject.g().setTaskDone(analysisCopyOp);
				ClassicProject.g().runTask(analysisCheck);
				boolean accept;
				double eq = Util.calcCorrectness(outRelNames);
				double perf = Util.calcPerf(timeT, timeR);
				double cost = eq + perf;
				if (toDelete) {
					if (eq == 0.0)
						accept = true;
					else
						accept = false;
					System.out.println("DELETE: eq: " + eq + "  perf: " + perf + "   accept: " + accept);
				} else {
					double prob = Util.probRewriteAccept(cost);
					//accept = Util.getBoolWithProb(prob);
					if (eq == 0.0)
						accept = true;
					else
						accept = false;
					System.out.println("MUTATE: eq: " + eq + "  perf: " + perf + "  prob: " + prob + "   accept: " + accept);
				}
				if (accept) {
					FunctionOpt.dumpOut(m, trial, true);
					FunctionOpt.flushLogs();
					Util.storeAcceptedRewriteCost(cost);
					if (toDelete) rejectCnt = 0;
				} else {
					FunctionOpt.rejectChanges(m);
					FunctionOpt.dumpOut(m, trial, false);
					if (toDelete) rejectCnt++;
				}
				File stopFile;
		    	stopFile = new File(Config.workDirName + File.separator + "stop");
		    	if (stopFile.exists()) {
		    		System.out.println("STOPPING GRACEFULLY");
		    		stopFile.delete();
		    		breakOut = true;
		    		break;
		    	}
		    	File firstFile;
		    	firstFile = new File(Config.workDirName + File.separator + "first");
		    	if (firstFile.exists()) {
		    		System.out.println("STOPPING AT THE NEXT SUCCESSFUL MUTATION/DELETION");
		    		firstFile.delete();
		    		stopAtSuccess = true;
		    	}
		    	toDelete = false;
		    	if (stopAtSuccess && eq == 0.0 && accept) {
					breakOut = true;
		    		break;
				}
			}
			if (breakOut) break;
		}
		FunctionOpt.makeFinalState();
		FunctionOpt.dumpLibMethods();
		FunctionOpt.closeLogs();
	}
	
	public void prepareForReuse () {
		FunctionOpt.prepForReuse(inRelNames);
	}
	
	public void runTest () {
		FunctionOpt.initializeForTest(inRelNames); 
		FunctionOpt.loadForTest(inRelNames, null, sound);
		ClassicProject.g().resetTaskDone(analysis);
		ClassicProject.g().setTaskDone(analysisFilt);
		ClassicProject.g().runTask(analysis);
		ClassicProject.g().setTaskDone(analysisCopyOp);
		ClassicProject.g().runTask(analysisCheck);
		
		System.out.println("NO. OF OPT METHODS TO PROCESS: " + FunctionOpt.optimizedFuncRels.keySet().size());
		System.out.println("Soundness check: " + sound);
		for (jq_Method m : FunctionOpt.optimizedFuncRels.keySet()) {
			System.out.println ("PROCESSING OPTIMIZED METHOD: " + m.toString());
			FunctionOpt.loadForTest(inRelNames, m, sound);
			if (sound) {
				ClassicProject.g().resetTaskDone(analysis);
				ClassicProject.g().setTaskDone(analysisFilt);
				long startTime = System.nanoTime(); 
				ClassicProject.g().runTask(analysis);
				long endTime = System.nanoTime();
				long timeR = endTime - startTime;
				ClassicProject.g().setTaskDone(analysisCopyOp);
				ClassicProject.g().runTask(analysisCheck);
				ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("unequal_any");
				rel.load();
				if (rel.size() != 0)
					FunctionOpt.undoMethodInclusion(m);
				System.out.println("OPTIMIZED TIME: " + timeR);
			}
		}
		FunctionOpt.loadForTest(inRelNames, null, sound);
		ClassicProject.g().resetTaskDone(analysis);
		ClassicProject.g().setTaskDone(analysisFilt);
		long startTime = System.nanoTime(); 
		ClassicProject.g().runTask(analysis);
		long endTime = System.nanoTime();
		long timeR = endTime - startTime;
		ClassicProject.g().setTaskDone(analysisCopyOp);
		ClassicProject.g().runTask(analysisCheck);
		System.out.println("OPTIMIZED TIME: " + timeR);
	}
	
	
	public void checkEquivalence() {
		FunctionOpt.initializeForTest(inRelNames); 
		int ctr = 0;
		for (jq_Method m : FunctionOpt.optimizedFuncRels.keySet()) {
			System.out.println ("EQUIV CHECK FOR OPTIMIZED METHOD: " + m.toString());
			HashMap<String, ArrayList<Object[]>> unoptMethBody = FunctionOpt.getUnoptMethodBody(m);
			HashMap<String, ArrayList<Object[]>> optMethBody = FunctionOpt.getOptMethodBody(m);
			boolean isEquiv = EquivCheck.chkEquiv(unoptMethBody, optMethBody, "m" + ctr);
			ctr++;
			System.out.println("METHOD: " + m + " " + (isEquiv? "is equivalent.": "is not equivalent."));
		}
	}
}
