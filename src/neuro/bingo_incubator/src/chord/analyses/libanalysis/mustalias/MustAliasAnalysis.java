package chord.analyses.libanalysis.mustalias;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.io.PrintWriter;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alloc.DomH;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.typestate.AbstractState;
import chord.analyses.typestate.AccessPath;
import chord.analyses.typestate.Edge;
import chord.analyses.typestate.EdgeKind;
import chord.analyses.typestate.Helper;
import chord.analyses.typestate.TypeStateParser;
import chord.analyses.typestate.RegisterAccessPath;
import chord.analyses.typestate.TypeState;
import chord.analyses.typestate.TypeStateAnalysis;
import chord.analyses.typestate.TypeStateSpec;
import chord.bddbddb.Rel.PairIterable;
import chord.program.Loc;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.BackTraceIterator;
import chord.project.analyses.rhs.MergeKind;
import chord.project.analyses.rhs.RHSAnalysis;
import chord.util.ArraySet;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.project.OutDirUtils;

@Chord(name = "mustalias-java",
	consumes = { "trackedM", "libM", "trackedBaseIM", "trackedActualIMMinusOverridden", "checkIncludedI", "reachableFromOverriddenM"}
)
public class MustAliasAnalysis extends TypeStateAnalysis {
	protected Map<Quad, Set<jq_Method>> invkToActualMethods;
	protected Map<Quad, jq_Method> invkToBaseMethod;
	protected Set<jq_Method> worstTrackedM, overriddenM, trackedActualMMinusOverridden;
	protected Set<Quad> checkIncludedI;
	private boolean isInit;
	private AnalysisType analysisType;
	private TypeState bestState;

	@Override
	public TypeStateSpec getTypeStateSpec() {
		TypeStateSpec tss = super.getTypeStateSpec();
		tss.addState("Best");
		return tss;
	}

	@Override
	public void init() {
		// XXX: do not compute anything here which needs to be re-computed on each call to run() below.

		if (isInit) return;
		isInit = true;

		super.init();  // XXX: needed to compute trackedSites which are used below

		bestState = sp.getState("Best");
		
		// build set trackedSites again
		{
			for (Iterator<Quad> i = trackedSites.iterator(); i.hasNext();) {
				Quad q = i.next();
				String qType = DomH.getType(q);
				if ((qType.startsWith("java.lang.String") || qType.startsWith("java.lang.StringBuilder") || 
						qType.startsWith("java.lang.StringBuffer") || qType.contains("Exception"))) {
					i.remove();
				}
	    	}
		}
		
		{
			checkIncludedI = new HashSet<Quad>();
	
			ProgramRel relI = (ProgramRel) ClassicProject.g().getTrgt("checkIncludedI");
			relI.load();
			Iterable<Quad> tuples = relI.getAry1ValTuples();
			for (Quad q : tuples)
				checkIncludedI.add(q);
			relI.close();
		}
		
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
				if (invkToActualMethods.get(p.val0) != null)
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

		{
			overriddenM = new HashSet<jq_Method>();
			
			jq_Class runnableInterface = (jq_Class) Program.g().getClass("java.lang.Runnable");
			jq_Class threadClass = (jq_Class) Program.g().getClass("java.lang.Thread");

			ProgramRel relM = (ProgramRel) ClassicProject.g().getTrgt("reachableFromOverriddenM");
			relM.load();
			PairIterable<jq_Method,jq_Method> overriddenMethods = relM.getAry2ValTuples();
			for (Pair<jq_Method, jq_Method> m : overriddenMethods) {
				overriddenM.add(m.val1);
			}
			relM.close();
		}
		
		{
			ProgramRel relExcludedM = (ProgramRel) ClassicProject.g().getTrgt("libM");
			relExcludedM.load();
			Iterable<jq_Method> excludedMethods = relExcludedM.getAry1ValTuples();
			for(jq_Method m : excludedMethods){
				methodToModFields.remove(m);
			}
			relExcludedM.close();
		}
	}

	public void setAnalysisType() {
		String s = System.getProperty("chord.mustaliaslibanalysis.type", "noop");
		if (s.equals("noop"))
			analysisType = AnalysisType.NOOP;
		else if (s.equals("wildcard"))
			analysisType = AnalysisType.WILDCARD;
		else if (s.equals("oracle"))
			analysisType = AnalysisType.ORACLE;
		else
			throw new RuntimeException("Bad value for property chord.mustalias.type: " + s);
	}
	
