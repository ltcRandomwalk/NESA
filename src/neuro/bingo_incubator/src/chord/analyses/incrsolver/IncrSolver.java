package chord.analyses.incrsolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.project.analyses.provenance.Tuple;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.analyses.argret.DomZ;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.Timer;

/*
 *
 * A solver which applies training data (summaries) compositionally. 
 * @author Sulekha
 *
 * -Dchord.incrsolver.summaryDir           - [""] The directory from which conditional summaries should be loaded.
 * -Dchord.incrsolver.baseline             - [false] Run baseline (just the sweeping pass)
 * -Dchord.incrsolver.printRels            - [false] If true, print all the tuples of all relations in a fixed dir (one file per rel)
 * -Dchord.incrsolver.printRelCount        - [false] Print per-relation total tuple count and lib tuple count
 * -Dchord.incrsolver.appName              - [""] name of the app on which this analysis is acting upon.
 * -Dchord.incrsolver.sliceGraph           - [false] If true, the provided summaries are for a sliced derivation graph. And, therefore, the 
 *                                                   final saturation (sweeping pass) should happen only for the sliced derivation graph.
 * -Dchord.incrsolver.iterative            - [false] If true, the summary matching and will loading happen iteratively
 *                                                   else,  through the dlog file.                                       
 *
 */
@Chord(name = "incrsolver")
public class IncrSolver extends JavaAnalysis {
	List<ITask> tasks;
	List<ITask> repeatTask;
	List<ITask> finalTask;
	String summaryDir;
	String repeatTaskName = "cspa-kobj-frnt-dlog";
	String repeatTaskWithSummLoad = "cspa-kobj-frnt-summ-load-dlog";
	String finalTaskName = "cspa-kobj-sweep-dlog";
	String preSweepTaskName = "cspa-kobj-pre-sweep-dlog";
	HashMap<String, HashMap<String, Integer>> stringToDomIdx;
	Timer timer1;
	Timer timer2;
	HashMap<String, int[]> mark = new HashMap<String, int[]>();
	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	private boolean printOutputRels;
	private boolean printRelCnt;
	private String selfAppName;
	private String appUnderTest;
	private boolean sliceGraph;
	private boolean doBaseline;
	private boolean iterative;

	public void run() {
		// Perform initial tasks
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("incr-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("VCfilter-dlog"));
		tasks.add(ClassicProject.g().getTask("retCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("preload-java"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-incrinit-dlog"));
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		
		summaryDir = System.getProperty("chord.incrsolver.summaryDir", "");
		doBaseline = Boolean.getBoolean("chord.incrsolver.baseline");
		printOutputRels = Boolean.getBoolean("chord.incrsolver.printRels");
		printRelCnt = Boolean.getBoolean("chord.incrsolver.printRelCount");
		selfAppName = System.getProperty("chord.incrsolver.appName");
		sliceGraph = Boolean.getBoolean("chord.incrsolver.sliceGraph");
		iterative = Boolean.getBoolean("chord.incrsolver.iterative");
		appUnderTest = System.getenv("INCRSOLVER_TEST_APP");
		
		repeatTask = new ArrayList<ITask>();
		repeatTask.add(ClassicProject.g().getTask(repeatTaskName));
		finalTask = new ArrayList<ITask>();
		if (sliceGraph) {
			if (doBaseline) {
				System.out.println("Baseline computation for a sliced derivation graphs is not supported");
				System.exit(1);
			}
			if (!iterative) {
				finalTask.add(ClassicProject.g().getTask(repeatTaskWithSummLoad));
				finalTask.add(ClassicProject.g().getTask(preSweepTaskName));
				finalTask.add(ClassicProject.g().getTask(finalTaskName));
			} else
				finalTask.add(ClassicProject.g().getTask(repeatTaskName));
		} else {
			if (!iterative && !doBaseline)
				finalTask.add(ClassicProject.g().getTask(repeatTaskWithSummLoad));
			finalTask.add(ClassicProject.g().getTask(finalTaskName));
		}
		
		if(selfAppName != null && appUnderTest != null && !appUnderTest.equals(selfAppName)) {
			System.out.println("Looks like INCRSOLVER_TEST_APP is set incorrectly - it is not " + selfAppName);
			System.exit(1);
		}
		
		// reachableT is a derived relation for cipa-0cfa and for cspa-kobj. Since we are doing a preload here, derived rels need to be cleared.
		clearDerivedRelations();
		
		if (printOutputRels || printRelCnt)
			markDoms();
		
		// For incremental analysis, generate frontier, evaluate condition and load summaries, in a loop
		if (!doBaseline && iterative) {
			System.out.println("IS: ENTERING INCR PHASE");
			createDomainMap();
			int times = Integer.getInteger("chord.incrsolver.times");
			int count = 0;
			timer1 = new Timer("Condition tree creation");
			timer1.init();
			SummaryHandler sumHandler = new SummaryHandler(summaryDir, stringToDomIdx);
			timer1.done();
			long time1 = timer1.getInclusiveTime();
			System.out.println("IS: EXECTIME: Condition tree creation: " + Timer.getTimeStr(time1));
			HashMap<String, ProgramRel> relNameToPgmRel1 = new HashMap<String, ProgramRel>();
			loadFrontier(relNameToPgmRel1);
			sumHandler.loadUnconditionalSummary(relNameToPgmRel1);
			boolean summariesAvailable = sumHandler.areSummariesAvailable();
			while (summariesAvailable) {
				System.out.println("IS: LOAD SUMMARIES: Iteration no.: " + count);
				timer2 = new Timer("Time for one iteration");
				timer2.init();
				for (ITask t : repeatTask) {
					ClassicProject.g().resetTaskDone(t);
					ClassicProject.g().runTask(t);
				}
				if (printOutputRels || printRelCnt)
					printRelations("IDBRelsFrnt");
				HashMap<String, ProgramRel> relNameToPgmRel = new HashMap<String, ProgramRel>();
				loadFrontier(relNameToPgmRel);
				sumHandler.loadConditionalSummaries(relNameToPgmRel);
				summariesAvailable = sumHandler.areSummariesAvailable();
				count++;
				timer2.done();
				long time2 = timer2.getInclusiveTime();
				System.out.println("IS: EXECTIME: Time for one iteration:" + Timer.getTimeStr(time2));
				if (times > 0 && count >= times)
					break;
			}
		}
		
		// Do the final fixed point sweeping pass computation
		// Note: println below is used by automation script.
		System.out.println("SWEEPING PASS");
		for (ITask t : finalTask) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
			if (printOutputRels || printRelCnt)
				printRelations("IDBRelsAll");
		}
	}
	
