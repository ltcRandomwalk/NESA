package chord.analyses.bigcode.connection.TopDown;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap; 
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.bigcode.connection.DomPar;
import chord.analyses.bigcode.connection.DstNodeNullness;
import chord.analyses.bigcode.connection.EdgeNullness;
import chord.analyses.bigcode.connection.LocalVariable;
import chord.analyses.bigcode.connection.SrcNodeNullness;
import chord.analyses.bigcode.connection.StaticVariable;
import chord.analyses.bigcode.connection.UndirectedReachabilityNullnessVisitor;
import chord.analyses.bigcode.connection.Utils;
import chord.analyses.bigcode.connection.UtilsNullness;
import chord.analyses.bigcode.connection.Variable;
import chord.analyses.bigcode.connection.VariablesPartition;
import chord.analyses.bigcode.connection.VariablesPartitionNullness;
import chord.analyses.bigcode.connection.RHSAnalysis;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.PairIterable;
import chord.program.Loc;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.IEdge;
import chord.project.analyses.rhs.TimeoutException;
import chord.util.ArraySet;
import chord.util.IndexMap;
import chord.util.Timer;
import chord.util.tuple.object.Pair;
 
 
/**
 * Static undirected reachability analysis with must nullness analysis
 * 
 * 
 * @author Ghila Castelnuovo
 */
@Chord(name = "undirected-java-nosummaries-null", 
	   consumes = {"methodsStatics"})
