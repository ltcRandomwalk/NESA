package chord.analyses.mln.kobj;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Compiler.Quad.Quad;
import chord.analyses.alloc.DomH;
import chord.analyses.argret.DomK;
import chord.analyses.invk.DomI;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * This class should use mln inference engine to replace the Datalog+MAXSAT in PLDI'14. Currently it only generates the constraints for one single
 * query for the first iteration.
 * A general class to run experiments based on k-obj analysis.
 * -Dchord.mln.client=<polysite/downcast/datarace>: specify the client to use
 * -Dchord.mln.obj2=<true/false>: specify whether to run only with the queries solvable using 2-OBJ
 * -Dchord.mln.queryOption=<all/separate/single>: specify the way to solve queries
 * -Dchord.mln.heap=<true/false>: specify whether to turn on heap-cloning
 * -Dchord.mln.mono=<true/false>: specify whether to monotonically grow the k values
 * -Dchord.mln.queryWeight=<Integer>: specify the weight we want to use for queries; if -1, treat them as hard constraints.
 * -Dchord.mln.model=<default>: specify what model to use to bias the refinement. Default: default(no bias)
 * If 0, use the sum(input weight) + 1
 * -Dchord.mln.boolDomain=<true/false>: specify whether we want to use boolean domain based kcfa
 * -Dchord.mln.invkK=<2>: if we use boolean domain, what is the k value we want for invoke sites
 * -Dchord.mln.allocK=<2>: if we use boolean domain, what is the k value we want for alloc sites 
 * -Dchord.mln.numQueries: randomly track given number of queries, default(-1) to track all queries
 * @author xin
 * 
 */
@Chord(name = "kobj-refiner-mln")
public class KOBJRefiner extends JavaAnalysis {
	DomI domI;
	DomH domH;
	DomK domK;
	Map<Tuple, Map<Quad, Integer>> absMap;
	Set<Tuple> unresolvedQs = new HashSet<Tuple>();
	Set<Tuple> impossiQs = new HashSet<Tuple>();
	String[] configFiles;

	List<String> inputRelationNames = new ArrayList<String>();
	List<ProgramRel> inputRelations = new ArrayList<ProgramRel>();
	List<String> progDomNames = new ArrayList<String>();
	List<Dom> programDoms = new ArrayList<Dom>();
	
	ProgramRel IKRel;
	ProgramRel HKRel;
	ProgramRel OKRel;
	ProgramRel allowHRel;
	ProgramRel denyHRel;
	ProgramRel allowORel;
	ProgramRel denyORel;
	ProgramRel queryRel;
	List<ITask> tasks;
	List<ITask> recycleTasks;
	PrintWriter debugPW;
	PrintWriter statPW;

	int client; // 0 polysite, 1 downcast, 2 datarace

	String clientFile;
	String clientConfigPath;
	String queryRelName;
	String modelStr;
	
	boolean ifCfa2;
	boolean ifHeap;
	boolean ifMono;
	boolean ifBool;
	int invkK;
	int allocK;

	static int iterLimit = 100;//max number of refinement iterations allowed
	static int max = 20; //max number of k value for both OK and HK
	
	int queryWeight;
	int numQueries;

