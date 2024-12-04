package chord.analyses.mustnotnull;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.alloc.DomH;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.analyses.method.DomM;
import chord.program.Loc;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Messages;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.RHSAnalysis;
import chord.util.ArraySet;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.project.Config;
import chord.program.Program;

/**
 * System properties:
 * 1. Max number of instance fields in any access path in any must set tracked
 *    chord.mustnotnull.maxdepth (default value: 6)
 *    
 *    TODO: Reflection handling is limited and needs to be fixed.
 *    Also, some unsoundness for the case where v = u.foo();
 *    but we don't know the target. In that case, currently the
 *    analysis just skips the instruction. Ideally, we want
 *    to remove all access paths starting with v.
 */
@Chord(name = "mustnotnull-java",
consumes = {"modMF","reflectUniqM", "V"},
produces = {"localNotNull"}
)
public class MustNotNullAnalysis extends RHSAnalysis<Edge, Edge> {
    protected static boolean DEBUG = false;
    protected CIPAAnalysis cipa;
    protected ICICG cicg;
    protected Map<jq_Method, Set<jq_Field>> methodToModFields;
    protected Set<jq_Method> reflectUniqM;
    protected Set<jq_Method> methodsWithoutSummaries;
    protected MyQuadVisitor qv = new MyQuadVisitor();
    // protected jq_Method threadStartMethod;
    public static int maxDepth;
    private int actualMaxDepth;
    private boolean isInit;
	private DomV domV;
	
    
    @Override
    public void init() {
        // XXX: do not compute anything here which needs to be re-computed on each call to run() below.

        if (isInit) return;
        isInit = true;

        // threadStartMethod = Program.g().getThreadStartMethod();
        
        actualMaxDepth = Integer.getInteger("chord.mustnotnull.maxdepth", 6);
        assert (actualMaxDepth >= 0);

        cipa = (CIPAAnalysis) ClassicProject.g().getTask("cipa-java");
        ClassicProject.g().runTask(cipa);

        CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTask("cicg-java");
        ClassicProject.g().runTask(cicgAnalysis);
        cicg = cicgAnalysis.getCallGraph();
        
        domV = (DomV) ClassicProject.g().getTrgt("V");
        
        super.init();

        // build map methodToModFields
        {
            ProgramRel relModMF = (ProgramRel) ClassicProject.g().getTrgt("modMF");
            ClassicProject.g().runTask("modMF-dlog");
            relModMF.load();
            methodToModFields = new HashMap<jq_Method, Set<jq_Field>>();
            Iterable<Pair<jq_Method, jq_Field>> tuples = relModMF.getAry2ValTuples();
            for (Pair<jq_Method, jq_Field> p : tuples) {
                jq_Method m = p.val0;
                Set<jq_Field> modFields = methodToModFields.get(m);
                if (modFields == null) {
                    modFields = new HashSet<jq_Field>();
                    methodToModFields.put(m, modFields);
                }
                modFields.add(p.val1);
            }
            relModMF.close();
        }
        
        {
			reflectUniqM = new HashSet<jq_Method>();
			ProgramRel relReflectUniqM = (ProgramRel) ClassicProject.g().getTrgt("reflectUniqM");
			relReflectUniqM.load();
			Iterable<jq_Method> methods = relReflectUniqM.getAry1ValTuples();
			for (jq_Method m : methods)
				reflectUniqM.add(m);
			relReflectUniqM.close();
		}
        methodsWithoutSummaries = new HashSet<jq_Method>();
    }

    @Override
    public void run() {
        init();
        maxDepth = 0;
        runPass();
        
        // Necessary to deal with methods without summaries
        // This is typically when reflection is not correctly resolved
        // leading to an infinite loop.
        for (jq_Method m : cicg.getNodes()) {
        	Set<Edge> seSet = summEdges.get(m);
        	if (seSet == null) {
        		methodsWithoutSummaries.add(m);
        	}
        }
        
        maxDepth = actualMaxDepth;
        runPass();
        if (DEBUG) print();
        printRelation();
        done();
    }

    @Override
    public ICICG getCallGraph() {
        return cicg;
    }
    