public class UndirectedReachabilityAnalysisNoSummariesNullness extends
		RHSAnalysis<EdgeNullness, EdgeNullness>  {
	
	private HashMap<Quad, Set<VariablesPartition>> bottomUpResults = new HashMap<Quad, Set<VariablesPartition>>();	
	
	private Timer topDownTimer;
	private Set<Quad> localQueries =  new HashSet<Quad>();	
	private static DomM domM;
	private static DomI domI;
	private static DomV domV;
	private static DomF domF;
	private static DomPar domPar;
	private int varId[];
	private UndirectedReachabilityNullnessVisitor qv;
	private HashMap<jq_Method, Set<Integer>> methToVars = new HashMap<jq_Method, Set<Integer>>();
	private HashMap<jq_Method, Set<Integer>> methToStaticVars = new HashMap<jq_Method, Set<Integer>>();
	private Set<Integer> staticVars = new HashSet<Integer>();
	private HashMap<Quad, Set<VariablesPartitionNullness>> results =  new HashMap<Quad, Set<VariablesPartitionNullness>>();
	private UtilsNullness utils;
	private Set<Quad> chosenQuads =  new HashSet<Quad>();	
	static public VariablesPartitionNullness initPart =  null;
	protected jq_Method mainMethod;
	private int TDsize = 0;
	private int BUsize = 0;
	private static int onlyChosen = 0;
	private static int onlyNotChosen = 1;
	private static int all = 2;		
	
	@Override
	public void run() {
		
		Utils.exhaustiveGC();
		long initialMemory = Runtime.getRuntime().totalMemory() - 
						Runtime.getRuntime().freeMemory();
	
		System.out.println("initial memory is:"+initialMemory);
		
		topDownTimer = new Timer();
		topDownTimer.init();
		
			
		TObjectIntHashMap<jq_Method> methToNumVars = new TObjectIntHashMap<jq_Method>();
		TObjectIntHashMap<jq_Method> methToFstVar = new TObjectIntHashMap<jq_Method>();
		Program program = Program.g();
		mainMethod = program.getMainMethod();
		System.out.println("main is "+mainMethod);
		domI = (DomI) ClassicProject.g().getTrgt("I");
		ClassicProject.g().runTask(domI);
		domM = (DomM) ClassicProject.g().getTrgt("M");
		ClassicProject.g().runTask(domM);
		domV = (DomV) ClassicProject.g().getTrgt("V");
		ClassicProject.g().runTask(domV);
		domF = (DomF) ClassicProject.g().getTrgt("F");
		ClassicProject.g().runTask(domF);		
	
		domPar = (DomPar) ClassicProject.g().getTrgt("Par");
		
	
		/*****
		ProgramRel relMethodSummaries = (ProgramRel) ClassicProject.g().getTrgt("resultsStates");
		relMethodSummaries.load();

		PairIterable<Quad, VariablesPartition> tuples = relMethodSummaries.getAry2ValTuples();
		
		for (Pair<Quad, VariablesPartition> tuple : tuples) {
			Set<VariablesPartition> quadResults = bottomUpResults.get(tuple.val0);
			if (quadResults == null)
			{
				quadResults = new HashSet<VariablesPartition>();
				bottomUpResults.put(tuple.val0, quadResults);
			}
			quadResults.add(new VariablesPartition(tuple.val1));
		}
		*****/
	
		ProgramRel chosenQuadRel= (ProgramRel) ClassicProject.g().getTrgt("chosenQuads");
		chosenQuadRel.load();
		
		Iterable<Quad> quads = chosenQuadRel.getAry1ValTuples();
		
		for (Quad q: quads)
		{
			chosenQuads.add(q);
		}
		for (jq_Method method : domM)
		{
			methToVars.put(method, new HashSet<Integer>());
			methToStaticVars.put(method, new HashSet<Integer>());
		}
		
		ProgramRel relMethodsStatics= (ProgramRel) ClassicProject.g().getTrgt("methodsStatics");		
		relMethodsStatics.load();
		PairIterable<jq_Method, jq_Field> methodStaticsPairs = relMethodsStatics.getAry2ValTuples();
		Iterator<Pair<jq_Method, jq_Field>> itPairs = methodStaticsPairs.iterator();
		
		while(itPairs.hasNext())
		{
			Pair<jq_Method, jq_Field> pair = itPairs.next();
			Set<Integer> staticVarsLoc = methToStaticVars.get(pair.val0);
			staticVarsLoc.add(domF.indexOf(pair.val1));
		}
		Iterator<jq_Field> itF = domF.iterator();

		while (itF.hasNext()) {
			jq_Field f =  itF.next();
			if ((f != null) && (f.isStatic())) {
				staticVars.add(domF.indexOf(f));
			}
		}

		VariablesPartitionNullness.setVarDom(domV);
		VariablesPartitionNullness.setStaticFieldsDom(domF);
		
		int numV = domV.size();
		varId = new int[numV];
		for (int vIdx = 0; vIdx < numV;) {
			Register v = domV.get(vIdx);
			jq_Method m = domV.getMethod(v);
		
			int n = m.getLiveRefVars().size();
			methToNumVars.put(m, n);
			methToFstVar.put(m, vIdx);
			
			Set<Integer> vars = methToVars.get(m);
			if (vars == null) {
				vars = new HashSet<Integer>();
				methToVars.put(m, vars);
			}
			vars.add(vIdx);
			//allVars.add(vIdx);
			
			for (int i = 0; i < n; i++) {
				varId[vIdx + i] = i;
				//allVars.add(vIdx + i);
				vars.add(vIdx + i);
			}
			vIdx += n;
		}
		
		utils =  new UtilsNullness(domV,domF);
		utils.setMethodToStaticVars(methToStaticVars);
		qv = new UndirectedReachabilityNullnessVisitor(utils);
		initPart = VariablesPartitionNullness.createInit(null, methToStaticVars.get(mainMethod));		
		init();
		boolean timeout=false;
		System.out.println("starting the analysis");
		try {
			runPass();
		} catch (TimeoutException ex) {
			System.out.println("Timeout has occurred!");
			timeout = true;
		}
		
	//	printEdges(3);
	    topDownTimer.done();    
		PrintWriter statesSizeOut = null;
		try {
			statesSizeOut = new PrintWriter(new FileWriter(Config.outDirName +"//statesSizeNull.txt"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		statesSizeOut.println("The top down nullness analysis took "+topDownTimer.getInclusiveTimeStr());
		  
		Utils.exhaustiveGC();
		long allocatedMemory = Runtime.getRuntime().totalMemory() - 
				Runtime.getRuntime().freeMemory();
	    
		statesSizeOut.println("allocatedMemory after the top-down analysis is = "+ allocatedMemory);
		statesSizeOut.println("memory used by the top-down analysis is = "+ (allocatedMemory - initialMemory ));
		
		System.out.println("finished the analysis starting saving the adges");
		Calendar calendar = new GregorianCalendar();
		System.out.println("time is:"+ calendar.getTime().toString());
		int appStates = countStates(onlyChosen);
		if (!timeout)
		{
			int statesNumber = saveEdges(2);
			assert(appStates == statesNumber);			
		}
		
		statesSizeOut.println("number of states in chosen quads (in app code) computed by TopDown Nullness analysis is "+appStates);
		System.out.println("finished saving the edges");
		int totalStates = countStates(all);
		assert((appStates + countStates(onlyNotChosen)) == totalStates);
		statesSizeOut.println("total number of states computed by TopDown Nullness analysis is "+totalStates);
		statesSizeOut.close();
		System.out.println("time is:"+ calendar.getTime().toString());
		//compareResults(timeout);
	}
	

	@Override
	public EdgeNullness getInvkPathEdge(Quad q, EdgeNullness clrPE, jq_Method m, EdgeNullness tgtSE) 
	{
		//assert(tgtSE.dstNode.isRetn);
		
		VariablesPartitionNullness callerPart = new VariablesPartitionNullness(clrPE.dstNode.partition); 	
		VariablesPartitionNullness summaryPart = tgtSE.srcNode.partition;			
		VariablesPartitionNullness calleeInitPart = utils.getInitCalleePartitionNullness(callerPart, q, m);
		VariablesPartitionNullness callerTargetPart;
		
		if (calleeInitPart.equals(summaryPart))
		{			
			callerTargetPart = utils.getTargetCallerPartitionNullness(callerPart, tgtSE.dstNode.partition, tgtSE.dstNode.retIdxs, q, m);
			DstNodeNullness dstNode = new DstNodeNullness(callerTargetPart,false ,null); 
			return new EdgeNullness(clrPE.srcNode, dstNode);
		}	
//		else if (summaryPart.merge(calleeInitPart).equals(summaryPart))
//		{
//			System.out.println("summary contained  - giving a less precise result");
//			callerTargetPart = utils.getTargetCallerPartitionNullness(callerPart, tgtSE.dstNode.partition, tgtSE.dstNode.retIdxs, q, m);
//			DstNodeNullness dstNode = new DstNodeNullness(callerTargetPart,false ,null); 
//			return new Edge(clrPE.srcNode, dstNode);
//		}
		else
		{
			return null;
		}	
	}
	
	@Override
	public EdgeNullness getInitPathEdge(Quad q, jq_Method m2, EdgeNullness pe) 
	{	
		EdgeNullness pe2 = null;
		DstNodeNullness callerDstNode= pe.dstNode;

		VariablesPartitionNullness callerPartition = callerDstNode.partition;

		VariablesPartitionNullness calleePartition = utils.getInitCalleePartitionNullness(callerPartition, q, m2);
		
		SrcNodeNullness srcNode2 = new SrcNodeNullness(calleePartition);
		DstNodeNullness dstNode2 = new DstNodeNullness(calleePartition, false, null);
		
		pe2 = new EdgeNullness(srcNode2, dstNode2);
		return pe2;
	}

	
	protected EdgeNullness getRootPathEdge(jq_Method m) 
	{
		assert (m == mainMethod || m.getName().toString().equals("<clinit>"));
		SrcNodeNullness srcNode = new SrcNodeNullness(VariablesPartitionNullness.createInit(methToVars.get(m), methToStaticVars.get(m)));
		DstNodeNullness  dstNode = new DstNodeNullness(VariablesPartitionNullness.createInit(methToVars.get(m), methToStaticVars.get(m)), false , null);
		EdgeNullness pe = new EdgeNullness(srcNode, dstNode);
		return pe;
	}

	@Override
	public Set<Pair<Loc, EdgeNullness>> getInitPathEdges() {
		
		Set<jq_Method> roots = cicg.getRoots();
		ArraySet<Pair<Loc, EdgeNullness>> initPEs = new ArraySet<Pair<Loc, EdgeNullness>>(
				roots.size());
		 
		SrcNodeNullness srcNodeMain = new SrcNodeNullness(initPart);
		DstNodeNullness  dstNodeMain = new DstNodeNullness(initPart, false , null);
		EdgeNullness mainEdge = new EdgeNullness(srcNodeMain, dstNodeMain);
		BasicBlock bbMain = mainMethod.getCFG().entry();
		Loc locMain = new Loc(bbMain, -1);
		Pair<Loc, EdgeNullness> pairMain = new Pair<Loc, EdgeNullness>(locMain, mainEdge);
		initPEs.add(pairMain);
		
		for (jq_Method m : roots) {
			EdgeNullness pe;		
			if (m != mainMethod)
			{
				if (m.getName().toString().contains("<clinit>"))
				{
					pe = getRootPathEdge(m);
					BasicBlock bb = m.getCFG().entry();
					Loc loc = new Loc(bb, -1);
					Pair<Loc, EdgeNullness> pair = new Pair<Loc, EdgeNullness>(loc, pe);
					initPEs.add(pair);	
				}
				else
				{
					System.out.println(m);
				}
			}
		}
		return initPEs;
	}
	
	@Override
	public EdgeNullness propagateSEtoInit(jq_Method m, EdgeNullness seToAdd) {		
		initPart = utils.getTargetCallerPartitionNullness(initPart, seToAdd.dstNode.partition, null, null, m);	
		EdgeNullness initEdge = new EdgeNullness(new SrcNodeNullness(initPart),  new DstNodeNullness(initPart,false ,null));
		return initEdge;
	} 
	
	@Override
	public ICICG getCallGraph() {
		if (cicg == null) {
			CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g()
					.getTrgt("cicg-java");
			ClassicProject.g().runTask(cicgAnalysis);
			cicg = cicgAnalysis.getCallGraph();
		}
		return cicg;
	}

	@Override
	public EdgeNullness getMiscPathEdge(Quad q, EdgeNullness pe) 
	{
		DstNodeNullness dstNode = pe.dstNode;
		qv.iDstNode = dstNode;
		if (dstNode != null)
		{
			qv.oDstNode = new DstNodeNullness(dstNode);
		}
		else
		{
			qv.oDstNode = null;
		}
		q.accept(qv);
		DstNodeNullness dstNode2 = qv.oDstNode;
		EdgeNullness e = new EdgeNullness(pe.srcNode, dstNode2);
		return e;
	}


	@Override
	public EdgeNullness getSummaryEdge(jq_Method m, EdgeNullness pe) {
		EdgeNullness se;
		DstNodeNullness dstNode = pe.dstNode;
		se = new EdgeNullness(pe.srcNode, dstNode);
		return se;
	}
	

	@Override
	public EdgeNullness getPECopy(EdgeNullness pe) {
		return new EdgeNullness(pe);
	}

	@Override
	public EdgeNullness getSECopy(EdgeNullness se) {
		return new EdgeNullness(se);
	}
	
	/* private methods */

	
	private int saveEdges(int pass)
	{
		System.out.println("in save edges");
		PrintWriter resultsOut  = OutDirUtils.newPrintWriter("nosummariesNull.txt");
		PrintWriter functionsOut  = OutDirUtils.newPrintWriter("functionsTD.txt");	
		int totalNumber = 0;
		Set<jq_Method> reachableMethods = cicg.getNodes();	
	
		for (jq_Method m : reachableMethods) 
		{
			functionsOut.println(m);
	
			for (BasicBlock bb : m.getCFG().reversePostOrder()) 
			{
				if (!(bb.isEntry() || bb.isExit()))
				{
					for (int i=0; i<bb.size(); i++)
					{
						Quad q = bb.getQuad(i);
						if (!chosenQuads.contains(q)) continue;
						
						Set<EdgeNullness> peSet = pathEdges.get(q);
						if (peSet != null)
						{
							for (EdgeNullness e:peSet)
							{	
								VariablesPartitionNullness par = e.dstNode.partition;
								if (par != null)
								{
									Set<VariablesPartitionNullness> quadResults = results.get(q);
									if (quadResults == null)
									{
										quadResults = new HashSet<VariablesPartitionNullness>();
										results.put(q, quadResults);
									}
									quadResults.add(par);
									totalNumber ++;
									resultsOut.println(q.toVerboseStr() +" "+par.toString());
								}
							}
						}
					}			
				}
			}
		}
		resultsOut.close();
		functionsOut.close();
		return totalNumber;
	}

	
	private int countStates(int countFilter)
	{

		int totalNumber = 0;
		Set<jq_Method> reachableMethods = cicg.getNodes();	
		HashMap<Integer, Integer>  sizeOfAbsRanges = null;
		if (countFilter == all)
		{
			sizeOfAbsRanges =  new HashMap<Integer, Integer> ();
		}
		for (jq_Method m : reachableMethods) 
		{		
			for (BasicBlock bb : m.getCFG().reversePostOrder()) 
			{
				if (!(bb.isEntry() || bb.isExit()))
				{
					for (int i=0; i<bb.size(); i++)
					{
						Quad q = bb.getQuad(i);
						if ((countFilter == onlyChosen && chosenQuads.contains(q) ) ||
						    (countFilter == onlyNotChosen && !chosenQuads.contains(q)) || 
						    (countFilter == all))
						{
							Set<EdgeNullness> peSet = pathEdges.get(q);
							if (peSet != null)
							{
								for (EdgeNullness e:peSet)
								{	
									VariablesPartitionNullness par = e.dstNode.partition;
									if (par != null)
									{
										totalNumber ++;
									}
								}
								if (countFilter == all)
								{
									int partSetSize = peSet.size();
									Integer currentCount = sizeOfAbsRanges.get(partSetSize);
									if (currentCount == null)
									{
										sizeOfAbsRanges.put(partSetSize, 1);
									}
									else
									{
										sizeOfAbsRanges.remove(partSetSize);
										int currentCountInt = currentCount.intValue();
										currentCountInt++;
										sizeOfAbsRanges.put(partSetSize, currentCountInt);
									}
								}
							}
						}
					}			
				}
			}
		}
		if (countFilter == all)
		{
			Utils.serializeRangesSingle("sizeOfAbstrTDNullness.csv", sizeOfAbsRanges);
		}
		return totalNumber;
	}

	private String toString(Set<EdgeNullness> peSet, jq_Method m) {
		if (peSet == null)
			return "No edges<br>";
		String s = "";
		for (EdgeNullness pe : peSet)
			s += "<pre>" + toString(pe, m) + "</pre>";
		return s;
	}

	private String toString(EdgeNullness pe, jq_Method m) {
		
		if (pe != null && pe.dstNode != null  && pe.dstNode.partition != null)
		{
			return pe.dstNode.partition.toString();
		}
		else
		{
			return "";
		}
	}
		
	private void compareResults(boolean timeout) {
			
		System.out.println("comparing results");
		Calendar calendar = new GregorianCalendar();
		System.out.println("time is:"+ calendar.getTime().toString());
		PrintWriter nullResultsOut  = OutDirUtils.newPrintWriter("nullResults.txt");	
		PrintWriter resultsOut  = OutDirUtils.newPrintWriter("resultsNullness.txt");
		PrintWriter mismatchesOut  = OutDirUtils.newPrintWriter("matchMismatch.txt");
		PrintWriter statesSizeOut = null;
		try {
			statesSizeOut = new PrintWriter(new FileWriter(new File(Config.outDirName, "statesSizeNull.txt"), true));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		double reachedQ = 0;
		double totalLocalSize = 0;
	
		double totalSizeOfSetBULoad = 0;
		double totalSizeOfSetTDLoad = 0;
		double totalSizeOfSetBUField = 0;
		double totalSizeOfSetTDField = 0;
		double totalSizeOfTDPart = 0;
		double totalSizeOfBUPart = 0;
		HashMap<Integer, Integer> sizeOfSetBULoadRanges =  new HashMap<Integer, Integer> ();
		HashMap<Integer, Integer> sizeOfSetBUFieldRanges =  new HashMap<Integer, Integer> ();
		HashMap<Integer, Integer> sizeOfSetTDLoadRanges =  new HashMap<Integer, Integer> ();
		HashMap<Integer, Integer> sizeOfSetTDFieldRanges =  new HashMap<Integer, Integer> ();
		HashMap<Integer, Integer> sizeOfPartTDRanges =  new HashMap<Integer, Integer> ();
		HashMap<Integer, Integer> sizeOfPartBURanges =  new HashMap<Integer, Integer> ();
		boolean result = true;
		
		int testedQuads = 0;
		int testedQuadsLoad = 0;
		int testedQuadsField = 0;
		
		for (Quad q: chosenQuads)
		{
			if (!(q.getOperator() instanceof Operator.ALoad || 
				q.getOperator() instanceof Operator.AStore ||
				q.getOperator() instanceof Operator.Putfield ||
				q.getOperator() instanceof Operator.Getfield)) continue;
			
			Set<VariablesPartition> BUPart = bottomUpResults.get(q);
			Set<VariablesPartitionNullness> TDPart = results.get(q);
			
			if (TDPart == null && !timeout)
			{
				if (!q.getBasicBlock().getQuad(0).toVerboseStr().contains("GET_EXCEPTION"))
				{
					nullResultsOut.println(q.toVerboseStr());
					BasicBlock bb = q.getBasicBlock();
					nullResultsOut.println(bb.getQuads());
					nullResultsOut.println();
				}
			}
			else
			{
				reachedQ ++;
			}
			if (!timeout && 
				(((BUPart == null) && (TDPart != null)) ||
				 ((TDPart == null) && (BUPart != null))))
			{
				resultsOut.println("There was a bug in quad "+q.toVerboseStr()+"because"+ (BUPart == null ? "BUPart" : "TDPart") + "is null" );
				result = false;
			}
			
			if ((BUPart != null))
			{	
				RegisterOperand reg = null;
				if (q.getOperator() instanceof Operator.ALoad)
				{
					 reg = (RegisterOperand) ALoad.getBase(q);		
					 testedQuadsLoad ++;
				}
				else if (q.getOperator() instanceof Operator.AStore)
				{
					reg = (RegisterOperand) AStore.getBase(q);
					testedQuadsLoad ++;
				}
				else if (q.getOperator() instanceof Operator.Putfield)
				{
					reg = (RegisterOperand) Putfield.getBase(q);
					testedQuadsField++;
				}
				else if	(q.getOperator() instanceof Operator.Getfield)
				{
					reg = (RegisterOperand) Getfield.getBase(q);
					testedQuadsField ++;
				}
				else assert (false);
				testedQuads ++;
				int rIdx = utils.getAbsIdx(reg);
				LocalVariable v = LocalVariable.getNewLocalVariable(rIdx);
				Set<Variable> equivVarsBU = new HashSet<Variable>();
				Set<Variable> equivVarsBULoc = null; 
				VariablesPartition mergedPartitionBU = null;
				int partSizeBU=0;
				equivVarsBU.add(v);
				for (VariablesPartition p: BUPart)
				{
					Set<Variable> vSet = p.getSet(v);
					if (vSet != null)
					{
						equivVarsBU.addAll(vSet);		
					}
					if (mergedPartitionBU == null)
					{
						mergedPartitionBU = p;
					}
					else
					{
						mergedPartitionBU = mergedPartitionBU.merge(p);
					}	
				}
				
				partSizeBU = mergedPartitionBU.getRelativeSize(q.getMethod().getLiveRefVars());
				equivVarsBULoc = getNewLocalVars(equivVarsBU);
				Set<Variable> equivVarsTD = new HashSet<Variable>();
				Set<Variable> equivVarsTDLoc = null;
				equivVarsTD.add(v);
				int partSizeTD = 0;
				VariablesPartitionNullness mergedPartitionTD= null;
				if (TDPart != null)
				{
					for (VariablesPartitionNullness p: TDPart)
					{
						Set<Variable> nullSet = p.getSet(VariablesPartitionNullness.NULL_VAR);
						Set<Variable> vSet = p.getSet(v);
						if (vSet != null && vSet != nullSet)
						{
							equivVarsTD.addAll(vSet);	
						}
						if(mergedPartitionTD == null)
						{
							mergedPartitionTD = p;
						}
						else{
							mergedPartitionTD = (VariablesPartitionNullness) mergedPartitionTD.merge(p);
						}
						
					}
					partSizeTD = mergedPartitionTD.getRelativeSizeNullness(q.getMethod().getLiveRefVars());
					equivVarsTDLoc = getNewLocalVars(equivVarsTD);
		
					if (!(equivVarsTDLoc == null || (equivVarsBULoc != null  && equivVarsBULoc.containsAll(equivVarsTDLoc))))
					{
						System.out.println(q.toVerboseStr());
						System.out.println(equivVarsTDLoc);
						System.out.println(equivVarsBULoc);
						System.out.println(equivVarsBU);
						System.out.println(equivVarsTD);
						assert(false);
					}
				}
				int equivVarsBULocSize = equivVarsBULoc == null ? 0 : (equivVarsBULoc.isEmpty() ? 1 : equivVarsBULoc.size());
				int equivVarsTDLocSize = equivVarsTDLoc == null ? 0 : (equivVarsTDLoc.isEmpty() ? 1 : equivVarsTDLoc.size());

				if (q.getOperator() instanceof Operator.ALoad || q.getOperator() instanceof Operator.AStore)
				{
					totalSizeOfSetBULoad += equivVarsBULocSize;
					totalSizeOfSetTDLoad += equivVarsTDLocSize;
					if (equivVarsBULoc != null)
					{
						Integer currentCount = sizeOfSetBULoadRanges.get(equivVarsBULocSize);
						if (currentCount == null)
						{
							sizeOfSetBULoadRanges.put(equivVarsBULocSize, 1);
						}
						else
						{
							sizeOfSetBULoadRanges.remove(equivVarsBULocSize);
							int currentCountInt = currentCount.intValue();
							currentCountInt++;
							sizeOfSetBULoadRanges.put(equivVarsBULoc.size(), currentCountInt);
						}
					}
					if (equivVarsTDLoc != null)
					{
						Integer currentCount = sizeOfSetTDLoadRanges.get(equivVarsTDLocSize);
						if (currentCount == null)
						{
							sizeOfSetTDLoadRanges.put(equivVarsTDLocSize, 1);
						}
						else
						{
							sizeOfSetTDLoadRanges.remove(equivVarsTDLocSize);
							int currentCountInt = currentCount.intValue();
							currentCountInt++;
							sizeOfSetTDLoadRanges.put(equivVarsTDLocSize, currentCountInt);
						}
					}					
				}
				if (q.getOperator() instanceof Operator.Putfield || q.getOperator() instanceof Operator.Getfield)
				{
					totalSizeOfSetBUField += equivVarsBULocSize;
					totalSizeOfSetTDField += equivVarsTDLocSize;
					
					if (equivVarsBULoc != null)
					{
						Integer currentCount = sizeOfSetBUFieldRanges.get(equivVarsBULocSize);
						if (currentCount == null)
						{
							sizeOfSetBUFieldRanges.put(equivVarsBULocSize, 1);
						}
						else
						{
							sizeOfSetBUFieldRanges.remove(equivVarsBULocSize);
							int currentCountInt = currentCount.intValue();
							currentCountInt++;
							sizeOfSetBUFieldRanges.put(equivVarsBULocSize, currentCountInt);
						}				
					}
					if (equivVarsTDLoc != null)
					{
						Integer currentCount = sizeOfSetTDFieldRanges.get(equivVarsTDLocSize);
						if (currentCount == null)
						{
							sizeOfSetTDFieldRanges.put(equivVarsTDLocSize, 1);
						}
						else
						{
							sizeOfSetTDFieldRanges.remove(equivVarsTDLocSize);
							int currentCountInt = currentCount.intValue();
							currentCountInt++;
							sizeOfSetTDFieldRanges.put(equivVarsTDLocSize, currentCountInt);
						}	
					}
				}
			
				totalSizeOfBUPart += partSizeBU;
				totalSizeOfTDPart += partSizeTD;
			
				Integer currentCount = sizeOfPartBURanges.get(partSizeBU);
				if (currentCount == null)
				{
					sizeOfPartBURanges.put(partSizeBU, 1);
				}
				else
				{
					sizeOfPartBURanges.remove(sizeOfPartBURanges);
					int currentCountInt = currentCount.intValue();
					currentCountInt++;
					sizeOfPartBURanges.put(partSizeBU, currentCountInt);
				}
				currentCount = sizeOfPartTDRanges.get(partSizeTD);
				if (currentCount == null)
				{
					sizeOfPartTDRanges.put(partSizeTD, 1);
				}
				else
				{
					sizeOfPartTDRanges.remove(partSizeTD);
					int currentCountInt = currentCount.intValue();
					currentCountInt++;
					sizeOfPartTDRanges.put(partSizeTD, currentCountInt);
				}
				
				totalLocalSize += q.getMethod().getLiveRefVars().size();
			}
			if (TDPart != null)
			{
				TDsize += TDPart.size();
			}
			if (BUPart != null)
			{
				BUsize += BUPart.size();
			}
		}
		assert(testedQuads == testedQuadsLoad + testedQuadsField);
		statesSizeOut.println("######################################################################");
		statesSizeOut.println("total average size for BU for heap accessing statements of type LOAD/STORE "+ totalSizeOfSetBULoad/testedQuadsLoad);
		statesSizeOut.println("total average size for TD for heap accessing statements LOAD/STORE "+ totalSizeOfSetTDLoad/testedQuadsLoad);
		statesSizeOut.println("total average relation TD/BU for heap accessing statements LOAD/STORE "+ totalSizeOfSetTDLoad/totalSizeOfSetBULoad);
		statesSizeOut.println("total heap accessing statements LOAD/STORE "+ testedQuadsLoad);
		statesSizeOut.println("total average size for BU for heap accessing statements of type GETFIELD/PUTFIELD "+ totalSizeOfSetBUField/testedQuadsField);
		statesSizeOut.println("total average size for TD for heap accessing statements GETFIELD/PUTFIELD "+ totalSizeOfSetTDField/testedQuadsField);
		statesSizeOut.println("total average relation TD/BU for heap accessing statements GETFIELD/PUTFIELD "+ totalSizeOfSetTDField/totalSizeOfSetBUField);
		statesSizeOut.println("total heap accessing statements GETFIELD/PUTFIELD "+ testedQuadsField);
		statesSizeOut.println("total average size for BU for heap accessing statements "+ (totalSizeOfSetBUField + totalSizeOfSetBULoad)/testedQuads);
		statesSizeOut.println("total average size for TD for heap accessing statements "+ (totalSizeOfSetTDField + totalSizeOfSetTDLoad)/testedQuads);
		statesSizeOut.println("total average relation TD/BU for heap accessing statements "+ (totalSizeOfSetTDField + totalSizeOfSetTDLoad)/(totalSizeOfSetBUField + totalSizeOfSetBULoad));
		statesSizeOut.println("total heap accessing statements "+ testedQuads);
		statesSizeOut.println("total average size of TD partitions "+ (totalSizeOfTDPart/testedQuads));
		statesSizeOut.println("total average size of BU partitions "+ (totalSizeOfBUPart/testedQuads));
		statesSizeOut.println("total average TD partitions/locals "+ (totalSizeOfTDPart/totalLocalSize));
		statesSizeOut.println("total average BU partitions/locals  "+ (totalSizeOfBUPart/totalLocalSize));
		statesSizeOut.println("total average locals per quad "+ (totalLocalSize/testedQuads));
		
		//statesSizeOut.println("total static Rels "+ totalStaticRels);
		statesSizeOut.println("######################################################################");
	//	statesSizeOut.println("The average size of partition is: "+ (totalNumberOfPartitions / BUsize));
		statesSizeOut.println("######################################################################");
		statesSizeOut.println("The total number of states for TD out of "+reachedQ+" reached quads is: "+ TDsize);
		statesSizeOut.println("The total number of states for BU out of "+reachedQ+" reached quads is: "+ BUsize);
	//	statesSizeOut.println("The average size of state for TD out of "+reachedQ+" reached quads is: "+ (TDsize / reachedQ));
	//	statesSizeOut.println("The average size of state for BU out of "+reachedQ+" reached quads is: "+ (BUsize / reachedQ));
		statesSizeOut.println("######################################################################");
		statesSizeOut.println("The ranges BU for heap accessing statements of type GETFIELD/PUTFIELD " + sizeOfSetBUFieldRanges);
		statesSizeOut.println("The ranges TD for heap accessing statements of type GETFIELD/PUTFIELD " + sizeOfSetTDFieldRanges);
		statesSizeOut.println("The ranges BU for heap accessing statements of type LOAD/STORE " + sizeOfSetBULoadRanges);
		statesSizeOut.println("The ranges TD for heap accessing statements of type LOAD/STORE " + sizeOfSetTDLoadRanges);
		statesSizeOut.println("The ranges of BU partSize for heap accessing statements " + sizeOfPartBURanges);
		statesSizeOut.println("The ranges of TD partSize for heap accessing statements " + sizeOfPartTDRanges);
		
	
			
		Utils.serializeRanges("load.csv",sizeOfSetTDLoadRanges, sizeOfSetBULoadRanges);
		Utils.serializeRanges("field.csv", sizeOfSetTDFieldRanges, sizeOfSetBUFieldRanges);
		//Utils.serializeRanges("size.csv", sizeOfPartTDRanges, sizeOfPartBURanges);
		assert(result);
		resultsOut.close();
		statesSizeOut.close();
		mismatchesOut.close();
	}

	/* Private printing functions */
	
	


	private int getNewLocalPartSize(VariablesPartition mergedPartition) {
		int count = 0;
		for (Set<Variable> set : mergedPartition.partition)
		{
			if (!set.contains(VariablesPartitionNullness.NULL_VAR))
				if (getNewLocalVars(set).size() != 0)
				{
					count ++;
				}
		}
		return count;
	}


	private Set<Variable> getNewLocalVars(Set<Variable> vSet) {
		Set<Variable> newSet = new HashSet<Variable>();

		for (Variable v: vSet)
		{
			if (!v.isOld() && !v.isStatic) newSet.add(v);
		}
		return newSet;
	}


	private void printEdges(int pass) {
		File dir = new File(Config.outDirName, Integer.toString(pass));
		dir.mkdir();
		for (jq_Method m : summEdges.keySet()) {
			PrintWriter w = OutDirUtils.newPrintWriter(pass + "/"
					+ domM.indexOf(m) + ".html");
			w.println("<html><head></head><body>");
			w.println("Method: " + toHTMLStr(m) + "<br>");
			w.println("<br>Callers:<br>");
			for (Quad q : cicg.getCallers(m)) {
				jq_Method m2 = q.getMethod();
				w.println(q.getID() + ": " + toHTMLStr(m2) + "<br>");
			}
			w.println("<br>Summary Edges:");
			Set<EdgeNullness> seSet = summEdges.get(m);
			w.print(toString(seSet, m));
			ControlFlowGraph cfg = m.getCFG();
			w.println("<pre>");
			w.println("Register Factory: " + cfg.getRegisterFactory());
			for (BasicBlock bb : cfg.reversePostOrder()) {
				Iterator<BasicBlock> bbi;
				String s = bb.toString() + "(in: ";
				bbi = bb.getPredecessors().iterator();
				if (!bbi.hasNext())
					s += "none";
				else {
					s += bbi.next();
					while (bbi.hasNext())
						s += ", " + bbi.next();
				}
				s += ", out: ";
				bbi = bb.getSuccessors().iterator();
				if (!bbi.hasNext())
					s += "none";
				else {
					s += bbi.next();
					while (bbi.hasNext())
						s += ", " + bbi.next();
				}
				s += ")";
				for (Quad q : bb.getQuads()) {
					s += "\n" + q;
					if (q.getOperator() instanceof Invoke) {
						Set<jq_Method> tgts = cicg.getTargets(q);
						for (jq_Method m2 : tgts)
							s += "\n" + toHTMLStr(m2);
					}
				}
				w.println(s);
			}
			w.println("</pre>");
			for (BasicBlock bb : m.getCFG().reversePostOrder()) {
				if (bb.isEntry() || bb.isExit()) {				
					Set<EdgeNullness> peSet = pathEdges.get(bb);
					w.println(toString(peSet, m));
					w.println(bb.isEntry() ? "ENTRY:" : "EXIT:");
				} else {
					for (Quad q : bb) {
						Set<EdgeNullness> peSet = pathEdges.get(q);
						w.println(toString(peSet, m));
						w.println(q.toString() + ":");
					}
				}
			}
			w.println("</body>");
			w.close();
		}
	}
	
	private String toHTMLStr(jq_Method m) {
		String s = m.toString().replace("<", "&lt;").replace(">", "&gt;");
		return "<a href=\"" + domM.indexOf(m) + ".html\">" + s + "</a>";
	}
	
	public static String toString(VariablesPartitionNullness p) {
		if (p != null) {
			return p.toString();
		} else
			return "";
	}
	
	
	public static String toString(VariablesPartition p) {
		if (p != null) {
			return p.toString();
		} else
			return "";
	}
	
	protected void printResults(String fileName) {
		
		Set<Quad> quads = results.keySet();
		PrintWriter resultsOut  = OutDirUtils.newPrintWriter(fileName);
		
		for (Quad q: quads)
		{
			resultsOut.println("Quad: "+ q + "result "+ results.get(q));
		}
		resultsOut.close();
	}
}