	private void clearDerivedRelations() {
		List<ProgramRel> producedTrgts = getProducedTrgts();
		System.out.println("IS: In ClearDerivedRelations");
		for (ProgramRel currTgt: producedTrgts) {
			currTgt.zero();
			currTgt.save();
		}
	}
	

	private void printRelations (String prDir) {
		List<ProgramRel> producedTrgts = getProducedTrgts();
		String appDirName = "";
		if (printOutputRels) {
			String benchDir = System.getenv("PJBENCH");
			String dirName = benchDir + "/" + prDir;
			File relDir = new File(dirName);
			if (!relDir.exists()) {
				try{
			        relDir.mkdir();
			     } catch(SecurityException se){
			        System.out.println("incrsolver: printRelations: " + dirName + " does not exist - unable to create it.");
			     }        
			}
			appDirName = dirName + "/" + selfAppName;
			File appDir = new File(appDirName);
			if (!appDir.exists()) {
				try{
			        appDir.mkdir();
			     } catch(SecurityException se){
			        System.out.println("IS: App dir " + appDirName + " does not exist - unable to create it.");
			        return;
			     }        
			}
		}
		int total = 0;
		int inLib = 0;
		int i = 0;
		for (ProgramRel currTgt: producedTrgts) {
			int[] cnts = new int[producedTrgts.size()];
			currTgt.load();
			System.out.println("IS:RELATION TOTAL CNT: " + currTgt.getName() + "    " + currTgt.size());			
			cnts[i] = printRelTuples(currTgt, appDirName);
			System.out.println("IS:RELATION LIB CNT: " + currTgt.getName() + "    " + cnts[i]);
			total += currTgt.size();
			inLib += cnts[i];
			i++;
		}
		System.out.println("IS: TUPLE COUNT: total " + total + "   total lib " + inLib);
	}