    /*
     * For each reachable method 'm' adds the following path edges:
     * 1. <null, null>
     * 2. FULL EDGE of form: <null, null>
     */
    @Override
    public Set<Pair<Loc, Edge>> getInitPathEdges() {
        Set<Pair<Loc, Edge>> initPEs = new ArraySet<Pair<Loc, Edge>>();
        Map<jq_Method, Loc> methToEntry = new HashMap<jq_Method, Loc>();
        for (jq_Method m : cicg.getNodes()) {
            BasicBlock bb = m.getCFG().entry();
            Loc loc = new Loc(bb, -1);
            methToEntry.put(m, loc);
            Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, Edge.NULL);
            if (DEBUG) System.out.println("getInitPathEdges: Added " + pair);
            initPEs.add(pair);
        }
        for (jq_Method m : cicg.getRoots()) {
            BasicBlock bb = m.getCFG().entry();
            Loc loc = new Loc(bb, -1);
            methToEntry.put(m, loc);
            ArraySet<AccessPath> ap = new ArraySet<AccessPath>();
            RegisterFactory rf = m.getCFG().getRegisterFactory();
            for (int i = 0; i < rf.size(); ++i) {
            	Register v = rf.get(i);
            	if (domV.contains(v))
            		ap.add(new RegisterAccessPath(v));
            }
            Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, new Edge(new AbstractState(ap), new AbstractState(ap)));
            if (DEBUG) System.out.println("getInitPathEdges: Added " + pair);
            initPEs.add(pair);
        }
        
        for (jq_Method m : reflectUniqM) {
        	 BasicBlock bb = m.getCFG().entry();
             Loc loc = new Loc(bb, -1);
             methToEntry.put(m, loc);
             ArraySet<AccessPath> ap = new ArraySet<AccessPath>();
             RegisterFactory rf = m.getCFG().getRegisterFactory();
             for (int i = 0; i < rf.size(); ++i) {
             	Register v = rf.get(i);
             	if (domV.contains(v))
             		ap.add(new RegisterAccessPath(v));
             }
             Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, new Edge(new AbstractState(ap), new AbstractState(ap)));
             if (DEBUG) System.out.println("getInitPathEdges: Added " + pair);
             initPEs.add(pair);
        }
        
        for (jq_Method m : methodsWithoutSummaries) {
        	Set<Edge> summEdges = new HashSet<Edge>();
        	summEdges.add(Edge.NULL);
        	this.summEdges.put(m, summEdges);
        }
 
        if (DEBUG){
            System.out.println("===== ENTER ALL QUERIES");
            for (Pair<Loc, Edge> pair : initPEs) {
                System.out.println(pair);
            }
            System.out.println("===== LEAVE ALL QUERIES");
        }
        return initPEs;
    }

    /*
     * If incoming path edge 'pe' is of the form <null, null>,
     * then do nothing: return null edge.
     *
     * If incoming path edge 'pe' is of the form <null, AS> or <AS', AS> then
     * create and return new path edge in callee of the form <AS1, AS2> where
     * AS1 and AS2 are as follows:
     *   mustnotnull-set of AS1 = mustnotnull-set of AS2 = subset of mustnotnull-set of AS consisting of two
     *   kinds of access paths: those of the form v.* where v is an actual argument (now
     *   replaced by the corresponding formal argument), and those of the form g.* where
     *   g is a static field.
     */
    @Override
    public Edge getInitPathEdge(Quad q, jq_Method m, Edge pe) {
        if (DEBUG) System.out.println("ENTER getInitPathEdge: q=" + q + " m=" + m + " pe=" + pe);
        if (pe == Edge.NULL || (pe.dstNode == null)) /*|| m == threadStartMethod)*/ {
            if (DEBUG) System.out.println("LEAVE getInitPathEdge: " + Edge.NULL);
            return Edge.NULL;
        }
        AbstractState oldDst = pe.dstNode;
        assert (oldDst != null);
        ArraySet<AccessPath> oldMS = oldDst.ms;
        ArraySet<AccessPath> newMS = new ArraySet<AccessPath>();
        // Build newMS in two steps
        // Step 1: for each r1.* where r1 is an actual arg of q, add r2.* where r2 is
        // the corresponding formal arg of m
        ParamListOperand args = Invoke.getParamList(q);
        RegisterFactory rf = m.getCFG().getRegisterFactory();
        boolean isthis = false;
        for (int i = 0; i < args.length(); i++) {
            Register actualReg = args.get(i).getRegister();
            Register formalReg = rf.get(i);
            for (int j = -1; (j = Helper.getPrefixIndexInAP(oldMS, actualReg, j)) >= 0;) {
                AccessPath oldAP = oldMS.get(j);
                AccessPath newAP = new RegisterAccessPath(formalReg, oldAP.fields);
                newMS.add(newAP);
                if (i == 0) isthis = true;
            }
        }
        // Step 2: add all g.*
        Helper.addAllGlobalAccessPath(newMS, oldMS);

        AbstractState newSrc = new AbstractState(newMS);
		AbstractState newDst = new AbstractState(newMS);
        Edge newEdge = new Edge(newSrc, newDst);
        if (DEBUG) System.out.println("LEAVE getInitPathEdge: " + newEdge);
        return newEdge;
    }

    @Override
    public Edge getMiscPathEdge(Quad q, Edge pe) {
        if (DEBUG) System.out.println("ENTER getMiscPathEdge: q=" + q + " pe=" + pe);
        if (pe == Edge.NULL) return pe;
        qv.istate = pe.dstNode;
        qv.ostate = pe.dstNode;
        // may modify only qv.ostate
        q.accept(qv);
        // XXX: DO NOT REUSE incoming PE (merge does strong updates)
        Edge newEdge = new Edge(pe.srcNode, qv.ostate);
        if (DEBUG) System.out.println("LEAVE getMiscPathEdge: ret=" + newEdge);
        return newEdge;
    }

    @Override
    public Edge getInvkPathEdge(Quad q, Edge clrPE, jq_Method m, Edge tgtSE) {
        if (DEBUG) System.out.println("ENTER getInvkPathEdge: q=" + q + " clrPE=" + clrPE + " m=" + m + " tgtSE=" + tgtSE);
/*
        if (m == threadStartMethod) {
            if (tgtSE == Edge.NULL) return getCopy(clrPE);
            return null;
        }
*/
        switch (clrPE.type) {
        case NULL:
            switch (tgtSE.type) {
            case NULL:
                if (DEBUG) System.out.println(Edge.NULL);
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: " + Edge.NULL);
                return Edge.NULL;
            case FULL:
                if (tgtSE.srcNode != null || tgtSE.dstNode == null) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                    return null;
                }
            }
            break;
        case FULL:
            switch (tgtSE.type) {
            case NULL:
                if (clrPE.dstNode == null) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: incoming clrPE");
                    return getCopy(clrPE);
                }
                if (!methodsWithoutSummaries.contains(m)) {
                	if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                	return null;
                }
                break;
            case FULL:
                if (clrPE.dstNode == null || tgtSE.srcNode == null) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                    return null;
                }
                // postpone check for equality of clrPE.dstNode.ms and tgtSE.srcNode.ms
                break;
            }
            break;
        }

        // At this point, we have one of the following three cases:
        //     clrPE                   tgtSE             condition
        // ============================================================
        // FULL:<AS1,AS2>       FULL:<AS3,AS4>     (need ms equality check below)
        // FULL:<null,AS1>      FULL:<AS2,AS3>     (need ms equality check below)
        // NULL:<null,null>     FULL:<null,AS>    None (need to generate suitable ms)
        // FULL:<AS1,AS2>       NULL:<null,null>  None (need to generate suitable fallthrough ms) [arises when method is one without summary]
        // FULL:<null,AS2>      NULL:<null,null>  None (need to generate suitable fallthrough ms) [arises when method is one without summary]

        ArraySet<AccessPath> newMS = new ArraySet<AccessPath>();
        ParamListOperand args = Invoke.getParamList(q);
        RegisterFactory rf = m.getCFG().getRegisterFactory();
        boolean removedViaModMF = false;

        if (clrPE.type == EdgeKind.FULL) {
            // Compare must sets; they should be equal in order to apply summary
            // Build this must set tmpMS in two steps
            ArraySet<AccessPath> tmpMS = new ArraySet<AccessPath>();

            // Step 1: for each r1.* in caller must set where r1 is an actual arg of q,
            // add r2.* where r2 is the corresponding formal arg
            ArraySet<AccessPath> clrMS = new ArraySet<AccessPath>(clrPE.dstNode.ms);
            for (int i = 0; i < args.length(); i++) {
                Register actualReg = args.get(i).getRegister();
                Register formalReg = rf.get(i);
                for (int j = -1; (j = Helper.getPrefixIndexInAP(clrPE.dstNode.ms, actualReg, j)) >= 0;) {
                    AccessPath oldAP = clrPE.dstNode.ms.get(j);
                    AccessPath newAP = new RegisterAccessPath(formalReg, oldAP.fields);
                    tmpMS.add(newAP);
                    clrMS.remove(oldAP);
                }
            }

            // Step 2: add all g.* in caller must set
            Helper.addAllGlobalAccessPath(tmpMS, clrPE.dstNode.ms);
            Helper.removeAllGlobalAccessPaths(clrMS);

            if (tgtSE.type == EdgeKind.FULL && !tgtSE.srcNode.ms.equals(tmpMS)) {
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null (must sets don't match)");
                return null;
            }

            // At this point we are done with tmpMS but we will still use clrMS.
            // Build final must set newMS in four steps.

            // Step 1: Add all x.* in caller must set where x is neither an actual arg nor a
            // static field, and no instance field in "*" is modified in the callee (as per modMF)
            removedViaModMF = addFallThroughAccessPaths(q, clrPE, m, tgtSE, newMS, clrMS);

            // Step 2: Add all caller local variables, i.e., paths r without any fields in caller
            // must set that were not added in step 1
            for (Iterator<AccessPath> i = clrPE.dstNode.ms.iterator(); i.hasNext();) {
                AccessPath ap = i.next();
                if (ap instanceof RegisterAccessPath && ap.fields.isEmpty())
                    newMS.add(ap);
            }

            // Step 3: Replace formals with actuals (effectively do reverse of above for loop)
            if (tgtSE.type == EdgeKind.FULL) {
            	for (int i = 0; i < args.length(); i++) {
            		Register formalReg = rf.get(i);
            		Register actualReg = args.get(i).getRegister();
            		for (int j = -1; (j = Helper.getPrefixIndexInAP(tgtSE.dstNode.ms, formalReg, j)) >= 0;) {
            			AccessPath oldAP = tgtSE.dstNode.ms.get(j);
            			AccessPath newAP = new RegisterAccessPath(actualReg, oldAP.fields);
            			newMS.add(newAP);
            		}
            	}
            }
            
            // Step 4: Add all g.* and return var; shared below with below else case,
            // where clrPE.type is NULL
        } else {
            // When clrPE.type is NULL and tgtSE.type is FULL, always return with suitable mustset

            // Step 1: Add elements to ms accessible via formal args
            for (int i = 0; i < args.length(); i++) {
                Register formalReg = rf.get(i);
                Register actualReg = args.get(i).getRegister();
                for (int j = -1; (j = Helper.getPrefixIndexInAP(tgtSE.dstNode.ms, formalReg, j)) >= 0;) {
                    AccessPath oldAP = tgtSE.dstNode.ms.get(j);
                    AccessPath newAP = new RegisterAccessPath(actualReg, oldAP.fields);
                    newMS.add(newAP);
                }
            }
            
        }

        //Though we add the return var to the new mustset, we ignore accesspaths of the form returVar.*;
        //leading to some imprecision
        Register tgtRetReg = (Invoke.getDest(q) != null) ? Invoke.getDest(q).getRegister() : null;
        if(tgtRetReg != null){
        	//New Fix (07/03/2013)
        	ArraySet<AccessPath> newMSWithoutRef = Helper.removeReference(newMS, tgtRetReg);
        	if (newMSWithoutRef != null) newMS = newMSWithoutRef;
        	if (tgtSE.type == EdgeKind.FULL && tgtSE.dstNode.canReturn) {
                newMS.add(new RegisterAccessPath(tgtRetReg));
            }
        }
        
        if (tgtSE.type == EdgeKind.FULL) Helper.addAllGlobalAccessPath(newMS, tgtSE.dstNode.ms);
        
        //TODO decide whether to drop edges like <h,{}>
//        if(clrPE.type == EdgeKind.NULL && tgtSE.type == EdgeKind.ALLOC && newMS.isEmpty())
//        	return null;
            
        AbstractState newDst = new AbstractState(newMS);
        EdgeKind newType = (clrPE.type == EdgeKind.NULL) ? EdgeKind.FULL : clrPE.type;
        Edge newEdge = new Edge(clrPE.srcNode, newDst);
        if (DEBUG) System.out.println("LEAVE getInvkPathEdge: " + newEdge);
        return newEdge;
    }

    // Refactored into a method to enable overloading later on
    public boolean addFallThroughAccessPaths(Quad q, Edge clrPE, jq_Method m, Edge tgtSE, ArraySet<AccessPath> newMS, ArraySet<AccessPath> clrMS) {
        newMS.addAll(clrMS);
        return(Helper.removeModifiableAccessPaths(methodToModFields.get(m), newMS));
    }
    
    @Override
    public Edge getPECopy(Edge pe) { return getCopy(pe); }

    @Override
    public Edge getSECopy(Edge se) { return getCopy(se); }

    private Edge getCopy(Edge pe) {
        if (DEBUG) System.out.println("Called Copy with: " + pe);
        return (pe == Edge.NULL) ? pe : new Edge(pe.srcNode, pe.dstNode);
    }

    @Override
    public Edge getSummaryEdge(jq_Method m, Edge pe) {
        if (DEBUG) System.out.println("\nCalled getSummaryEdge: m=" + m + " pe=" + pe);
        return getCopy(pe);
    }
    
    public void printRelation() {		
		ProgramRel relLocalNotNull = (ProgramRel) ClassicProject.g().getTrgt("localNotNull");
		relLocalNotNull.zero();
		for (Inst x : pathEdges.keySet()) {
			Set<Edge> peSet = pathEdges.get(x);
			Set<AccessPath> locals = null;
			if (peSet != null) {
				for (Edge pe : peSet) {
					if (pe.type == EdgeKind.FULL) {
						if (pe.dstNode != null) {
							if (locals == null) {
								locals = new HashSet<AccessPath>(pe.dstNode.ms);
							} else {
								locals.retainAll(pe.dstNode.ms);
							}
						} else {
							locals = new HashSet<AccessPath>();
							break;
						}
					}
				}
				
				if (locals != null) {
					for (AccessPath ap : locals) {
						if (ap instanceof RegisterAccessPath && ap.fields.size() == 0) {
							RegisterAccessPath rap = (RegisterAccessPath) ap;
							relLocalNotNull.add(x, rap.var);
						}
					}
				}
			}
		}
		relLocalNotNull.save();
    }

    public class MyQuadVisitor extends QuadVisitor.EmptyVisitor {
        public AbstractState istate;    // immutable, may be null
        public AbstractState ostate;    // mutable, initially ostate == istate
        public Quad h;                    // immutable, non-null
        @Override
        public void visitCheckCast(Quad q) {
            visitMove(q);
        }
        @Override
        public void visitMove(Quad q) {
        	if (istate == null) return;        // edge is FULL:<null, null>
        	// edge is FULL:<null, AS> or FULL:<AS', AS>
        	Register dstR = Move.getDest(q).getRegister();
        	Operand srcO = Move.getSrc(q);
        	ArraySet<AccessPath> oldMS = istate.ms;
        	ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
        	if (srcO instanceof RegisterOperand) {
        		Register srcR = ((RegisterOperand) Move.getSrc(q)).getRegister();
        		for (int i = -1; (i = Helper.getPrefixIndexInAP(oldMS, srcR, i)) >= 0;) {
        			if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
        			newMS.add(new RegisterAccessPath(dstR, oldMS.get(i).fields));
        		}
        	} else {
        		boolean isNull = false;
        		if (srcO instanceof AConstOperand) {
        			jq_Reference srcType = ((AConstOperand) srcO).getType();
        			if (srcType == jq_NullType.NULL_TYPE)
        				isNull = true;
        		}
        		if (!isNull && Move.getDest(q).getType().isReferenceType()) {
        			if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
        			newMS.add(new RegisterAccessPath(dstR));
        		}
        	}
        	if (newMS != null)
        		ostate = new AbstractState(newMS);
        }

        @Override
        public void visitPhi(Quad q) {
            if (istate == null) return;        // edge is FULL:<null, null>
         // edge is FULL:<null, AS> or FULL:<AS', AS>
            Register dstR = Phi.getDest(q).getRegister();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            ParamListOperand ros = Phi.getSrcs(q);
            int n = ros.length();
            for (int i = 0; i < n; i++) {
                RegisterOperand ro = ros.get(i);
                if (ro == null) continue;
                Register srcR = ((RegisterOperand) ro).getRegister();
                for (int j = -1; (j = Helper.getPrefixIndexInAP(oldMS, srcR, j)) >= 0;) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, oldMS.get(j).fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(newMS);
        }

        @Override
        public void visitNew(Quad q) {
            if (istate == null) {
            	// edge is FULL:<null, null>;
            	ArraySet<AccessPath> newMS = new ArraySet<AccessPath>(1);
            	Register dstR = New.getDest(q).getRegister();
            	newMS.add(new RegisterAccessPath(dstR));
            	ostate = new AbstractState(newMS);
            } else {
                // edge is FULL:<null, AS> or FULL:<AS', AS>
                Register dstR = New.getDest(q).getRegister();
                ArraySet<AccessPath> oldMS = istate.ms;
                ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
                if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                newMS.add(new RegisterAccessPath(dstR));
                ostate = new AbstractState(newMS);
            }
        }

        @Override
        public void visitNewArray(Quad q) {
            if (istate == null) {
            	// edge is FULL:<null, null>;
            	ArraySet<AccessPath> newMS = new ArraySet<AccessPath>(1);
            	Register dstR = New.getDest(q).getRegister();
            	newMS.add(new RegisterAccessPath(dstR));
            	ostate = new AbstractState(newMS);
            } else {
                // edge is FULL:<null, AS> or FULL:<AS', AS>
            	Register dstR = NewArray.getDest(q).getRegister();
            	ArraySet<AccessPath> oldMS = istate.ms;
                ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
                if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                newMS.add(new RegisterAccessPath(dstR));
                ostate = new AbstractState(newMS);
            }
        }

        @Override
        public void visitMultiNewArray(Quad q) {
        	if (istate == null) {
        		// edge is FULL:<null, null>;
        		ArraySet<AccessPath> newMS = new ArraySet<AccessPath>(1);
        		Register dstR = New.getDest(q).getRegister();
        		newMS.add(new RegisterAccessPath(dstR));
        		ostate = new AbstractState(newMS);
        	} else {
        		// edge is FULL:<null, AS> or FULL:<AS', AS>
        		Register dstR = MultiNewArray.getDest(q).getRegister();
        		ArraySet<AccessPath> oldMS = istate.ms;
                ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
                if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                newMS.add(new RegisterAccessPath(dstR));
                ostate = new AbstractState(newMS);
        	}
        }
        
        @Override
        public void visitALoad(Quad q) {
            if (istate == null) return;
            Register dstR = ALoad.getDest(q).getRegister();
            ArraySet<AccessPath> newMS = Helper.removeReference(istate.ms, dstR);
            if (newMS != null)
                ostate = new AbstractState(newMS);
        }

        @Override
        public void visitGetstatic(Quad q) {
            if (istate == null) return;
            Register dstR = Getstatic.getDest(q).getRegister();
            jq_Field srcF = Getstatic.getField(q).getField();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            for (int i = -1; (i = Helper.getPrefixIndexInAP(oldMS, srcF, i)) >= 0;) {
                if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                newMS.add(new RegisterAccessPath(dstR, oldMS.get(i).fields));
            }
            if (newMS != null) 
                ostate = new AbstractState(newMS);
        }

        @Override
        public void visitPutstatic(Quad q) {
            if (istate == null) return;
            jq_Field dstF = Putstatic.getField(q).getField();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstF);
            if (Putstatic.getSrc(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Putstatic.getSrc(q)).getRegister();
                for (int i = -1; (i = Helper.getPrefixIndexInAP(oldMS, srcR, i)) >= 0;) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new GlobalAccessPath(dstF, oldMS.get(i).fields));
                }
            }
            if (newMS != null) 
                ostate = new AbstractState(newMS);
        }

        /*
         * Outgoing APmust' is computed as follows:
         * Step 1: Capture effect of v.f = null, by removing all e.f.y from APmust such that may-alias(e, v)
         * Step 2: Capture effect of v.f = u as follows:
         *  Step 2.1: Whenever APmust contains u.y, add v.f.y
         *  Step 2.2: Remove any access paths added in Step 2.1 that exceed access-path-depth parameter.
         * 
         */
        @Override
        public void visitPutfield(Quad q) {
        	if (istate == null) return;
        	if (!(Putfield.getBase(q) instanceof RegisterOperand)) return;

        	Register dstR = ((RegisterOperand) Putfield.getBase(q)).getRegister();
        	jq_Field dstF = Putfield.getField(q).getField();
        	ArraySet<AccessPath> oldMS = istate.ms;
        	ArraySet<AccessPath> newMS = null;
        	for (AccessPath ap : oldMS) {
        		if (Helper.mayPointsTo(ap, dstR, dstF, cipa)) {
        			if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
        			newMS.remove(ap);
        		}
        	}

        	if (Putfield.getSrc(q) instanceof RegisterOperand) {
        		Register srcR = ((RegisterOperand) Putfield.getSrc(q)).getRegister();
        		for (int i = -1; (i = Helper.getPrefixIndexInAP(oldMS, srcR, i)) >= 0;) {
        			AccessPath oldAP = oldMS.get(i);
        			if (oldAP.fields.size() == maxDepth){
        				continue; 
        			}
        			List<jq_Field> fields = new ArrayList<jq_Field>();
        			fields.add(dstF);
        			fields.addAll(oldAP.fields);
        			if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
        			newMS.add(new RegisterAccessPath(dstR, fields));
        		}
        	}
        	if (newMS != null)
        		ostate = new AbstractState(newMS);
        }
        
        @Override
        public void visitGetfield(Quad q) {
            if (istate == null) return;
            Register dstR = Getfield.getDest(q).getRegister();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            if (Getfield.getBase(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Getfield.getBase(q)).getRegister();
                jq_Field srcF = Getfield.getField(q).getField();
                // when stmt is x=y.f, we add x.* if y.f.* is in the must set
                for (int i = -1; (i = Helper.getPrefixIndexInAP(oldMS, srcR, srcF, i)) >= 0;) {
                    List<jq_Field> fields = new ArrayList<jq_Field>(oldMS.get(i).fields);
                    fields.remove(0);
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(newMS);
        }

        @Override
        public void visitReturn(Quad q) {
            if (istate == null) return;        // edge is FULL:<null, null>
         // edge is FULL:<null, AS> or FULL:<AS', AS>
            if (q.getOperator() instanceof THROW_A)
                return;
            if (Return.getSrc(q) instanceof RegisterOperand) {
                Register tgtR = ((RegisterOperand) (Return.getSrc(q))).getRegister();
                if (Helper.getIndexInAP(istate.ms, tgtR) >= 0) {
                    ostate = new AbstractState(istate.ms, true);
                }
            } else if (Return.getSrc(q) instanceof AConstOperand) {
            	 jq_Reference tgtType = ((AConstOperand) (Return.getSrc(q))).getType();
            	 if (tgtType == jq_NullType.NULL_TYPE)
            		 ostate = new AbstractState(istate.ms, false);
            }
        }
    }
}
