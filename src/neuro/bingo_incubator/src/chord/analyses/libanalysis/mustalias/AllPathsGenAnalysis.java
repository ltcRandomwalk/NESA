package chord.analyses.libanalysis.mustalias;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.DomC;
import chord.analyses.alias.ICICG;
import chord.analyses.invk.DomI;
import chord.analyses.typestate.Edge;
import chord.program.Loc;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Project;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.IEdge;
import chord.project.analyses.rhs.MergeKind;
import chord.project.analyses.rhs.RHSAnalysis;
import chord.project.analyses.rhs.TimeoutException;
import chord.util.ArraySet;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

@Chord(name = "pathGen-java",
consumes = { "nodePairs", "libM", "trackedActualIMMinusOverridden", "trackedBaseIM", "reachedFromMM" },
produces = { "nodePairToPath" },
namesOfTypes = { "nodePairs", "nodePairToPath" },
types = { ArrayList.class, HashMap.class  }
		)
public class AllPathsGenAnalysis extends RHSAnalysis<Path, Path> {

	protected ArrayList<Pair<Inst,Inst>> nodePairList;
	protected HashMap<Pair<Inst,Inst>, List<Set<jq_Method>>> nodePairToPathMap;
	protected Map<Quad, Set<jq_Method>> invkToActualMethods;
	protected Map<Quad, jq_Method> invkToBaseMethod;
	protected Map<jq_Method, Set<jq_Method>> reachedFromMM;
	protected Set<jq_Method> trackedActualMMinusOverridden;
	protected CIPAAnalysis cipa;
	protected ICICG cicg;
	protected String cipaName, cicgName;
	protected static boolean DEBUG = false;

	@Override
	public void run() {
		init();
		runPass();
		if (DEBUG) print();
		done();
	}

	@Override
	public void setMergeKind() {
		String s = System.getProperty("chord.rhs.pathgen.merge", "lossy");
		if (s.equals("lossy"))
			mergeKind = MergeKind.LOSSY;
		else if (s.equals("pjoin"))
			mergeKind = MergeKind.PJOIN;
		else if (s.equals("naive"))
			mergeKind = MergeKind.NAIVE;
		else
			throw new RuntimeException("Bad value for property chord.rhs.pathgen.merge: " + s);
	}

	@Override
	public void init(){
		//super.DEBUG = DEBUG;

		// build map invokeToActualMethods
		{
			ProgramRel relTrackedActualIM = (ProgramRel) ClassicProject.g().getTrgt("trackedActualIMMinusOverridden");
			relTrackedActualIM.load();
			invkToActualMethods = new HashMap<Quad, Set<jq_Method>>();
			trackedActualMMinusOverridden = new HashSet<jq_Method>();

			Iterable<Pair<Quad, jq_Method>> tuples = relTrackedActualIM.getAry2ValTuples();
			for (Pair<Quad, jq_Method> p : tuples) {
				trackedActualMMinusOverridden.add(p.val1);
				Set<jq_Method> methods = invkToActualMethods.get(p.val0);
				if (methods != null)
					methods.add(p.val1);
				else {
					methods = new HashSet<jq_Method>();
					methods.add(p.val1);
					invkToActualMethods.put(p.val0, methods);
				}
			}
			relTrackedActualIM.close();
		}

		// build map invokeToMethod
		{
			ProgramRel relTrackedBaseIM = (ProgramRel) ClassicProject.g().getTrgt("trackedBaseIM");
			relTrackedBaseIM.load();
			invkToBaseMethod = new HashMap<Quad, jq_Method>();

			Iterable<Pair<Quad, jq_Method>> tuples = relTrackedBaseIM.getAry2ValTuples();
			for (Pair<Quad, jq_Method> p : tuples) {
				invkToBaseMethod.put(p.val0, p.val1);
			}
			relTrackedBaseIM.close();
		}

		//build map reachableMM
		{
			ProgramRel relReachedFromMM = (ProgramRel) ClassicProject.g().getTrgt("reachedFromMM");
			relReachedFromMM.load();
			reachedFromMM = new HashMap<jq_Method, Set<jq_Method>>();

			Iterable<Pair<jq_Method, jq_Method>> tuples = relReachedFromMM.getAry2ValTuples();
			for (Pair<jq_Method, jq_Method> p : tuples) {
				Set<jq_Method> methods = reachedFromMM.get(p.val0);
				if (methods != null)
					methods.add(p.val1);
				else {
					methods = new HashSet<jq_Method>();
					methods.add(p.val1);
					reachedFromMM.put(p.val0, methods);
				}
			}
			relReachedFromMM.close();
		}

		cipaName = System.getProperty("chord.typestate.cipa", "cipa-java");
		cipa = (CIPAAnalysis) ClassicProject.g().getTask(cipaName);
		ClassicProject.g().runTask(cipa);

		cicgName = System.getProperty("chord.typestate.cicg", "cicg-java");
		CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTask(cicgName);
		ClassicProject.g().runTask(cicgAnalysis);
		cicg = cicgAnalysis.getCallGraph();

		nodePairList = (ArrayList<Pair<Inst,Inst>>) ClassicProject.g().getTrgt("nodePairs");
		nodePairToPathMap = (HashMap<Pair<Inst,Inst>, List<Set<jq_Method>>>) ClassicProject.g().getTrgt("nodePairToPath");

		super.isInit = false;
		super.init();
	}