	@Override
	protected boolean jumpToMethodEnd(Quad q, jq_Method m, Edge predPe, Edge pe){
		if (analysisType != AnalysisType.WILDCARD && analysisType != AnalysisType.NOOP)
    		return false;
    	
        Set<jq_Method> trackedMethods = invkToActualMethods.get(q);
        if (trackedMethods != null) 
            if (trackedMethods.contains(m)) 
                return true;
        
        return false;
	}
	
    @Override
    protected boolean skipMethod(Quad q, jq_Method m, Edge predPe, Edge pe){
   /* 	if(pe.type == EdgeKind.FULL)
    		if(pe.srcNode.ms.isEmpty())
    			return true;
    */	
    	return false;
    }
    
	@Override
	public void run() {
		setAnalysisType();
		worstTrackedM = new HashSet<jq_Method>();
		
		if (analysisType != AnalysisType.WILDCARD && analysisType != AnalysisType.NOOP) {
			this.init();
			super.run();
		} else {
			// build set trackedMethods
			{
				ProgramRel relM = (ProgramRel) ClassicProject.g().getTrgt("trackedM");
				relM.load();
				Iterable<jq_Method> worstM = relM.getAry1ValTuples();
				for (jq_Method m : worstM) {
					worstTrackedM.add(m);
				}
				relM.close();
			}

			this.init();
			super.run();
		}
	}
	
	private static void printQueries(Set<Pair<Inst, Quad>> queries, String fileName) {
		PrintWriter out = OutDirUtils.newPrintWriter(fileName);
		for (Pair<Inst, Quad> e : queries) {
			out.println(e);
		}
		out.close();
	}

	@Override
	public Edge getInitPathEdge(Quad q, jq_Method m, Edge pe) {
		Edge newEdge = super.getInitPathEdge(q, m, pe);
		
		if (newEdge == Edge.NULL) return newEdge;
		
		if (analysisType != AnalysisType.NOOP && analysisType != AnalysisType.WILDCARD)
			return newEdge;
		
		boolean includedM = false;
		Set<jq_Method> methods = invkToActualMethods.get(q);
		if (methods != null && methods.contains(m))
			includedM = true;
		
		if (includedM) {
			jq_Method n = invkToBaseMethod.get(q);	
			// this is a call to a library method n (instance or static)
			// B => ms has a wildcard character & E => edge has been through a WORST case lib method last
			//   n is:  incoming   outgoing
			//   NO-OP   <S,m>       <S,m>
            //   NO-OP   <E,m>       <E,m>
			//	 NO-OP   <B,m> --------------> Will never happen with NO-OP case
			//   BEST    <S,m>       <B,m'>		where m' only includes local variables in caller
            //   BEST    <E,m>       <B,m>		where m 	" "	
			//	 BEST    <B,m> 		 <B,m> 		" "	
			//   WORST   <S,m>       <E,m'>		where m' only includes local variables in caller
            //   WORST   <E,m>       <E,m>		where m 	" "	
			//	 WORST   <B,m> 		 <E,m> 		" "	

			if (DEBUG) System.out.println("ENTER getInitPathEdge for MISSING method: q=" + q + " m=" + m + " pe=" + pe);
			
			if (DEBUG) System.out.println("Worst Behavior for the method: "+ worstTrackedM.contains(n));
			
			TypeState newTS = getTargetStateForExcluded(newEdge.dstNode.ts, worstTrackedM.contains(n));
			AbstractState dst;
			
			//ms will be populated with local variables in getInvkPathEdge
			if(worstTrackedM.contains(n) ){
				dst = new AbstractState(true, newTS, AbstractState.emptyMS);
			}else if (newTS == bestState && analysisType == AnalysisType.WILDCARD){
				dst = new AbstractState(false, newTS, AbstractState.emptyMS); 
			} else { //m != worstAbstraction && analysisType = No-op bestCase
				dst = new AbstractState(newEdge.dstNode.may, newTS, newEdge.dstNode.ms);
			}
			newEdge = new Edge(newEdge.srcNode, dst, newEdge.type, newEdge.h);
			if (DEBUG) System.out.println("LEAVE getInitPathEdge for MISSING method: " + newEdge);
		}
		
		return newEdge;
	}
	
	/*
	 * Modify so that edges created within a library method bypass the library body and end at the exit block
	 */
	@Override
	public Set<Pair<Loc, Edge>> getInitPathEdges() {
		Set<Pair<Loc, Edge>> initPEs = super.getInitPathEdges();
		
		if(analysisType != AnalysisType.WILDCARD && analysisType != AnalysisType.NOOP){
			return initPEs;
		}
		
		ProgramRel relExcludedM = (ProgramRel) ClassicProject.g().getTrgt("libM");
		relExcludedM.load();
		
		for (Iterator<Pair<Loc, Edge>> i = initPEs.iterator(); i.hasNext();) {
			Pair<Loc, Edge> p = i.next();
			jq_Method m = p.val0.i.getMethod();
			if(relExcludedM.contains(m)){
				if(trackedActualMMinusOverridden.contains(m)){
					BasicBlock bb = m.getCFG().exit();
					Loc loc = new Loc(bb, -1);
					p.val0 = loc;
					if (DEBUG) System.out.println("getInitPathEdges for MISSING method: Modified " + p);
				}else{
					i.remove();
					if (DEBUG) System.out.println("getInitPathEdges for MISSING method: Removed " + p);
				}
			}

		}
		
		relExcludedM.close();
		return initPEs;
	}
		