	@Override
	public void run() {
		try {
			debugPW = new PrintWriter(new File(Config.outDirName + File.separator + "debug.txt"));
			statPW = new PrintWriter(new File(Config.outDirName+File.separator+"stat.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		{
			//for kobj-bit-init
			inputRelationNames.add("truncCKC");
			inputRelationNames.add("IM");
			inputRelationNames.add("MI");
			inputRelationNames.add("VH");
			inputRelationNames.add("statM");
			inputRelationNames.add("MH");
			inputRelationNames.add("initCHC");
			inputRelationNames.add("DenyO");
			inputRelationNames.add("CL");
			inputRelationNames.add("DenyH");
			inputRelationNames.add("initCOC");
			inputRelationNames.add("roots");
			inputRelationNames.add("thisMV");
			inputRelationNames.add("AllowH");
			inputRelationNames.add("AllowO");
			
			// for pro-cspa-kobj
			
			inputRelationNames.add("HT");
			inputRelationNames.add("cha");
			inputRelationNames.add("sub");
			inputRelationNames.add("MI");
			inputRelationNames.add("statIM");
			inputRelationNames.add("specIM");
			inputRelationNames.add("virtIM");
			inputRelationNames.add("MobjValAsgnInst");
			inputRelationNames.add("MobjVarAsgnInst");
			inputRelationNames.add("MgetInstFldInst");
			inputRelationNames.add("MputInstFldInst");
			inputRelationNames.add("MgetStatFldInst");
			inputRelationNames.add("MputStatFldInst");
			inputRelationNames.add("clsForNameIT");
			inputRelationNames.add("objNewInstIH");
			inputRelationNames.add("objNewInstIM");
			inputRelationNames.add("conNewInstIH");
			inputRelationNames.add("conNewInstIM");
			inputRelationNames.add("aryNewInstIH");
			inputRelationNames.add("classT");
			inputRelationNames.add("staticTM");
			inputRelationNames.add("staticTF");
			inputRelationNames.add("clinitTM");
			inputRelationNames.add("MmethArg");
			inputRelationNames.add("MspcMethArg");
			inputRelationNames.add("IinvkArg");
			inputRelationNames.add("IinvkArg0");
			inputRelationNames.add("IinvkRet");
			inputRelationNames.add("argCopy");
			inputRelationNames.add("retCopy");
			inputRelationNames.add("VCfilter");
			inputRelationNames.add("CH");
			inputRelationNames.add("epsilonM");
			inputRelationNames.add("kobjSenM");
			inputRelationNames.add("ctxtCpyM");	
		}
		
		{
			progDomNames.add("I");
			progDomNames.add("H");
			progDomNames.add("M");		
			progDomNames.add("K");
			progDomNames.add("V");
			progDomNames.add("C");
			
			progDomNames.add("T");
			progDomNames.add("F");
			progDomNames.add("Z");
		}
		
		String client = System.getProperty("chord.mln.client");
		if (client.equals("polysite")) {
			inputRelationNames.add("checkExcludedI");
			inputRelationNames.add("virtIM");
			queryRelName = "polySite";
		} else if (client.equals("downcast")) {

		} else if (client.equals("datarace")){

		}else
			throw new RuntimeException("Unknown client: " + this.client);

			
		//The analyses we need to run
		tasks = new ArrayList<ITask>();
		recycleTasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("simple-pro-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("pro-argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("kobj-bit-init-dlog"));
		tasks.add(ClassicProject.g().getTask("pro-cspa-kobj-dlog"));
		tasks.add(ClassicProject.g().getTask("pro-cspa-kobj-precallgraph-dlog"));
		recycleTasks.add(ClassicProject.g().getTask("simple-pro-ctxts-java"));
		recycleTasks.add(ClassicProject.g().getTask("pro-argCopy-dlog"));
		if(client.equals("polysite"))
			tasks.add(ClassicProject.g().getTask("polysite-dlog"));
		else
			throw new RuntimeException("Unknown client "+client);

		System.setProperty("chord.ctxt.kind", "co");
		System.setProperty("chord.kobj.khighest", "" + max);
		System.setProperty("chord.kcfa.khighest", "" + max);

		IKRel = (ProgramRel) ClassicProject.g().getTrgt("IK");
	
		HKRel = (ProgramRel) ClassicProject.g().getTrgt("HK");
		OKRel = (ProgramRel) ClassicProject.g().getTrgt("OK");
		allowHRel = (ProgramRel) ClassicProject.g().getTrgt("AllowH");
		denyHRel = (ProgramRel) ClassicProject.g().getTrgt("DenyH");
		allowORel = (ProgramRel) ClassicProject.g().getTrgt("AllowO");
		denyORel = (ProgramRel) ClassicProject.g().getTrgt("DenyO");
		domI = (DomI) ClassicProject.g().getTrgt("I");
		domK = (DomK) ClassicProject.g().getTrgt("K");
		domH = (DomH) ClassicProject.g().getTrgt("H");
		queryRel = (ProgramRel) ClassicProject.g().getTrgt(queryRelName);
		ClassicProject.g().runTask(domI);
		ClassicProject.g().runTask(domK);
		ClassicProject.g().runTask(domH);
		absMap = new HashMap<Tuple, Map<Quad, Integer>>();

		IKRel.zero();
		for (int i = 0; i < domI.size(); i++) {
			Quad I = (Quad) domI.get(i);
			IKRel.add(I,0);
		}
		IKRel.save();	
		
		String opt = System.getProperty("chord.mln.queryOption", "separate");
		ifCfa2 = Boolean.getBoolean("chord.mln.obj2");
		ifHeap = Boolean.getBoolean("chord.mln.heap");
		ifMono = Boolean.getBoolean("chord.mln.mono");
		ifBool = Boolean.getBoolean("chord.mln.boolDomain");
		invkK = Integer.getInteger("chord.mln.invkK",2);
		allocK = Integer.getInteger("chord.mln.allocK",2);
		numQueries = Integer.getInteger("chord.mln.numQueries", -1);
		
		modelStr = System.getProperty("chord.mln.model", "default");
		
		System.out.println("chord.mln.queryOption = "+opt);
		System.out.println("chord.mln.obj2 = "+ifCfa2);
		System.out.println("chord.mln.mono = "+ifMono);
		System.out.println("chord.mln.queryWeight = "+queryWeight);
		System.out.println("chord.mln.boolDomain = "+ifBool);
		System.out.println("chord.mln.invkK = "+invkK);
		System.out.println("chord.mln.allocK = "+allocK);
		System.out.println("chord.mln.numQueries = "+numQueries);
		System.out.println("chord.mln.model = "+modelStr);
		
		//Initialize the queries
		unresolvedQs = this.runClientWithK(0);
		if(ifCfa2){//if we want to solve 2-CFA queries only
			Set<Tuple> cfa2qs = runClientWithK(2);
			unresolvedQs.removeAll(cfa2qs);
		}
		
		if(numQueries != -1){
			List<Tuple> allQs = new ArrayList<Tuple>(unresolvedQs);
			Collections.shuffle(allQs);
			Set<Tuple> ret = new HashSet<Tuple>();
	    	for(int i = 0; i < numQueries && i < allQs.size(); i++){
	    		Tuple chosenQuery = allQs.get(i);
	    		ret.add(chosenQuery);
	    	}
	    	unresolvedQs.retainAll(ret);
		}
		
		for (Tuple t : unresolvedQs) {// put empty abstraction map for each query
			absMap.put(t, new HashMap<Quad, Integer>());
		}
		if (opt.equals("all")) {
			runAll();
		}
		if (opt.equals("separate")) {
			runSeparate();
		}
		if (opt.equals("single")) {
			String queryString = System.getProperty("chord.mln.query");
			Tuple t = new Tuple(queryString);
			// DBG Tuple t = new Tuple("polySite(64)");
			runSingle(t);
		}
		if (opt.equals("group")) {
			runGroup();
		}
		debugPW.flush();
		debugPW.close();
		statPW.flush();
		statPW.close();
	}
	

	private void runAll() {

	}


	private void updateAbsMap(Set<Tuple> qts, Set<Tuple> tupleToEli) {
		for (Tuple t : qts) {
			Map<Quad, Integer> abs = absMap.get(t);
			if (abs == null) {
				abs = new HashMap<Quad, Integer>();
				absMap.put(t, abs);
			}
			if(!ifMono)
				abs.clear();
			for (Tuple t1 : tupleToEli) {
				if (t1.getRelName().equals("DenyO")) {
					Quad H = (Quad) t1.getValue(0);
					Integer K = (Integer) t1.getValue(1);
					if(ifBool)
						K = invkK;
					Integer ek = abs.get(H);
					if(ek == null)
						abs.put(H, K);
					else
						if(K > ek)
							abs.put(H, K);
				} else if (t1.getRelName().equals("DenyH")) {
					Quad H = (Quad) t1.getValue(0);
					Integer K = (Integer) t1.getValue(1);
					if(ifBool)
						K = allocK;
					Integer ek = abs.get(H);
					if(ek == null)
						abs.put(H, K);
					else
						if(K > ek)
							abs.put(H, K);
				} else
					if(!t1.getRelName().equals(this.queryRelName))
						throw new RuntimeException("Unexpected param tuple: " + t1);
			}
		}
	}


	private void setHK(Quad q, int k){
		HKRel.add(q,k);
		for(int i = 0; i <= k; i++){
			allowHRel.add(q,i);
		}
		for(int i = k+1; i <= max; i++){
			denyHRel.add(q,i);
		}
	}
	
	private void setOK(Quad q, int k){
		OKRel.add(q,k);
		for(int i = 0; i <= k; i++){
			allowORel.add(q,i);
		}
		for(int i = k+1; i <= max; i++){
			denyORel.add(q,i);
		}	
	}
	
	/**
	 * Run the analysis with the abstraction map specified in the parameter
	 * @param abs
	 */
	private void runPreAnalysis(Map<Quad, Integer> abs) {
		HKRel.zero();
		allowHRel.zero();
		denyHRel.zero();
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			Integer k = abs.get(H);
			if (k == null||k==0)
				k = 1;
			setHK(H,k);
		}
		HKRel.save();
		allowHRel.save();
		denyHRel.save();

		OKRel.zero();
		allowORel.zero();
		denyORel.zero();
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			Integer k = abs.get(H);
			if (k == null)
				k = 0;
			setOK(H,k);
		}
		OKRel.save();
		allowORel.save();
		denyORel.save();
		
		runPreTasks();
	}

