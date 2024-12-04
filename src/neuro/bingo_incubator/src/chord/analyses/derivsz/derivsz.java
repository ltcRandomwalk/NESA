package chord.analyses.derivsz;

import chord.project.analyses.JavaAnalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import chord.bddbddb.Dom;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.var.DomV;
import chord.analyses.derivsz.TupleStats;
import chord.analyses.field.DomF;
import chord.analyses.type.DomT;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.LookUpRule;
import chord.project.analyses.provenance.Tuple;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

/*
 * -Dchord.incrsolver.summaryDir           - [null] location of summary dir
 * -Dchord.incrsolver.appName              - [null] the name of the app on which this analysis is running 
 * -Dchord.incrsolver.reorderDoms          - [false] dom entries to be created in lib-sorted order with lib entries preceding app entries
 * -Dchord.incrsolver.disjunctProbPercent  - [0] the probability (as percentage value) with which frontiers will be tracked for two disjuncts at every derivation step.
 * -Dchord.incrsolver.sliceGraph           - [false] slice the derivation graph to retain only outset derivations (transitively)
 * -Dchord.incrsolver.sliceComplement      - [false] retain the complement of the slice i.e. non-escaping tuples and 
 *                                                   generate provenance and summaries for them.
 * -Dchord.incrsolver.iterative            - [false] if true, summaries and frontiers are dumped as text files
 *                                                   if false, frontiers and summaries are dumped as Datalog relations/rules.
 * -Dchord.incrsolver.dlogWriteFile        - [""] The .dlog file with full path into which the auto-generated rules for loading summaries are
 *                                           to be written. (to be used when -Dchord.incrsolver.iterative is false) 
 *
 */



@Chord(name = "derivsz")
public class derivsz extends JavaAnalysis {
		
	String[] configFiles;
	HashMap<String, int[]> mark = new HashMap<String, int[]>();
	HashMap<String, HashMap<Integer, TupleStats>> derivedRelData = new HashMap<String, HashMap<Integer, TupleStats>>();
	HashMap<TIntHashSet, TIntHashSet> frontierMap = new HashMap<TIntHashSet, TIntHashSet>();

	List<ITask> tasks;

	ConstraintGenerator cg;
	List<Tuple> allOutsetTuples;
	List<Tuple> allEDBTuples;
	List<LookUpRule> rules;
	TupleIndex tupleIndex;
	HashMap<Integer, TupleStats> tupleToTupleStatsMap;
	HashMap<Integer, TupleStats> tupleToTupleStatsMapCompl;
	ArrayList<String> derivedRelList;
	

	private Set<String> derivedRs;
	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	private String sep = "####";
	private String summaryDir;
	private String selfAppName;
	private boolean reorder;
	private int disjunctProbPercent;
	private boolean sliceGraph;
	private boolean sliceComplement;
	private boolean iterative;
	private String dlogWriteFile;
	
	
	PrintWriter frntFileListPW;
	PrintWriter csvFileListPW;
	PrintWriter currPW;
	PrintWriter dlogWriteFilePW;
	