	private int printRelTuples(ProgramRel r, String relDirName) {
		
		PrintWriter currPW = null;
		if (printOutputRels) {
			String fName = relDirName + "/" + r.getName() + ".txt";
			File relFile = new File(fName);
			try {
				relFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				currPW = new PrintWriter(relFile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		int cnt = 0;
		
		for (int[] args : r.getAryNIntTuples()) {
			Tuple t = new Tuple(r, args);
			if (belongsToLib(t)) {
				if (printOutputRels) 
					currPW.println(t.toString());
				cnt++;
			}
		}
		if (printOutputRels)
			currPW.close();
		return cnt;
	}


	private void createDomainMap() {
		stringToDomIdx = new HashMap<String, HashMap<String, Integer>>();
		
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		HashMap<String, Integer> nameMapI = new HashMap<String, Integer>();
		for(int i = 0; i < domI.size(); i++){
			nameMapI.put(domI.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("I", nameMapI);
		
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		HashMap<String, Integer> nameMapH = new HashMap<String, Integer>();
		for(int i = 0; i < domH.size(); i++){
			nameMapH.put(domH.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("H", nameMapH);
		
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		HashMap<String, Integer> nameMapM = new HashMap<String, Integer>();
		for(int i = 0; i < domM.size(); i++){
			nameMapM.put(domM.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("M", nameMapM);
		
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		HashMap<String, Integer> nameMapC = new HashMap<String, Integer>();
		for(int i = 0; i < domC.size(); i++){
			nameMapC.put(domC.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("C", nameMapC);
		
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		HashMap<String, Integer> nameMapV = new HashMap<String, Integer>();
		for(int i = 0; i < domV.size(); i++){
			nameMapV.put(domV.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("V", nameMapV);
		
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		HashMap<String, Integer> nameMapF = new HashMap<String, Integer>();
		for(int i = 0; i < domF.size(); i++){
			nameMapF.put(domF.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("F", nameMapF);
		
		DomT domT = (DomT) ClassicProject.g().getTrgt("T");
		HashMap<String, Integer> nameMapT = new HashMap<String, Integer>();
		for(int i = 0; i < domT.size(); i++){
			nameMapT.put(domT.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("T", nameMapT);
		
		DomZ domZ = (DomZ) ClassicProject.g().getTrgt("Z");
		HashMap<String, Integer> nameMapZ = new HashMap<String, Integer>();
		for(int i = 0; i < domZ.size(); i++){
			nameMapZ.put(domZ.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("Z", nameMapZ);
	}
	
	private void loadFrontier(HashMap<String, ProgramRel> relNameToPgmRel) {
		List<ProgramRel> producedTrgts = getProducedTrgts();
		for (ProgramRel currTgt: producedTrgts) {
			currTgt.load();
			relNameToPgmRel.put(currTgt.getName(), currTgt);
		}
	}
	
	private List<ProgramRel> getProducedTrgts() {
		List<ProgramRel> relList = new ArrayList<ProgramRel>();
		
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjValAsgnInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjVarAsgnInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetInstFldInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputInstFldInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetStatFldInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputStatFldInst"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableT"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIC"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIH"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DI"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVDV"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCI"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCM"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("rootCM"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CICM"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CMCM"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVC"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CVC"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("FC"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CFC"));

		return relList;		
	}
	
	
	private boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		for (int i = 0; i < dArr.length; i++) {
			if (mark.containsKey(dArr[i].getName()))
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
	
	
	private void markDoms()
	{
		@SuppressWarnings("rawtypes")
		List<Dom> domArray = new ArrayList<Dom>();
		domArray.add((DomI) ClassicProject.g().getTrgt("I"));
		domArray.add((DomH) ClassicProject.g().getTrgt("H"));
		domArray.add((DomM) ClassicProject.g().getTrgt("M"));
		domArray.add((DomC) ClassicProject.g().getTrgt("C"));
		domArray.add((DomV) ClassicProject.g().getTrgt("V"));
		domArray.add((DomF) ClassicProject.g().getTrgt("F"));
		domArray.add((DomT) ClassicProject.g().getTrgt("T"));
		
		for(Dom dom : domArray) {
			if (!mark.containsKey(dom.getName())) {
				int[] markVec = new int[dom.size()];
				markDomain(dom, markVec);
				mark.put(dom.getName(), markVec);
			}
		}	
	}
	
	private void markDomain(Dom dom, int[] vec) {
		
		if(dom.getName().equals("I")) {
			DomI domI = (DomI)dom;
			for (int i = 0; i < domI.size(); i++) {
				Quad q = (Quad) domI.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}	
		else if(dom.getName().equals("H")) {
			DomH domH = (DomH)dom;
			vec[0] = 2;  // Library
			for (int i = 1; i < domH.size(); i++) {
				Quad q = (Quad) domH.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("M")){
			DomM domM = (DomM)dom;
			vec[0] = 4;
			for (int i = 1; i < domM.size(); i++) { 
				jq_Class cl = domM.get(i).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("C")){
			DomC domC = (DomC)dom;
			vec[0] = 2; //Library
			for (int i = 1; i < domC.size(); i++) {
				Ctxt ctxt = (Ctxt) domC.get(i);
				int cnt = ctxt.length();
				Quad[] qArr;
				boolean valid;
				if (cnt > 0) {
					qArr = ctxt.getElems();
					valid = true;
					for (int j = 0; j < cnt; j++) {
						jq_Class cl = qArr[j].getMethod().getDeclaringClass();
						if (!cl.getName().matches(libraryPrefix)) {
							valid = false;
							break;
						}
					}
				} else {
					qArr = null;
					valid = true;
				}
				if (valid)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
				
			}
		}
		else if (dom.getName().equals("V")) {
			DomV domV = (DomV)dom;
			for (int i = 0; i < domV.size(); i++) { 
				Register v = domV.get(i);
				jq_Class cl = domV.getMethod(v).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("F")){
			DomF domF = (DomF)dom;
			vec[0] = 2;
			for (int i = 1; i < domF.size(); i++) {
				jq_Class cl = domF.get(i).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("T")){
			vec[0] = 2;
			DomT domT = (DomT)dom;
			for (int i = 1; i < domT.size(); i++) {
				if (domT.get(i).getName().matches(libraryPrefix))
					vec[i] = 2;   // Library
				else if (domT.get(i).getName().matches(fundamentalTypePrefix))
					vec[i] = 2;   // Library
				else
					vec[i] = 4;   // Application
			}
		}
	}
}