	private void runSeparate() {
		for (Tuple q : unresolvedQs) {
			runSingle(q);
		}
	}


	private Set<Tuple> runClientWithK(int k) {
		int hk;
		if(k == 0)
			hk = 1;
		else
			hk = k;
		System.out.println("k: "+k);
		System.out.println("hk: "+hk);
		HKRel.zero();
		allowHRel.zero();
		denyHRel.zero();
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			setHK(H,hk);
		}
		HKRel.save();
		allowHRel.save();
		denyHRel.save();
		
		OKRel.zero();
		allowORel.zero();
		denyORel.zero();
		for(int i = 0; i < domH.size(); i++){
			Quad H = (Quad) domH.get(i);
			setOK(H,k);
		}
		OKRel.save();
		allowORel.save();
		denyORel.save();
	
		runAllTasks();
		return tuples(queryRel);
	}

	private void runSingle(Tuple q) {
		printlnInfo("Processing query: " + q);
		int numIter = 0;
		this.runAllTasks();
		try{
			while (true) {
				printlnInfo("===============================================");
				printlnInfo("===============================================");
				printlnInfo("Iteration: " + numIter);
				Map<Quad, Integer> abs = absMap.get(q);
				printlnInfo("Abstraction used: " + abs);
				PrintWriter edb = new PrintWriter(new File(Config.outDirName+File.separator+"polysite.db"));
				runPreAnalysis(abs);	
				//write domain predicates
				edb.println("// domains");
				for(String s : this.progDomNames){
					Dom d = (Dom)ClassicProject.g().getTrgt(s);
					for(int i = 0 ; i < d.size(); i++){
						edb.println("dom_"+d.getName()+"("+ i +")");
					}
				}
				
				edb.println();
				edb.println("// relations");
				for(String s : this.inputRelationNames){
					ProgramRel inRel = (ProgramRel) ClassicProject.g().getTrgt(s);
					inRel.load();
					for(int[] indices : inRel.getAryNIntTuples()){
						Tuple t = new Tuple(inRel,indices);
						int tw = this.getWeight(t);
						if(tw >= 0)
							edb.println(tw + " "+t);
						else
							edb.println(t);
					}
				}

				edb.println();
				edb.println("// queries");
				edb.println("!"+q);
				edb.close();
				edb.flush();
				numIter++;
				break;
			}
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	private static Set<Tuple> tuples(final ProgramRel r) {
		Set<Tuple> ts = new HashSet<Tuple>();
		for (int[] args : r.getAryNIntTuples())
			ts.add(new Tuple(r, args));
		return ts;
	}

	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		queryRel.load();
	}
	
	private final void runPreTasks() {
		for (ITask t : recycleTasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}

	private void runGroup() {

	}

	private void printlnInfo(String s) {
		System.out.println(s);
		debugPW.println(s);
	}

	private void printInfo(String s) {
		System.out.print(s);
		debugPW.print(s);
	}

	private int getWeight(String tuple){
		if(tuple.startsWith("Deny"))
			return 1;
		return -1;
	}
	
	private int getWeight(Tuple t){
		if(t.getRelName().startsWith("Deny"))
			return 1;
		return -1;
	}
}