	@Override
    protected boolean jumpToMethodEnd(Quad q, jq_Method m, Path predPe, Path Pe){    	
        Set<jq_Method> trackedMethods = invkToActualMethods.get(q);
        if (trackedMethods != null) 
            if (trackedMethods.contains(m)) 
                return true;
        
        return false;
    }
	
	@Override
	public void done(){
		System.out.println("RHS DONE");

		for(Pair<Inst, Inst> pair : nodePairList){
			List<Set<jq_Method>> pathList = nodePairToPathMap.get(pair);
			if(pathList == null){
				pathList = new ArrayList<Set<jq_Method>>();
				nodePairToPathMap.put(pair, pathList);
			}


			Set<Path> localInEdges = pathEdges.get(pair.val1);
			if(localInEdges != null){
				for(Path p : localInEdges){
					if(p.pathKind != PathKind.QUERY)
						continue;
					if(p.startInst == pair.val0 && p.endInst == pair.val1)	
						pathList.add(p.finalMethodList);
				}
			}

			Set<jq_Method> reachedFromM= reachedFromMM.get(pair.val1.getMethod());
			if(reachedFromM != null){
				for(jq_Method m : reachedFromM){
					Set<Quad> callers = getCallers(m);
					if(callers != null){
						for(Quad q : callers){
							Set<Path> inEdges = pathEdges.get(q);
							if(inEdges != null){
								for(Path p : inEdges){
									if(p.pathKind == PathKind.QUERY && p.startInst == pair.val0 && p.endInst == pair.val1){
										Set<Path> mSummEdges = summEdges.get(m);
										if(mSummEdges != null){
											for(Path s : mSummEdges){
												if(s.pathKind == PathKind.PARTIALCOLLECTDONE && s.endInst == pair.val1){
													Set<jq_Method> comboMList = new LinkedHashSet<jq_Method>(p.finalMethodList);
													comboMList.addAll(s.finalMethodList);
													pathList.add(comboMList);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		ClassicProject.g().setTrgtDone("nodePairToPath");
		super.done();
	}

	@Override
	public Set<Pair<Loc, Path>> getInitPathEdges() {
		if(DEBUG) System.out.println("\nENTER getInitPathEdges:");
		Set<Pair<Loc, Path>> initPEs = new ArraySet<Pair<Loc, Path>>();

		ProgramRel relExcludedM = (ProgramRel) ClassicProject.g().getTrgt("libM");
		relExcludedM.load();

		for (jq_Method m : cicg.getNodes()) {
			BasicBlock bb = null;
			if(relExcludedM.contains(m)){
				if(trackedActualMMinusOverridden.contains(m)){
					bb = m.getCFG().exit();
				}else{
					continue;
				}
			}else{
				bb = m.getCFG().entry();
			}

			Loc loc = new Loc(bb, -1);
			Pair<Loc, Path> collectPair = new Pair<Loc, Path>(loc, new Path(PathKind.COLLECT));
			Pair<Loc, Path> nullPair = new Pair<Loc, Path>(loc, Path.NULL);
			initPEs.add(collectPair);
			initPEs.add(nullPair);
		}

		for(Pair<Inst,Inst> nodePair : nodePairList){
			Inst i = nodePair.val0;
			Pair<Loc, Path> queryPair = new Pair<Loc, Path>(new Loc(i, i instanceof BasicBlock? -1 : i.getBasicBlock().getQuadIndex((Quad) i)), 
					new Path(i,nodePair.val1));
			initPEs.add(queryPair);

			Pair<Loc, Path> partialCollectPair = new Pair<Loc, Path>(new Loc(nodePair.val1.getMethod().getCFG().entry(), -1),new Path(nodePair.val1));
			initPEs.add(partialCollectPair);
		}

		if(DEBUG) {
			for(Pair<Loc, Path> p : initPEs)
				System.out.println(p);
			System.out.println("EXIT getInitPathEdges:");
		}
		
		relExcludedM.close();
		return initPEs;
	}

	@Override
	public Path getInitPathEdge(Quad q, jq_Method m, Path pe) {
		if(DEBUG) System.out.println("\nENTER getInitPathEdge: \npe=" + pe + " \nMethod: " + m);

		if(DEBUG) System.out.println("EXIT getInitPathEdge: \npe=" + Path.NULL);
		return Path.NULL;
	}

	private jq_Method libMethodCheck(Quad q, jq_Method m){
		boolean includedM = false;
		Set<jq_Method> methods = invkToActualMethods.get(q);
		if (methods != null && methods.contains(m))
			includedM = true;

		if(includedM){
			return(invkToBaseMethod.get(q));
		}
		return null;
	}

	@Override
	public Path getMiscPathEdge(Quad q, Path pe) {
		//	if(DEBUG) System.out.println("ENTER getMiscPathEdge");
		return getCopy(pe);
	}

	@Override
	public Path getInvkPathEdge(Quad q, Path clrPE, jq_Method m, Path tgtSE) {
		if(DEBUG) System.out.println("\nENTER getInvkPathEdge: \nclrPE=" + clrPE + " \ntgtSE=" + tgtSE);


		switch(clrPE.pathKind){
		case NULL:
			switch(tgtSE.pathKind){
			case NULL:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + Path.NULL);
				return Path.NULL;
			case QUERY:
				if(tgtSE.initMethodList.isEmpty()){
					if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + tgtSE);
					return getCopy(tgtSE);
				}else{
					if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
					return null;
				}
			default:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
				return null;
			}

		case COLLECT:
			switch(tgtSE.pathKind){
			case COLLECT:
			{
				Path newP = getCopy(clrPE);
				jq_Method n = libMethodCheck(q, m);
				if(n != null)
					newP.finalMethodList.add(n);
				newP.finalMethodList.addAll(tgtSE.finalMethodList);
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + newP);
				return newP;
			}
			case PARTIALCOLLECTDONE:
			{
				Path newP = getCopy(clrPE);
				jq_Method n = libMethodCheck(q, m);
				if(n != null)
					newP.finalMethodList.add(n);
				newP.finalMethodList.addAll(tgtSE.finalMethodList);

				newP.pathKind = PathKind.PARTIALCOLLECTDONE;
				newP.endInst = tgtSE.endInst;

				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + newP);
				return newP;
			}	
			default:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
				return null;
			}

		case QUERY:
			switch(tgtSE.pathKind){
			case COLLECT:
				Path newP = getCopy(clrPE);
				jq_Method n = libMethodCheck(q, m);
				if(n != null)
					newP.finalMethodList.add(n);
				newP.finalMethodList.addAll(tgtSE.finalMethodList);
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + newP);
				return newP;

			default:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
				return null;
			}

		case PARTIALCOLLECTDONE:
			switch(tgtSE.pathKind){
			case NULL:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + Path.NULL);
				return getCopy(clrPE);

			default:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
				return null;

			}

		case PARTIALCOLLECT: //Hack to generate 2 edges from a single original edge
			boolean isEndInst = q==clrPE.endInst;

			switch(tgtSE.pathKind){
			case NULL:
			{
				Path newP = getCopy(clrPE);
				if(isEndInst){
					newP.pathKind = PathKind.PARTIALCOLLECTDONE;
				}else{
					newP = null;
				}

				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + newP);
				return newP;
			}

			case COLLECT:
			{
				Path newP = getCopy(clrPE);
				jq_Method n = libMethodCheck(q, m);
				if(n != null)
					newP.finalMethodList.add(n);

				newP.finalMethodList.addAll(tgtSE.finalMethodList);


				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + newP);
				return newP;
			}

			default:
				if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
				return null;
			}			

		default:
			if(DEBUG) System.out.println("EXIT getInvkPathEdge: \npe=" + null);
			return null;
		}
	}

	@Override
	public Path getPECopy(Path pe) {
		return getCopy(pe);
	}

	@Override
	public Path getSECopy(Path pe) {
		return getCopy(pe);
	}

	@Override
	public Path getSummaryEdge(jq_Method m, Path pe) {
		//	if(DEBUG) System.out.println("ENTER getSummaryPathEdge");
		return getCopy(pe);
	}

	@Override
	public ICICG getCallGraph() {
		return cicg;
	}

	private Path getCopy(Path p){
		if(p == Path.NULL) return Path.NULL;
		return new Path(p.startInst, p.endInst, p.initMethodList, p.finalMethodList, p.pathKind);
	}
}