	@Override
	public boolean addFallThroughAccessPaths(Quad q, Edge clrPE, jq_Method m, Edge tgtSE, ArraySet<AccessPath> newMS, ArraySet<AccessPath> clrMS){
		
		/* Either analysisType is irrelevant  || tgtSE.dstNode.ts = startState or
		 * (analysisType == No-op && tgtSE.dstNode.ts != errorState)
		 */
		if((analysisType != AnalysisType.WILDCARD && analysisType != AnalysisType.NOOP) || (tgtSE.dstNode.ts != errorState && tgtSE.dstNode.ts != bestState ))
			return(super.addFallThroughAccessPaths(q, clrPE, m, tgtSE, newMS, clrMS));
		else{//tgtSE.dstNode.ts == errorState || tgtSE.dstNode.ts == bestState
			//NO action, since the commented work is also done by Step2 of newMS generation in getInvkPathEdge
			//	newMS.addAll(clrMS);
			//	Helper.removeAllExceptLocalVariables(newMS);
			return true;
		} 
	}
		
	private TypeState getTargetStateForExcluded(TypeState currState, boolean worstAbs){
		if (worstAbs) {
			return errorState;
		} else {
			if(analysisType == AnalysisType.WILDCARD)
				return bestState;
			else
				return currState;
		}
	}
	
	private static boolean isInterestingSite(Operator o) {
 		return o instanceof Invoke && !(o instanceof InvokeStatic);
	}

	private Map<Register, Set<Quad>> pointsToMap = new HashMap<Register, Set<Quad>>();

	private boolean pointsTo(Register v, Quad q) {
		Set<Quad> pts = pointsToMap.get(v);
		if (pts == null) {
			pts = cipa.pointsTo(v).pts;
			pointsToMap.put(v, pts);
		}
		return pts.contains(q);
	}
	
	private void getOverriddenMethodErrQueries(Set<Pair<Quad, Quad>> queries) {
		if (worstTrackedM.size() == 0)
			return;
		for (Quad q : checkIncludedI) {
			if (overriddenM.contains(q.getMethod())) {
				if (isInterestingSite(q.getOperator())) {
					Register v = Invoke.getParam(q, 0).getRegister();
					for(Quad h: trackedSites) {
						if (pointsTo(v, h)) {
							queries.add(new Pair<Quad, Quad>(q, h));	
						}
					}
				}
			}
		}
	}

	public Set<Pair<Quad, Quad>> getAllQueries() {
		Set<Pair<Quad, Quad>> retQuads = new HashSet<Pair<Quad, Quad>>();
		for (Quad q : checkIncludedI) {
			if (isInterestingSite(q.getOperator())) {
				Register v = Invoke.getParam(q, 0).getRegister();
				for (Quad h: cipa.pointsTo(v).pts) {
					if (trackedSites.contains(h)) {
						retQuads.add(new Pair<Quad, Quad>(q, h));
					}
				}
			}
		}
		return retQuads;
	}

	public Set<Pair<Quad, Quad>> getErrQueries() {
		Set<Pair<Quad, Quad>> retQuads = new HashSet<Pair<Quad, Quad>>();
		for (Inst x : pathEdges.keySet()) {
			if (x instanceof Quad) {
				Quad i = (Quad) x;
				if (checkIncludedI.contains(i) && isInterestingSite(i.getOperator())) {
					Set<Edge> peSet = pathEdges.get(i);
					if (peSet != null) {
						for (Edge pe : peSet) {
							if (pe.dstNode != null) {
								Quad q = pe.h;
								if (trackedSites.contains(q)) {
									Register v = Invoke.getParam(i, 0).getRegister();
									if (pointsTo(v, q)) {
										if (pe.dstNode.ts != bestState && Helper.getIndexInAP(pe.dstNode.ms,v) == -1 && pe.dstNode.may)
											retQuads.add(new Pair<Quad, Quad>(i, q));
									}
								}
							}
						}
					}
				}
			}
		}
		
		getOverriddenMethodErrQueries(retQuads);
		return retQuads;
	}
}