	@Override
	public void run() {
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("incr-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("VCfilter-dlog"));
		tasks.add(ClassicProject.g().getTask("retCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("preload-java"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-incrinit-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-summary-dlog_XZ90_"));
		System.setProperty("chord.ctxt.kind", "co");
		String chordMain = System.getenv("CHORD_MAIN");
		String kobjConfig = chordMain + File.separator + "../incubator/src/chord/analyses/incrsolver/kobj/cspa-kobj-summary-dlog_XZ90_.config";
		configFiles = new String[]{ kobjConfig };

		allOutsetTuples = new ArrayList<Tuple>();
		allEDBTuples = new ArrayList<Tuple>();
		rules = new ArrayList<LookUpRule>();
		
		summaryDir = System.getProperty("chord.incrsolver.summaryDir");
		selfAppName = System.getProperty("chord.incrsolver.appName");
		reorder = Boolean.getBoolean("chord.incrsolver.reorderDoms");
		sliceGraph = Boolean.getBoolean("chord.incrsolver.sliceGraph");
		sliceComplement = Boolean.getBoolean("chord.incrsolver.sliceComplement");
		disjunctProbPercent = Integer.getInteger("chord.incrsolver.disjunctProbPercent", 0);
		iterative = Boolean.getBoolean("chord.incrsolver.iterative");
		dlogWriteFile = System.getProperty("chord.incrsolver.dlogWriteFile");
		
		runAllTasks();
		markDoms();
		cg = new ConstraintGenerator(sliceGraph, mark, disjunctProbPercent, sliceComplement);
		tupleIndex = cg.getTupleIndex();
		initRules();
		populateRules();
		addAxiomsToEDB();
		cg.computeFrontier(allEDBTuples, allOutsetTuples);
		tupleToTupleStatsMap = cg.getTupleToTupleStatsMap();
		if (sliceComplement) tupleToTupleStatsMapCompl = cg.getTupleToTupleStatsMapCompl();
		dumpFrontierAndSummary();
	}
	
	private void addAxiomsToEDB() {
		ProgramRel reachableCM = (ProgramRel) ClassicProject.g().getTrgt("reachableCM");
		ProgramRel rootCM = (ProgramRel) ClassicProject.g().getTrgt("rootCM");
		int[] a1 = { 0, 0 };
		Tuple t1 = new Tuple(reachableCM, a1);
		int[] a2 = { 0, 0 };
		Tuple t2 = new Tuple(rootCM, a2);
		allEDBTuples.add(t1);
		allEDBTuples.add(t2);
		tupleIndex.getIndex(t1);
		tupleIndex.getIndex(t2);
	}
	
	
	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	private void initRules() {
		derivedRs = new HashSet<String>();
		for (String conFile : configFiles) {
			try {
				Scanner sc = new Scanner(new File(conFile));
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (!line.equals("")) {
						LookUpRule rule = new LookUpRule(line);
						rules.add(rule);
						derivedRs.add(rule.getHeadRelName());
					}
				}
				sc.close();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private void populateRules() {
		for (LookUpRule r : rules) {
			cg.update(r);
			collectEDBTuples(r);
		}
	}
	
	private void collectEDBTuples(LookUpRule r) {
		Iterator<ConstraintItem> iter = r.getAllConstrIterator();
		while (iter.hasNext()) {
			ConstraintItem it = iter.next();
			Tuple head = it.headTuple;
			boolean isApp = false;
			if (!belongsToLib(head)) isApp = true;
			for (Tuple st : it.subTuples) {
				if (st != null) {
					if (!derivedRs.contains(st.getRelName())) {
						allEDBTuples.add(st);
					} else {
						// this is a sub tuple belonging to a derived relation
						if (isApp)
							if (belongsToLib(st))
								allOutsetTuples.add(st);
					}
				}
			}
		}
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
	
	private void dumpFrontierAndSummary() {
		int count = 0;
		for (Map.Entry<Integer, TupleStats> ent : tupleToTupleStatsMap.entrySet()) {
			int t = ent.getKey();
			Tuple tpl = tupleIndex.getTuple(t);
			if (belongsToLib(tpl)) {
				TupleStats ts = ent.getValue();
				for (TIntHashSet fSet : ts.frntSetList) {
					TIntHashSet summary;
					if (frontierMap.containsKey(fSet)) {
						summary = (TIntHashSet) frontierMap.get(fSet);
					} else {
						summary = new TIntHashSet();
						frontierMap.put(fSet, summary);
					}
					summary.add(t);
				}
				count++;
				ts.frntSetList.clear();
				ts.currDisjunctFrntSetList.clear();
			} 
		}
		System.out.println("Num of tuples added as summary tuple for the slice:" + count);
		if (iterative) {
			dumpFrontierAndSummaryInTextFiles();
		} else {
			dumpFrontierAndSummaryInDatalog();
			if (sliceGraph) dumpUnusedAsLastSummary();
		}
		
		if (sliceComplement) {
			frontierMap.clear();
			count = 0;
			for (Map.Entry<Integer, TupleStats> ent : tupleToTupleStatsMapCompl.entrySet()) {
				int t = ent.getKey();
				Tuple tpl = tupleIndex.getTuple(t);
				if (belongsToLib(tpl)) {
					TupleStats ts = ent.getValue();
					for (TIntHashSet fSet : ts.frntSetList) {
						TIntHashSet summary;
						if (frontierMap.containsKey(fSet)) {
							summary = (TIntHashSet) frontierMap.get(fSet);
						} else {
							summary = new TIntHashSet();
							frontierMap.put(fSet, summary);
						}
						summary.add(t);
					}
					count++;
				}	
			}
			System.out.println("Num of tuples added as summary tuple for the slice complement:" + count);
			if (iterative) {
				summaryDir = summaryDir + "Compl";
				dumpFrontierAndSummaryInTextFiles();
			} else {
				// This feature (sliceComplement) is not currently expected to be used to generate datalog conditional loading rules.
				// It is currently being used only for study.
			}
		}
	}
	
	
	private void dumpUnusedAsLastSummary() {
		TIntHashSet unusedTuples = cg.unusedTuples;
		HashMap<String, ProgramRel> unusedRelNameToPgmRel = new HashMap<String, ProgramRel>();
		loadUnusedRelations(unusedRelNameToPgmRel);
		
		for (TIntIterator iter = unusedTuples.iterator(); iter.hasNext(); ) {
			int t = iter.next();
			Tuple tpl = tupleIndex.getTuple(t);
			String newRelName = tpl.getRelName() + "_l";
			if (unusedRelNameToPgmRel.containsKey(newRelName)) {
				ProgramRel pRel = (ProgramRel)unusedRelNameToPgmRel.get(newRelName);
				int[] indices = tpl.getIndices();
				switch (indices.length) {
	    		case 1:
	    			pRel.add(indices[0]);
	    			break;
	    		case 2:
	    			pRel.add(indices[0], indices[1]);
	    			break;
	    		case 3:
	    			pRel.add(indices[0], indices[1], indices[2]);
	    			break;
	    		case 4:
	    			pRel.add(indices[0], indices[1], indices[2], indices[3]);
	    			break;
	    		default:
	    			break;
	    		}
			}
    	}
		for (ProgramRel pRel : unusedRelNameToPgmRel.values()) {
			pRel.save();
		}
	}
	
	
	private void dumpFrontierAndSummaryInDatalog() {
		tasks.clear();
		tasks.add(ClassicProject.g().getTask("cspa-kobj-summary-rels-dlog"));
		runAllTasks();
		
		HashMap<String, ProgramRel> summRelNameToPgmRel = new HashMap<String, ProgramRel>();
		HashMap<String, ProgramRel> frntRelNameToPgmRel = new HashMap<String, ProgramRel>();
		HashMap<String, ProgramRel> genRelNameToPgmRel = new HashMap<String, ProgramRel>();
		loadSummaryRelations(summRelNameToPgmRel);
		loadFrontierRelations(frntRelNameToPgmRel);
		loadGeneratedRelations(genRelNameToPgmRel);
		dumpAsDatalogRulesAndRels(summRelNameToPgmRel, frntRelNameToPgmRel, genRelNameToPgmRel);
	}
	
	
	private void loadUnusedRelations(HashMap<String, ProgramRel> unusedRelNameToPgmRel) {
		List<ProgramRel> trgts = getUnusedTrgts();
		for (ProgramRel currTgt: trgts) {
			currTgt.load();
			currTgt.zero();
			unusedRelNameToPgmRel.put(currTgt.getName(), currTgt);
		}
	}
	
	
	private void loadSummaryRelations(HashMap<String, ProgramRel> summRelNameToPgmRel) {
		List<ProgramRel> trgts = getSummaryTrgts();
		for (ProgramRel currTgt: trgts) {
			currTgt.load();
			currTgt.zero();
			summRelNameToPgmRel.put(currTgt.getName(), currTgt);
		}
	}
	
	
	private void loadFrontierRelations(HashMap<String, ProgramRel> frntRelNameToPgmRel) {
		List<ProgramRel> trgts = getFrontierTrgts();
		for (ProgramRel currTgt: trgts) {
			currTgt.load();
			currTgt.zero();
			frntRelNameToPgmRel.put(currTgt.getName(), currTgt);
		}
	}
	
	
	private void loadGeneratedRelations(HashMap<String, ProgramRel> genRelNameToPgmRel) {
		List<ProgramRel> trgts = getGeneratedTrgts();
		for (ProgramRel currTgt: trgts) {
			currTgt.load();
			currTgt.zero();
			genRelNameToPgmRel.put(currTgt.getName(), currTgt);
		}
	}
	
	
	private List<ProgramRel> getUnusedTrgts() {
		List<ProgramRel> relList = new ArrayList<ProgramRel>();
		
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjValAsgnInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjVarAsgnInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetInstFldInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputInstFldInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetStatFldInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputStatFldInst_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableT_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIC_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIH_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DI_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVDV_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCI_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCM_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("rootCM_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CICM_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CMCM_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVC_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CVC_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("FC_l"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CFC_l"));
		return relList;		
	}
	
	
	private List<ProgramRel> getSummaryTrgts() {
		List<ProgramRel> relList = new ArrayList<ProgramRel>();
		
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjValAsgnInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjVarAsgnInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetInstFldInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputInstFldInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetStatFldInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("RputStatFldInst_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableT_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIC_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DIH_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DI_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVDV_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCI_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCM_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("rootCM_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CICM_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CMCM_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVC_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CVC_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("FC_s"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CFC_s"));
		return relList;		
	}
	
	
	private List<ProgramRel> getFrontierTrgts() {
		List<ProgramRel> relList = new ArrayList<ProgramRel>();
		
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableT_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DI_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCM_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CICM_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("DVC_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("FC_f"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("CFC_f"));
		return relList;		
	}
	
	
	private List<ProgramRel> getGeneratedTrgts() {
		List<ProgramRel> relList = new ArrayList<ProgramRel>();
		
		relList.add((ProgramRel) ClassicProject.g().getTrgt("matched"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("fpath"));
		relList.add((ProgramRel) ClassicProject.g().getTrgt("pred"));
		return relList;		
	}
	
	
	private void dumpAsDatalogRulesAndRels(HashMap<String, ProgramRel> summRelNameToPgmRel,
			                               HashMap<String, ProgramRel> frntRelNameToPgmRel,
			                               HashMap<String, ProgramRel> genRelNameToPgmRel) {
		int cnt = 0;
		for (TIntHashSet fset : frontierMap.keySet()){
			cnt++;
			TIntHashSet summary = (TIntHashSet)frontierMap.get(fset);
			addToSummaryRels(cnt, summary, summRelNameToPgmRel);
			addToFrontierRels(cnt, fset, frntRelNameToPgmRel);
			addToGeneratedRels(cnt, fset, genRelNameToPgmRel);
		}
		System.out.println("NUMBER OF CONDITIONAL SUMMARIES: " + cnt);
		for (ProgramRel pRel : summRelNameToPgmRel.values()) {
			pRel.save();
		}
		for (ProgramRel pRel : frntRelNameToPgmRel.values()) {
			pRel.save();
		}
		for (ProgramRel pRel : genRelNameToPgmRel.values()) {
			pRel.save();
		}
		try {
			dlogWriteFilePW = new PrintWriter(new File(dlogWriteFile));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		declareRelations(summRelNameToPgmRel);
		dlogWriteFilePW.println("");
		declareRelations(frntRelNameToPgmRel);
		dlogWriteFilePW.println("");
		declareRelations(genRelNameToPgmRel);
		dlogWriteFilePW.println("");
		dlogWriteFilePW.println("");
		createDatalogFrontierRules(frntRelNameToPgmRel);
		dlogWriteFilePW.println("");
		dlogWriteFilePW.println("");
		createDatalogSummaryRules(summRelNameToPgmRel);
		dlogWriteFilePW.close();
	}
	
	
	private void createDatalogSummaryRules(HashMap<String, ProgramRel> relNameToPgmRel){
		for (String relName : relNameToPgmRel.keySet()) {
			// code below assumes that if "_s" is removed from relName, we can get the original relation name
			String origRelName = relName.substring(0, relName.length() - 2);
			ProgramRel pRel = relNameToPgmRel.get(relName);
			int numVars = pRel.getDoms().length;
			StringBuilder origVarList = new StringBuilder();
			StringBuilder varList = new StringBuilder();
			String varName = "x";
			origVarList.append("(");
			varList.append("(n,");
			for (int i = 1; i < numVars; i++) {
				if (i > 1) {
					origVarList.append("," + varName + i);
					varList.append("," + varName + i);
				} else {
					origVarList.append(varName + i);
					varList.append(varName + i);
				}
			}
			varList.append(")");
			origVarList.append(")");
			dlogWriteFilePW.println(origRelName + origVarList.toString() + " :- pred(n), " + relName + varList.toString() + ".");
		}
	}

	
	private void declareRelations(HashMap<String, ProgramRel> relNameToPgmRel) {
		for (String relName : relNameToPgmRel.keySet()) {
			String relType = getRelType(relName);
			StringBuilder sb = new StringBuilder();
			
			ProgramRel pRel = relNameToPgmRel.get(relName);
			Dom[] domArr = pRel.getDoms();
			int varCnt = 0;
			String varPrefix = "x";
			HashMap<String, Integer> repeatNdx = new HashMap<String, Integer>();
			sb.append(relName);
			sb.append("(");
			for (int i = 0; i < domArr.length; i++) {
				if (i > 0) sb.append(",");
				sb.append(varPrefix + varCnt + ":");
				String domName = domArr[i].getName();
				int domNameCnt;
				if (repeatNdx.containsKey(domName))
					domNameCnt = repeatNdx.get(domName);
				else 
					domNameCnt = 0;
				repeatNdx.put(domName, domNameCnt+1);
				sb.append(domName + domNameCnt);
				varCnt++;
			}
			sb.append(")");
			dlogWriteFilePW.println(sb.toString() + " " + relType);
		}
	}
	
	
	private String getRelType(String relName) {
		if (relName.equals("pred") || relName.equals("fpath"))
			return ("output");
		else
			return ("preload input");
	}
	
	
	private void addToSummaryRels(int sumId, TIntHashSet summary, HashMap<String, ProgramRel> relNameToPgmRel) {
		for (TIntIterator iter = summary.iterator(); iter.hasNext(); ) {
			int t = iter.next();
			Tuple tpl = tupleIndex.getTuple(t);
			String newRelName = tpl.getRelName() + "_s";
			if (relNameToPgmRel.containsKey(newRelName)) {
				ProgramRel pRel = (ProgramRel)relNameToPgmRel.get(newRelName);
				int[] indices = tpl.getIndices();
				switch (indices.length) {
	    		case 1:
	    			pRel.add(sumId, indices[0]);
	    			break;
	    		case 2:
	    			pRel.add(sumId, indices[0], indices[1]);
	    			break;
	    		case 3:
	    			pRel.add(sumId, indices[0], indices[1], indices[2]);
	    			break;
	    		case 4:
	    			pRel.add(sumId, indices[0], indices[1], indices[2], indices[3]);
	    			break;
	    		default:
	    			break;
	    		}
			}
    	}
	}
	
	
	private void createDatalogFrontierRules(HashMap<String, ProgramRel> relNameToPgmRel) {
		for (String relName : relNameToPgmRel.keySet()) {
			// code below assumes that if "_f" is removed from relName, we can get the original relation name
			String origRelName = relName.substring(0, relName.length() - 2);
			ProgramRel pRel = relNameToPgmRel.get(relName);
			int numVars = pRel.getDoms().length;
			StringBuilder origVars = new StringBuilder();
			StringBuilder rhsVarsBase = new StringBuilder();
			StringBuilder lhsVarsBase = new StringBuilder();
			StringBuilder rhsVarsRec1 = new StringBuilder();
			StringBuilder rhsVarsRec2 = new StringBuilder();
			StringBuilder lhsVarsRec = new StringBuilder();
			String varName = "x";
			origVars.append("(");
			rhsVarsBase.append("(n,i,j,");
			lhsVarsBase.append("(n,i,j)");
			rhsVarsRec1.append("(n,i,k)");
			rhsVarsRec2.append("(n,k,j,");
			lhsVarsRec.append("(n,i,j)");
			for (int i = 3; i < numVars; i++) {
				if (i > 3) {
					origVars.append("," + varName + i);
					rhsVarsBase.append("," + varName + i);
					rhsVarsRec2.append("," + varName + i);
				} else {
					origVars.append(varName + i);
					rhsVarsBase.append(varName + i);
					rhsVarsRec2.append(varName + i);
				}
			}
			rhsVarsBase.append(")");
			rhsVarsRec2.append(")");
			origVars.append(")");
			dlogWriteFilePW.println("fpath" + lhsVarsBase.toString() + " :- " + relName + rhsVarsBase.toString() + ", " 
			                                + origRelName + origVars.toString() + ", i=j.");
			dlogWriteFilePW.println("fpath" + lhsVarsRec.toString() + " :- fpath" + rhsVarsRec1.toString() + ", "
			                                + relName + rhsVarsRec2.toString() + ", " + origRelName + origVars.toString() + ".");
		}		
		dlogWriteFilePW.println("pred(n) :- fpath(n,i,j), matched(n,i,j).");
	}
	
	
	private void addToFrontierRels(int sumId, TIntHashSet fset, HashMap<String, ProgramRel> relNameToPgmRel) {
		HashMap<String, HashSet<Tuple>> relToFrntTupleSet = new HashMap<String, HashSet<Tuple>>();
		
		for (TIntIterator iter = fset.iterator(); iter.hasNext(); ) {
			int t = iter.next();
			Tuple tpl = tupleIndex.getTuple(t);
			String relName = tpl.getRelName();
			HashSet<Tuple> tplSet;
			if (relToFrntTupleSet.containsKey(relName))
				tplSet = relToFrntTupleSet.get(relName);
			else {
				tplSet = new HashSet<Tuple>();
				relToFrntTupleSet.put(relName, tplSet);
			}
			tplSet.add(tpl);
		}
		int ndx = 1;
		boolean firstTuple = true;
		for (HashSet<Tuple> tplSet : relToFrntTupleSet.values()) {
			for (Tuple tpl : tplSet) {
				String newRelName = tpl.getRelName() + "_f";
				if (relNameToPgmRel.containsKey(newRelName)) {
					ProgramRel pRel = (ProgramRel)relNameToPgmRel.get(newRelName);
					int[] indices = tpl.getIndices();
					int val1;
					int val2;
					if (firstTuple) {
						val1 = 1;
						val2 = 1;
						firstTuple = false;
					} else {
						val1 = ndx;
						val2 = ndx + 1;
						ndx++;
					}
					switch (indices.length) {
		    		case 1:
		    			pRel.add(sumId, val1, val2, indices[0]);
		    			break;
		    		case 2:
		    			pRel.add(sumId, val1, val2, indices[0], indices[1]);
		    			break;
		    		case 3:
		    			pRel.add(sumId, val1, val2, indices[0], indices[1], indices[2]);
		    			break;
		    		case 4:
		    			pRel.add(sumId, val1, val2, indices[0], indices[1], indices[2], indices[3]);
		    			break;
		    		default:
		    			break;
		    		}
				}
			}
    	}
	}
	
	
	private void addToGeneratedRels(int sumId, TIntHashSet fset, HashMap<String, ProgramRel> relNameToPgmRel) {
		if (relNameToPgmRel.containsKey("matched")) {
			ProgramRel pRel = (ProgramRel)relNameToPgmRel.get("matched");
			pRel.add(sumId, 1, fset.size());
		}
	}
	
	
	private void dumpFrontierAndSummaryInTextFiles() {
		File sumDir = new File(summaryDir);
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("Summary dir " + summaryDir + " does not exist - unable to create it.");
		     }        
		}
		int cnt = 0;
		try {
			frntFileListPW = new PrintWriter(new File(summaryDir + File.separator + selfAppName + "_condition_files"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	    for (TIntHashSet fset : frontierMap.keySet()){
	    	cnt++;
	    	String frntFName = selfAppName + "_frontier_" + String.valueOf(cnt);
	    	String summaryFName = selfAppName + "_summary_" + String.valueOf(cnt);
	    	frntFileListPW.println(frntFName);
	   
	    	try {
				currPW = new PrintWriter(new File(summaryDir + File.separator + frntFName));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
	    	currPW.println(summaryFName);
	    	for (TIntIterator iter = fset.iterator(); iter.hasNext(); ) {
				int t = iter.next();
				Tuple tpl = tupleIndex.getTuple(t);
	    		if (reorder)
	    			currPW.println(tpl.toString());	
	    		else
	    			currPW.println(tpl.toSummaryString(sep));
	    	}
	    	currPW.flush();
	    	currPW.close();
	    	
	    	TIntHashSet summary = (TIntHashSet)frontierMap.get(fset);
	    	try {
				currPW = new PrintWriter(new File(summaryDir + File.separator + summaryFName));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
	    	for (TIntIterator iter = summary.iterator(); iter.hasNext(); ) {
				int t = iter.next();
				Tuple tpl = tupleIndex.getTuple(t);
	    		if (reorder)
		    		currPW.println(tpl.toString());
	    		else
		    		currPW.println(tpl.toSummaryString(sep));
	    	}
	    	currPW.flush();
	    	currPW.close();
	    	frontierMap.put(fset,  null);
	    }
	    frntFileListPW.flush();
	    frntFileListPW.close();
	}
}

