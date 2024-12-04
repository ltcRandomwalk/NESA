package chord.analyses.composba;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.RegisterFactory.Register;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import chord.program.Loc;
import chord.program.Program;
import chord.analyses.alias.ICICG;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.rhs.MergeKind;
import chord.project.analyses.rhs.OrderKind;
import chord.project.analyses.rhs.TimeoutException;
import chord.project.analyses.rhs.TraceOverflowException;
import chord.util.Alarm;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.project.Messages;

/**
 * Implementation of the Reps-Horwitz-Sagiv algorithm for context-sensitive dataflow analysis.
 * Modified for the infinity-CFA analysis. This implementation is different from the original
 * RHS in primarily how callgraph used by RHS is constructed.
 * 
 * Relevant system properties:
 * - chord.rhs.merge = [lossy|pjoin|naive] (default = lossy)
 * - chord.rhs.order = [bfs|dfs] (default = bfs) 
 * - chord.rhs.trace = [none|any|shortest] (default = none)
 * - chord.rhs.timeout = N milliseconds (default N = 0, no timeouts)
 *
 */
public abstract class InfiCFARHSAnalysis<PE extends IEdge, SE extends IEdge> extends JavaAnalysis {
    protected static boolean DEBUG = false;
    protected static final String CHORD_RHS_MERGE_PROPERTY = "chord.rhs.merge";
    protected static final String CHORD_RHS_ORDER_PROPERTY = "chord.rhs.order";
    protected static final String CHORD_RHS_TRACE_PROPERTY = "chord.rhs.trace";
    protected static final String CHORD_RHS_TIMEOUT_PROPERTY = "chord.rhs.timeout";

    protected List<Pair<Loc, PE>> workList = new ArrayList<Pair<Loc, PE>>();
    public Map<Inst, ArraySet<PE>> pathEdges = new HashMap<Inst, ArraySet<PE>>();
    public Map<jq_Method, ArraySet<SE>> summEdges = new HashMap<jq_Method, ArraySet<SE>>();
	public Map<jq_Method, ArraySet<SE>> savedSummEdges = new HashMap<jq_Method, ArraySet<SE>>();
	public Map<jq_Method, ArraySet<SE>> loadedSummEdges = new HashMap<jq_Method, ArraySet<SE>>();
    
    protected DomI domI;
    protected DomM domM;
    protected ICICG cicg;
    protected TObjectIntHashMap<Inst> quadToRPOid;
    protected Map<Quad, Loc> invkQuadToLoc;
    protected Map<jq_Method, ArraySet<Quad>> callersMap = new HashMap<jq_Method, ArraySet<Quad>>();
    protected Map<Quad, ArraySet<jq_Method>> targetsMap = new HashMap<Quad, ArraySet<jq_Method>>();
    protected Map<Pair<Quad, PE>, ArraySet<jq_Method>> targetsMapWithReflection = new HashMap<Pair<Quad, PE>, ArraySet<jq_Method>>();
    protected Map<Pair<jq_Method, SE>, HeapCondition> heapCondition = new HashMap<Pair<jq_Method, SE>, HeapCondition>();
    protected Map<Pair<jq_Method, SE>, Integer> hpCondValid = new HashMap<Pair<jq_Method, SE>, Integer>();
    protected int validationFailedCnt = 0;

    protected Map<Pair<Inst, PE>, WrappedPE<PE, SE>> wpeMap = new HashMap<Pair<Inst, PE>, WrappedPE<PE, SE>>();
    protected Map<Pair<jq_Method, SE>, WrappedSE<PE, SE>> wseMap = new HashMap<Pair<jq_Method, SE>, WrappedSE<PE, SE>>();
    

    protected boolean isInit, isDone;

    protected MergeKind mergeKind;
    protected OrderKind orderKind;
    protected TraceKind traceKind;

    private int timeout;
    private Alarm alarm;

    protected boolean mustMerge;
    protected boolean mayMerge;
    
    private SummaryHandler summH;
    private PrintWriter propOut;
    
    /*********************************************************************************
     * Methods that clients must define.
     *********************************************************************************/

    // get the initial set of path edges
    public abstract Set<Pair<Loc, PE>> getInitPathEdges();

    // get the path edge(s) in callee for target method m called from call site q
    // with caller path edge pe
    public abstract PE getInitPathEdge(Quad q, jq_Method m, PE pe);

    // get the path edge in caller when call site q with caller path edge pe
    // is found to have no targets and the corresponding call is skipped
    public abstract PE getSkipMethodEdge(Quad q, PE pe);
    
    // get outgoing path edge(s) from q, given incoming path edge pe into q.
    // q is guaranteed to not be an invoke statement, return statement, entry
    // basic block, or exit basic block.
    // the set returned can be reused by client.
    public abstract PE getMiscPathEdge(Quad q, PE pe);
 
    // q is an invoke statement and m is the target method.
    // get path edge to successor of q given path edge into q and summary edge of a
    // target method m of the call site q.
    // returns null if the path edge into q is not compatible with the summary edge.
    public abstract PE getInvkPathEdge(Quad q, PE clrPE, jq_Method m, SE tgtSE);

    public abstract PE getPECopy(PE pe);

    public abstract SE getSECopy(SE pe);

    // m is a method and pe is a path edge from entry to exit of m
    // (in case of forward analysis) or vice versa (in case of backward analysis)
    // that must be lifted to a summary edge.
    public abstract SE getSummaryEdge(jq_Method m, PE pe);

    /**
     * Provides the call graph to be used by the analysis.
     *
     * @return  The call graph to be used by the analysis.
     */
    public abstract ICICG getCallGraph();
    
    public abstract SummaryHandler getSummaryHandler();
    public abstract void resetPathSummEdgeRefsInSummHandler();
    public abstract void computeHeapAccessForPE(Inst i, PE currPE, HeapAccessData hp);
    public abstract SE getIdSEWithPE(PE pe);
    public abstract SE getIdSEWithSE(SE se);
    
    public abstract PE getInvkPathEdgeLoad(Quad q, PE clrPE, jq_Method m, SE tgtSE);
//  public abstract boolean isLoadedSummValid(PE pe, SE se);
  

    /*********************************************************************************
     * Methods that clients might want to override but is not mandatory; alternatively,
     * their default behavior can be changed by setting relevant chord.rhs.* property.
     *********************************************************************************/

    /**
     * Determines how this analysis must merge PEs at each program point that
     * have the same source state but different target states and, likewise,
     * SEs of each method that have the same source state but different target
     * states.
     */
    public void setMergeKind() {
        String s = System.getProperty(CHORD_RHS_MERGE_PROPERTY, "lossy");
        if (s.equals("lossy"))
            mergeKind = MergeKind.LOSSY;
        else if (s.equals("pjoin"))
            mergeKind = MergeKind.PJOIN;
        else if (s.equals("naive"))
            mergeKind = MergeKind.NAIVE;
        else
            throw new RuntimeException("Bad value for property " + CHORD_RHS_MERGE_PROPERTY + ": " + s);
    }

    public void setOrderKind() {
        String s = System.getProperty(CHORD_RHS_ORDER_PROPERTY, "bfs");
        if (s.equals("bfs"))
            orderKind = OrderKind.BFS;
        else if (s.equals("dfs"))
            orderKind = OrderKind.DFS;
        else
            throw new RuntimeException("Bad value for property " + CHORD_RHS_ORDER_PROPERTY + ": " + s);
    }

    public void setTraceKind() {
        String s = System.getProperty(CHORD_RHS_TRACE_PROPERTY, "none");
        if (s.equals("none"))
            traceKind = TraceKind.NONE;
        else if (s.equals("any"))
            traceKind = TraceKind.ANY;
        else if (s.equals("shortest"))
            traceKind = TraceKind.SHORTEST;
        else if (s.equals("all"))
        	traceKind = TraceKind.ALL;
        else
            throw new RuntimeException("Bad value for property " + CHORD_RHS_TRACE_PROPERTY + ": " + s);
    }

    public void setTimeout() {
        timeout = Integer.getInteger("chord.rhs.timeout", 0);
    }

    /*********************************************************************************
     * Methods that client may call/override.  Example usage:
     * init();
     * while (*) {
     *   runPass();
     *   // done building path/summary edges; clients can now call:
     *   // getPEs(i), getSEs(m), getAllPEs(), getAllSEs(),
     *   // getBackTracIterator(pe), print()
     * }
     * done();
     *********************************************************************************/

    public void init() {
        if (isInit) return;
        isInit = true;

        // start configuring the analysis
        setMergeKind();
        setOrderKind();
        setTraceKind();
        mayMerge  = (mergeKind != MergeKind.NAIVE);
        mustMerge = (mergeKind == MergeKind.LOSSY);
        if (mustMerge && !mayMerge) {
            Messages.fatal("Cannot create RHS analysis '" + getName() + "' with mustMerge but without mayMerge.");
        }
        if (mustMerge && traceKind != TraceKind.NONE) {
            Messages.fatal("Cannot create RHS analysis '" + getName() + "' with mustMerge and trace generation.");
        }
        setTimeout();
        // done configuring the analysis

        if (timeout > 0) {
            alarm = new Alarm(timeout);
            alarm.initAllPasses();
        }
        domI = (DomI) ClassicProject.g().getTrgt("I");
        ClassicProject.g().runTask(domI);
        domM = (DomM) ClassicProject.g().getTrgt("M");
        ClassicProject.g().runTask(domM);
        cicg = getCallGraph();
        quadToRPOid = new TObjectIntHashMap<Inst>();
        invkQuadToLoc = new HashMap<Quad, Loc>();
        for (jq_Method m : cicg.getNodesOrdered()) {
            if (m.isAbstract()) continue;
            ControlFlowGraph cfg = m.getCFG();
            quadToRPOid.put(cfg.entry(), 0);
            int rpoId = 1;
            for (BasicBlock bb : cfg.reversePostOrder()) {
                for (int i = 0; i < bb.size(); i++) {
                    Quad q = bb.getQuad(i);
                    quadToRPOid.put(q, rpoId++);
                    if (q.getOperator() instanceof Invoke) {
                        Loc loc = new Loc(q, i);
                        invkQuadToLoc.put(q, loc);
                    }
                }
            }
            quadToRPOid.put(cfg.exit(), rpoId);
        }
    }

    public void done() {
        if (isDone) return;
        isDone = true;
        if (timeout > 0)
            alarm.doneAllPasses();
    }

    /**
     * Run an instance of the analysis afresh.
     * Clients may call this method multiple times from their {@link #run()} method.
     */
    public void runPass() throws TimeoutException {
    	summH = getSummaryHandler();
        if (timeout > 0)
            alarm.initNewPass();
        // clear these sets since client may call this method multiple times
        workList.clear();
        summEdges.clear();
        pathEdges.clear();
        wpeMap.clear();
        wseMap.clear();
        hpCondValid.clear();
        loadedSummEdges.clear();
        
        if (summH.consumeSummaries) 
        	traceKind = TraceKind.NONE;
        if (debug) {
	        if (summH.genSummaries)
	        	propOut = OutDirUtils.newPrintWriter("prop_data_sumgen");
	        else
	        	propOut = OutDirUtils.newPrintWriter("prop_data_sumuse");
        }
        long rhsStart = System.nanoTime();
        Set<Pair<Loc, PE>> initPEs = getInitPathEdges();
        for (Pair<Loc, PE> pair : initPEs) {
            Loc loc = pair.val0;
            PE pe = pair.val1;
            addPathEdge(loc, pe, null, null, null, null);
        }
        propagate();
        long rhsEnd = System.nanoTime();
		dumpEdgeCnt();
		if (debug) propOut.close();
		long rhsTime = rhsEnd - rhsStart;
		System.out.println("RHS TIME: " + rhsTime);
		System.out.println("RHS TIME Readable: " + Timer.getTimeStr(rhsTime/1000000));
		if (summH.genSummaries) {
			initializeShadowSEandPEsets();
		    resetPathSummEdgeRefsInSummHandler();
			//postProcessHeapInfoFwd();
			postProcessHeapInfoBkwd();
			Timer dump = new Timer();
			dump.init();
			summH.dumpLibSEsFiltered();
			dump.done();
			long dumpTime = dump.getInclusiveTime();
			System.out.println("SUMMARY DUMPING TIME: " + dumpTime);
			System.out.println("SUMMARY DUMPING TIME: " + Timer.getTimeStr(dumpTime));
		}
		else if (!summH.genSummaries && !summH.consumeSummaries && summH.dumpToFile) {
			summH.dumpAllSEs();
		}
		else if (summH.consumeSummaries) {
			//summH.dumpRecomputeStats();
		}
    }
    
    private void dumpEdgeCnt() {
    	int seCnt = 0;
    	int seCntApp = 0;
    	int seCntLib = 0;
    	int mLib = 0;
    	int mApp = 0;
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		int sz = seSet.size();
    		seCnt += sz;
    		if (summH.mBelongsToLib(m)) {
    			seCntLib += sz;
    			mLib++;
    			
    		} else {
    			seCntApp += sz;
    			mApp++;
    		}
    	}
    	int mTotal = mLib + mApp;

    	System.out.println("CompoSBA: TOTAL NUM SUMMARY EDGES : " + seCnt + " APP: " + seCntApp + " LIB: " + seCntLib);
    	System.out.println("CompoSBA: TOTAL METHODS : " + mTotal + " APP: " + mApp + " LIB: " + mLib);

    	int peCnt = 0;
    	int peCntApp = 0;
    	int peCntLib = 0;
    	int iLib = 0;
    	int iApp = 0;
    	for (Inst i : pathEdges.keySet()) {
    		Set<PE> peSet = pathEdges.get(i);
    		int sz = peSet.size();
    		peCnt += sz;
    		if (summH.iBelongsToLib(i)) {
    			peCntLib += sz;
    			iLib++;
    		} else {
    			peCntApp += sz;
    			iApp++;
    		}
    	}
    	int iTotal = iLib + iApp;

    	System.out.println("CompoSBA: TOTAL NUM PATH EDGES : " + peCnt + " APP: " + peCntApp + " LIB: " + peCntLib);
    	System.out.println("CompoSBA: TOTAL INSTS : " + iTotal + " APP: " + iApp + " LIB: " + iLib);
    	
/*    	for (jq_Method m : loadedSummEdges.keySet()) {
    		System.out.println("Loaded SE of " + m);
    		Set<SE> seSet = loadedSummEdges.get(m);
            if (seSet != null) {
                for (SE se : seSet) {
                    System.out.println("\tSE " + se);
                    System.out.println("\tHpCnd " + heapCondition.get(new Pair<jq_Method, SE>(m,se)));
                }
            }
    	}
*/    	
    }
    
    
    /*****
    // TODO: might have to change the argument type to PE
    public BackTraceIterator<PE,SE> getBackTraceIterator(IWrappedPE<PE, SE> wpe) {
        if (traceKind == TraceKind.NONE) {
            throw new RuntimeException("trace generation not enabled");
        }
        return new BackTraceIterator<PE,SE>(wpe);
    }
    *****/

    public Set<PE> getPEs(Inst i) {
        return pathEdges.get(i);
    }

    public Map<Inst, ArraySet<PE>> getAllPEs() {
        return pathEdges;
    }

    public Set<SE> getSEs(jq_Method m) {
        return summEdges.get(m);
    }

    public Map<jq_Method, ArraySet<SE>> getAllSEs() {
        return summEdges;
    }

    public void print() {
        for (jq_Method m : summEdges.keySet()) {
            System.out.println("SE of " + m);
            Set<SE> seSet = summEdges.get(m);
            if (seSet != null) {
                for (SE se : seSet)
                    System.out.println("\tSE " + se);
            }
        }
        for (Inst i : pathEdges.keySet()) {
            System.out.println("PE of " + i);
            Set<PE> peSet = pathEdges.get(i);
            if (peSet != null) {
                for (PE pe : peSet)
                    System.out.println("\tPE " + pe);
            }
        }
    }
        
    /*********************************************************************************
     * Internal methods.
     *********************************************************************************/

    protected ArraySet<Quad> getCallers(jq_Method m) {
        ArraySet<Quad> callers = callersMap.get(m);
        if (callers == null) {
            callers = cicg.getCallersOrdered(m);
            callersMap.put(m, callers);
        }
        return callers;
    }

    protected ArraySet<jq_Method> getTargets(Quad i, PE pe) {
        ArraySet<jq_Method> targets = targetsMap.get(i);
        if (targets == null) {
            targets = cicg.getTargetsOrdered(i);
            targetsMap.put(i, targets);
        }
        return targets;
    }
    
    protected boolean skipMethod(Quad q, jq_Method m, PE predPe, PE pe){
    	return false;
    }
    
    protected boolean jumpToMethodEnd(Quad q, jq_Method m, PE predPe, PE pe){
    	return false;
    }
     
    /**
     * Propagate analysis results until fixpoint is reached.
     */

    int absStateNdx = 0;

    private void propagate() throws TimeoutException {
    	long iterCnt = 0;
        while (!workList.isEmpty()) {
            if (timeout > 0 && alarm.passTimedOut()) {
                System.out.println("TIMED OUT");
                throw new TimeoutException();
            }
            if (DEBUG) {
                System.out.println("WORKLIST:");
                for (Pair<Loc, PE> pair : workList) {
                    Loc loc = pair.val0;
                    int id = quadToRPOid.get(loc.i);
                    System.out.println("\t" + pair + " " + id);
                }
            }
            iterCnt++;
            int last = workList.size() - 1;
            Pair<Loc, PE> pair = workList.remove(last);
            Loc loc = pair.val0;
            PE pe = pair.val1;
            Inst i = loc.i;

            SE idse = getIdSEWithPE(pe);
            if (!absStateId.containsKey(idse)) {
                    absStateId.put(idse, absStateNdx++);
            }

            if (DEBUG) System.out.println("Processing loc: " + loc + " PE: " + pe);
            if (i instanceof BasicBlock) {
                // i is either method entry basic block, method exit basic block, or an empty basic block
                BasicBlock bb = (BasicBlock) i;
                if (bb.isEntry()) {
                    processEntry(bb, pe);
                } else if (bb.isExit()) {
                    processExit(bb, pe);
                } else {
					final PE pe2 = mayMerge ? getPECopy(pe) : pe;
                    propagatePEtoPE(loc, pe2, pe, null, null);
				}
            } else {
                Quad q = (Quad) i;
                // invoke or misc quad
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    processInvk(loc, pe);
                } else {
                    PE pe2 = getMiscPathEdge(q, pe);
                    propagatePEtoPE(loc, pe2, pe, null, null);
                }
            }
        }
        System.out.println("CompoSBA: Worklist elements processed: " + iterCnt);
    }
    
    protected void processInvk(final Loc loc, final PE pe) {
        final Quad q = (Quad) loc.i;
        final Set<jq_Method> targets = getTargets(q, pe);
        if (targets.isEmpty()) {
        	//New edge might be generated since analysis could
        	//account for the return register while skipping
        	final PE pe2 = getSkipMethodEdge(q, pe);
            //final PE pe2 = mayMerge ? getPECopy(pe) : pe;
            propagatePEtoPE(loc, pe2, pe, null, null);
        } else {
            for (jq_Method m2 : targets) {
                if (DEBUG) System.out.println("\tTarget: " + m2);
                
                // First handle the case where summaries may be 
                // present in the set of loaded summaries.
                if (summH.consumeSummaries) {
                	boolean matchLoad = false;
                	final Set<SE> seLoadSet = savedSummEdges.get(m2);
                	if (seLoadSet != null) {
                		for (SE se : seLoadSet) {
                			if (propagateSEtoPELoad(pe, loc, m2, se)) {
                				matchLoad = true;
                				break;
                			}
                		}
                	}
                	if (matchLoad) continue;
                }

                final PE pe2 = getInitPathEdge(q, m2, pe);
                
                if (skipMethod(q, m2, pe, pe2)) {
                	final PE pe3 = mayMerge ? getPECopy(pe) : pe;
                    propagatePEtoPE(loc, pe3, pe, null, null);
                } else if(jumpToMethodEnd(q, m2, pe, pe2)){
                	BasicBlock bb2 = m2.getCFG().entry();
                    Loc loc2 = new Loc(bb2, -1);
                    int initWorkListSize = workList.size();
                    addPathEdge(loc2, pe2, q, pe, null, null);
                    int finalWorkListSize = workList.size();
                    if(initWorkListSize != finalWorkListSize){
                    	//This operation is safe since any entry added to the worklist will necessarily be at the last
                    	//position irrespective of BFS or DFS updates
                    	Pair<Loc,PE> entryP = workList.remove(finalWorkListSize - 1); 
                    	BasicBlock bb3 = m2.getCFG().exit();
                        Loc loc3 = new Loc(bb3, -1);
                    	PE pe3 = mayMerge ? getPECopy(entryP.val1) : entryP.val1;
                        addPathEdge(loc3, pe3, bb2, entryP.val1, null, null);
                    }                    
                } else {
                    BasicBlock bb2 = m2.getCFG().entry();
                    Loc loc2 = new Loc(bb2, -1);
                    
                    if (debug) {
                    	SE idse1 = getIdSEWithPE(pe);
                    	int peState = absStateId.get(idse1);
                    	SE idse2 = getIdSEWithPE(pe2);
                    	int pe2State = absStateId.get(idse2);
                    	propOut.println("propPEToPE: " + q.toString() + "::" + q.getMethod().toString() + "::" + peState 
                    			          + " TO " + bb2.toString() + "::" + m2.toString() + "::" + pe2State);
                    }
                    
                    addPathEdge(loc2, pe2, q, pe, null, null);
                }
                final Set<SE> seSet = summEdges.get(m2);
                if (seSet == null) {
                    if (DEBUG) System.out.println("\tSE set empty");
                    continue;
                }
                for (SE se : seSet) {
                    if (DEBUG) System.out.println("\tTesting SE: " + se);
                    if (propagateSEtoPE(pe, loc, m2, se)) {
                        if (DEBUG) System.out.println("\tMatched");
                        if (mustMerge) {
                            // this was only SE; stop looking for more
                            break;
                        }
                    } else {
                        if (DEBUG) System.out.println("\tDid not match");
                    }
                }
            }
        }
    }

    private void processEntry(BasicBlock bb, PE pe) {
        for (BasicBlock bb2 : bb.getSuccessors()) {
            Inst i2; int q2Idx;
            if (bb2.size() == 0) {
                i2 = (BasicBlock) bb2;
                q2Idx = -1;
            } else {
                i2 = bb2.getQuad(0);
                q2Idx = 0;
            }
            Loc loc2 = new Loc(i2, q2Idx);
            PE pe2 = mayMerge ? getPECopy(pe) : pe;
            
            if (debug) {
            	SE idse = getIdSEWithPE(pe);
            	int peState = absStateId.get(idse);
            	propOut.println("propPEToPE: " + bb.toString() + "::" + bb.getMethod().toString() + "::" + peState 
            			          + " TO " + i2.toString() + "::" + i2.getMethod().toString() + "::" + peState);
            }
            
            addPathEdge(loc2, pe2, bb, pe, null, null);
        }
    }

    protected void processExit(BasicBlock bb, PE pe) {
        jq_Method m = bb.getMethod();
        SE se = getSummaryEdge(m, pe);
        ArraySet<SE> seSet = summEdges.get(m);
        if (DEBUG) System.out.println("\tChecking if " + m + " has SE: " + se);
        SE seToAdd = se;
        if (seSet == null) {
            seSet = new ArraySet<SE>();
            summEdges.put(m, seSet);
            seSet.add(se);
            if (DEBUG) System.out.println("\tNo, adding it as first SE");
        } else if (mayMerge) {
            boolean matched = false;
            for (SE se2 : seSet) {
                int result = se2.canMerge(se, mustMerge);
                if (result >= 0) {
                    if (DEBUG) System.out.println("\tNo, but matches SE: " + se2);
                    boolean changed = se2.mergeWith(se);
                    if (DEBUG) System.out.println("\tNew SE after merge: " + se2);
                    if (!changed) {
                        if (DEBUG) System.out.println("\tExisting SE did not change");
                        if (traceKind != TraceKind.NONE && result == 0)
                            updateWSE(m, se2, bb, pe);
                        return;
                    }
                    if (DEBUG) System.out.println("\tExisting SE changed");
                    // se2 is already in summEdges(m), so no need to add it
                    seToAdd = se2;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (DEBUG) System.out.println("\tNo, adding");
                seSet.add(se);
            }
        } else if (!seSet.add(se)) {
            if (DEBUG) System.out.println("\tYes, not adding");
            if (traceKind != TraceKind.NONE)
                updateWSE(m, se, bb, pe);
            return;
        }
        if (traceKind != TraceKind.NONE) {
            recordWSE(m, seToAdd, bb, pe);
        }
        for (Quad q2 : getCallers(m)) {
            if (DEBUG) System.out.println("\tCaller: " + q2 + " in " + q2.getMethod());
            Set<PE> peSet = pathEdges.get(q2);
            if (peSet == null)
                continue;
            // make a copy as propagateSEtoPE might add a path edge to this set itself;
            // in this case we could get a ConcurrentModification exception if we don't
            // make a copy.
            List<PE> peList = new ArrayList<PE>(peSet);
            Loc loc2 = invkQuadToLoc.get(q2);
            for (PE pe2 : peList) {
                if (DEBUG) System.out.println("\tTesting PE: " + pe2);
                boolean match = propagateSEtoPE(pe2, loc2, m, seToAdd);
                if (match) {
                    if (DEBUG) System.out.println("\tMatched");
                } else {
                    if (DEBUG) System.out.println("\tDid not match");
                }
            }
        }
    }

    
/*    protected void processShortCkt(jq_Method m, SE se) {
        ArraySet<SE> seSet = summEdges.get(m);
        if (DEBUG) System.out.println("\tChecking if " + m + " has SE: " + se);
        SE seToAdd = se;
        if (seSet == null) {
            seSet = new ArraySet<SE>();
            summEdges.put(m, seSet);
            seSet.add(se);
            if (DEBUG) System.out.println("\tNo, adding it as first SE");
        } else if (mayMerge) {
            boolean matched = false;
            for (SE se2 : seSet) {
                int result = se2.canMerge(se, mustMerge);
                if (result >= 0) {
                    if (DEBUG) System.out.println("\tNo, but matches SE: " + se2);
                    boolean changed = se2.mergeWith(se);
                    if (DEBUG) System.out.println("\tNew SE after merge: " + se2);
                    if (!changed) {
                        if (DEBUG) System.out.println("\tExisting SE did not change");
                        return;
                    }
                    if (DEBUG) System.out.println("\tExisting SE changed");
                    // se2 is already in summEdges(m), so no need to add it
                    seToAdd = se2;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (DEBUG) System.out.println("\tNo, adding");
                seSet.add(se);
            }
        } else if (!seSet.add(se)) {
            if (DEBUG) System.out.println("\tYes, not adding");
            return;
        }
        for (Quad q2 : getCallers(m)) {
            if (DEBUG) System.out.println("\tCaller: " + q2 + " in " + q2.getMethod());
            Set<PE> peSet = pathEdges.get(q2);
            if (peSet == null)
                continue;
            // make a copy as propagateSEtoPE might add a path edge to this set itself;
            // in this case we could get a ConcurrentModification exception if we don't
            // make a copy.
            List<PE> peList = new ArrayList<PE>(peSet);
            Loc loc2 = invkQuadToLoc.get(q2);
            for (PE pe2 : peList) {
                if (DEBUG) System.out.println("\tTesting PE: " + pe2);
                boolean match = propagateSEtoPE(pe2, loc2, m, seToAdd);
                if (match) {
                    if (DEBUG) System.out.println("\tMatched");
                } else {
                    if (DEBUG) System.out.println("\tDid not match");
                }
            }
        }
    }
    
    
    protected boolean checkPEhasMatchingSE(Loc loc, PE pe) {
    	//if (pe.isAppAccessed()) return false;
    	Inst i = loc.i;
    	if (i instanceof BasicBlock) {
    		BasicBlock bb = (BasicBlock)i;
    		if (bb.isEntry()) {
    			 jq_Method m = bb.getMethod();
    			 //if (appCallbkPres.contains(m)) return false;
    			 Set<SE> seSet = savedSummEdges.get(m);
    	         if (seSet == null) return false;
    	         for (SE se : seSet) {
    	        	 if (isLoadedSummValid(pe, se)) {
    	        		 Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
    	        		 boolean valid = false;
    	        		 if (hpCondValid.contains(pr)) {
    	        			 int done = hpCondValid.get(pr);
    	        			 if (done < 0) continue; // we have checked this heap condition before and found it to be invalid.
    	        			 if (done > 0) valid = true;
    	        		 } else {
	        				 HeapCondition hpCond = heapCondition.get(pr);
	    	        		 if (hpCond.validate()) {
	    	        			 valid = true;
	    	        			 hpCondValid.put(pr, 1);
	    	        		 } else {
	    	        			 valid = false;
	    	        			 hpCondValid.put(pr, -1);
	    	        			 if (debug) System.out.println("Validation failed for method: " + m.toString() + "\n" + se.toString() + "\n");
	    	        			 validationFailedCnt++;
	    	        		 }
	        			 }
	        			 
    	        		 if (valid) {
	    	        		 processShortCkt(m, se);
			        		 return true;
    	        		 }
    	        	 }
    	        	
//    	        	 int max = 0;
//    	        	 SE seMax = null;
//    	        	 int retval = pe.isMatching(se);
//    	        	 if (retval >= 0) {
//    	        		 if (retval >= max) {
//    	        			 max = retval;
//    	        			 seMax = se;
//    	        		 }
//    	        	 }
//    	        	 if (seMax != null) {
//	    	        	 processShortCkt(m, seMax);
//		        		 return true;
//    	        	 }
    	        	 
    	         }
    	         return false;
    		}
    	}
    	return false;
    }
*/
    
    
    // Add 'pe' as an incoming PE into loc.
    // 'predPE' and 'predSE' are treated as the provenance of 'pe', where 'predPE' is incoming PE into 'pred'.
    // 'predPE' is null iff 'predI' is null.
    // 'predSE' is null iff 'predM' is null.
    // 'loc' may be anything: entry basic block, exit basic block, invk quad, or misc quad.
    protected void addPathEdge(Loc loc, PE pe, Inst predI, PE predPE, jq_Method predM, SE predSE) {
    	Inst i = loc.i;
        if (DEBUG) System.out.println("\tChecking if " + loc + " has PE: " + pe);
        ArraySet<PE> peSet = pathEdges.get(i);
        PE peToAdd = pe;
   //   if (summH.consumeSummaries && checkPEhasMatchingSE(loc, pe)) return;
        boolean matched = false;
        PE prevPE = null;
        if (peSet == null) {
            peSet = new ArraySet<PE>();
            pathEdges.put(i, peSet);
            peSet.add(pe);
            if (DEBUG) System.out.println("\tNo, adding it as first PE");
        } else if (mayMerge) {
            for (PE pe2 : peSet) {
                int result = pe2.canMerge(pe, mustMerge);
                if (result >= 0) {
                    if (DEBUG) System.out.println("\tNo, but matches PE: " + pe2);
                    prevPE = getPECopy(pe2);
                    boolean changed = pe2.mergeWith(pe);
                    if (DEBUG) System.out.println("\tNew PE after merge: " + pe2); 
                    if (!changed) {
                        if (DEBUG) System.out.println("\tExisting PE did not change");
                        if (traceKind != TraceKind.NONE && result == 0)
                            updateWPE(i, pe2, predI, predPE, predM, predSE);
                        return;
                    }
                    if (DEBUG) System.out.println("\tExisting PE changed");
                    // pe2 is already in pathEdges(i) so no need to add it; but it may or may not be in workList
                    for (int j = workList.size() - 1; j >= 0; j--) {
                        Pair<Loc, PE> pair = workList.get(j);
                        PE pe3 = pair.val1;
                        if (pe3 == pe2) {
                        	if (DEBUG) System.out.println("\tFound merged PE in worklist");
                            assert (loc.equals(pair.val0));
                            if (traceKind != TraceKind.NONE) {
                            	if (DEBUG) System.out.println("\tcalling record and update prov");
                                recordAndUpdateWPE(i, pe2, predI, predPE, predM, predSE, prevPE);
                            }
                            return;
                        }
                    }
                    if (DEBUG) System.out.println("\tDid not find merged PE in worklist");
                    peToAdd = pe2;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (DEBUG) System.out.println("\tNo, adding");
                peSet.add(pe);
            }
        } else if (!peSet.add(pe)) {
            if (DEBUG) System.out.println("\tYes, not adding");
            if (traceKind != TraceKind.NONE)
                updateWPE(i, pe, predI, predPE, predM, predSE);
            return;
        }
        assert (peToAdd != null);
        if (traceKind != TraceKind.NONE) {
        	if (matched) {
        		if (DEBUG) System.out.println("\tcalling record and update prov - second loc");
        		recordAndUpdateWPE(i, peToAdd, predI, predPE, predM, predSE, prevPE);
        	} else {
        		if (DEBUG) System.out.println("\tcalling record wpe ");
        		recordWPE(i, peToAdd, predI, predPE, predM, predSE);
        	}
        }
        Pair<Loc, PE> pair = new Pair<Loc, PE>(loc, peToAdd);
        int j = workList.size() - 1;
        if (orderKind == OrderKind.BFS) {
            jq_Method m = loc.i.getMethod();
            int rpoId = quadToRPOid.get(i);
            for (; j >= 0; j--) {
                Loc loc2 = workList.get(j).val0;
                Inst i2 = loc2.i;
                if (i2.getMethod() != m) break;
                int rpoId2 = quadToRPOid.get(i2);
                if (rpoId2 > rpoId)
                    break;
            }
        }
        if (DEBUG) System.out.println("\tAlso adding to worklist at " + (j + 1));
        workList.add(j + 1, pair);
    }

    // Adds 'pe' as an incoming PE into each immediate successor of 'loc'.
    // 'predPE' and 'predSE' are treated as the provenance of 'pe', where 'predPE' is incoming PE into 'loc'.
    // 'predPE' is guaranteed to be non-null but 'predSE' may be null.
    protected void propagatePEtoPE(Loc loc, PE pe, PE predPE, jq_Method predM, SE predSE) {
        int qIdx = loc.qIdx;
        Inst i = loc.i;
        BasicBlock bb = i.getBasicBlock();
        if (qIdx != bb.size() - 1) {
            int q2Idx = qIdx + 1;
            Quad q2 = bb.getQuad(q2Idx);
            Loc loc2 = new Loc(q2, q2Idx);
            
            if (debug) {
            	SE idse = getIdSEWithPE(pe);
            	int peState = absStateId.get(idse);
            	propOut.println("propPEToPE: " + i.toString() + "::" + i.getMethod().toString() + "::" + peState 
            			          + " TO " + q2.toString() + "::" + q2.getMethod().toString() + "::" + peState);
            }
            
            addPathEdge(loc2, pe, i, predPE, predM, predSE);
            return;
        }
        boolean isFirst = true;
        for (BasicBlock bb2 : bb.getSuccessors()) {
            Inst i2; int q2Idx;
            if (bb2.size() == 0) { 
                i2 = (BasicBlock) bb2;
                q2Idx = -1;
            } else {
                i2 = bb2.getQuad(0);
                q2Idx = 0;
            }
            Loc loc2 = new Loc(i2, q2Idx);
            PE pe2;
            if (!mayMerge)
                pe2 = pe;
            else {
                if (isFirst) {
                    pe2 = pe;
                    isFirst = false;
                } else
                    pe2 = getPECopy(pe);
            }
            
            if (debug) {
            	SE idse = getIdSEWithPE(pe);
            	int peState = absStateId.get(idse);
            	propOut.println("propPEToPE: " + i.toString() + "::" + i.getMethod().toString() + "::" + peState 
            			        + " TO " + i2.toString() + "::" + i2.getMethod().toString() + "::" + peState);
            }
            
            addPathEdge(loc2, pe2, i, predPE, predM, predSE);
        }
    }

    protected boolean propagateSEtoPE(PE clrPE, Loc loc, jq_Method tgtM, SE tgtSE) {
        Quad q = (Quad) loc.i;
        PE pe2 = getInvkPathEdge(q, clrPE, tgtM, tgtSE);
        if (pe2 == null)
            return false;
        if (debug) {
        	SE idse1 = getIdSEWithPE(pe2);
        	int pe2State = absStateId.get(idse1);
        	SE idse2 = getIdSEWithSE(tgtSE);
        	int tgtSeState = absStateId.get(idse2);
        	propOut.println("propSEToPE: " + tgtM.toString() + "::" + tgtSeState 
        			        + " TO " + q.toString() + "::" + q.getMethod().toString() + "::" + pe2State);
        }
        propagatePEtoPE(loc, pe2, clrPE, tgtM, tgtSE);
        return true;
    }
    
    protected boolean propagateSEtoPELoad(PE clrPE, Loc loc, jq_Method tgtM, SE tgtSE) {
        Quad q = (Quad) loc.i;
        PE pe2 = getInvkPathEdgeLoad(q, clrPE, tgtM, tgtSE);
        if (pe2 == null)
            return false;
        propagatePEtoPE(loc, pe2, clrPE, tgtM, tgtSE);
        return true;
    }

    /*********************************************************************************
     * Provenance record/update methods.
     *********************************************************************************/

    
    /**
     * Initializes provenance of given PE.
     * Pre-condition: traceKind != NONE.
     * (i, peToAdd): the PE whose provenance will be initialized.
     * peToRemove: This is the PE which existed before a merge which produced peToAdd, happened. If traceKind is ALL (which means
     * that all provenances of a PE have to be tracked), then, the provenance of peToRemove must be unioned into the provenance of peToAdd.
     * After this, the WPE corresponding to peToRemove can be deleted.
     * (predI, predPE, predM, predSE): the provenance of the PE.
     */
    private void recordAndUpdateWPE(Inst i, PE peToAdd, Inst predI, PE predPE, jq_Method predM, SE predSE, PE peToRemove) {
        assert (peToAdd != null);
        // predPE and/or predSE may be null
        assert (!wpeMap.containsKey(new Pair<Inst, PE>(i,peToAdd)));
        PE peCopy = getPECopy(peToAdd);
        WrappedPE<PE, SE> predWPE;
        int len;
        if (predPE == null) {
            assert (predI == null && predM == null && predSE == null);
            predWPE = null;
            len = 0;
        } else {
            assert (predI != null);
            predWPE = wpeMap.get(new Pair<Inst, PE>(predI, predPE));
            assert (predWPE != null);
            if (i instanceof BasicBlock && ((BasicBlock) i).isEntry())
                len = 0;
            else
                len = 1 + predWPE.getLen();
        }
        WrappedSE<PE, SE> predWSE;
        if (predSE == null) {
            assert (predM == null);
            predWSE = null;
        } else {
            assert (predM != null);
            predWSE = wseMap.get(new Pair<jq_Method, SE>(predM, predSE));
            assert (predWSE != null);
            if (traceKind == TraceKind.ALL)
            	len += 1;
            else
            	len += predWSE.getLen();
        }
        if(len < 0){
            System.out.println("Trace length overflowed.");
            throw new TraceOverflowException();
        }
        WrappedPE<PE, SE> wpe;
        
        if (traceKind == TraceKind.ALL) {
        	wpe = new WrappedPE<PE, SE>(i, peCopy, null, null, len);
        	if (predWPE != null) {
        		Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> p = new Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>(predWPE, predWSE);
        		wpe.addToProvSet(p);
        		if (DEBUG) System.out.println("\tin record and update: creating wpe");
        	}
        	/* peToAdd is result of the merge of current pe (available in caller) into peToRemove.
             * A new wpe with peToAdd got created above with the provenance information of current pe.
             * Now, since peToAdd is a merge of current pe and peToRemove, we have to get the provenance info from peToRemove and
             * add it as provenance info in peToAdd.
             */
            
        	if (DEBUG) System.out.println("Current wpe: " + predWPE.getInst().toString() + "::" + predWPE.getInst().getMethod().toString());
        	assert (peToRemove != null);
        	Pair<Inst, PE> p = new Pair<Inst, PE>(i, peToRemove);
            
        	if (wpeMap.containsKey(p)) {
        		if (traceKind == TraceKind.ALL) {
            		WrappedPE<PE, SE> wpeOld = wpeMap.get(p);
            		if (DEBUG) System.out.println("\tin record and update: adding old prov");
            		wpe.addAllToProvSet(wpeOld.getProvSet());
        		}
        	}
        } else {
        	wpe = new WrappedPE<PE, SE>(i, peCopy, predWPE, predWSE, len);
        }
    	wpeMap.put(new Pair<Inst, PE>(i, peCopy), wpe);
    }
    
    /**
     * Potentially updates provenance of given SE.
     * Pre-condition: traceKind != NONE.
     * (m, seToAdd): the SE whose provenance will be potentially updated.
     * (bb, predPE): the new provenance of the SE.
     * Replace the old provenance with the new provenance if traceKind is SHORTEST
     * and new provenance has shorter length than old provenance.
     */
    protected void updateWSE(jq_Method m, SE seToAdd, BasicBlock bb, PE predPE) {
        assert (seToAdd != null && predPE != null);
        Pair<jq_Method, SE> p = new Pair<jq_Method, SE>(m, seToAdd);
        WrappedSE<PE, SE> wse = wseMap.get(p);
        assert (wse != null);
        if (traceKind == TraceKind.SHORTEST) {
            int oldLen = wse.getLen();
            Pair<Inst, PE> predP = new Pair<Inst, PE>(bb, predPE);
            WrappedPE<PE, SE> newWPE = wpeMap.get(predP);
            assert (newWPE != null);
            int newLen = 1 + newWPE.getLen();
            if (newLen < oldLen)
                wse.update(newWPE, newLen);
        } else if (traceKind == TraceKind.ALL) {
        	Pair<Inst, PE> predP = new Pair<Inst, PE>(bb, predPE);
            WrappedPE<PE, SE> newWPE = wpeMap.get(predP);
            assert (newWPE != null);
            wse.update(newWPE, 1);
        }
    }

    /**
     * Potentially updates provenance of given PE.
     * Pre-condition: traceKind != NONE.
     * (i, peToAdd): the PE whose provenance will be potentially updated.
     * (predI, predPE, predM, predSE): the new provenance of the PE.
     * Replace the old provenance with the new provenance if traceKind is SHORTEST
     * and new provenance has shorter length than old provenance.
     */
    private void updateWPE(Inst i, PE peToAdd, Inst predI, PE predPE, jq_Method predM, SE predSE) {
        assert (peToAdd != null);
        // predPE and/or predSE may be null
        Pair<Inst, PE> p = new Pair<Inst, PE>(i, peToAdd);
        WrappedPE<PE, SE> wpe = wpeMap.get(p);
        assert (wpe != null);
        
        int oldLen = wpe.getLen();
        if (predPE == null) {
            assert (oldLen == 0);
            // cannot reduce below 0, so return
            return;
        }
        int newLen;
        Pair<Inst, PE> predP = new Pair<Inst, PE>(predI, predPE);
        WrappedPE<PE, SE> newWPE = wpeMap.get(predP);
        assert (newWPE != null);
        if (i instanceof BasicBlock && ((BasicBlock) i).isEntry())
            newLen = 0;
        else
            newLen = 1 + newWPE.getLen();
        WrappedSE<PE, SE> newWSE;
        if (predSE != null) {
            assert (predM != null);
            Pair<jq_Method, SE> predN = new Pair<jq_Method, SE>(predM, predSE);
            newWSE = wseMap.get(predN);
            assert (newWSE != null);
            if (traceKind == TraceKind.ALL)
            	newLen += 1;
            else
            	newLen += newWSE.getLen();
        } else {
            assert (predM == null);
            newWSE = null;
        }
        if(newLen < 0)
            throw new TraceOverflowException();
        if (traceKind == TraceKind.SHORTEST) {
            if (newLen < oldLen)
                wpe.update(newWPE, newWSE, newLen);
        } else if (traceKind == TraceKind.ALL) {
			Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> pr = new Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>(newWPE, newWSE);
        	wpe.addToProvSet(pr);
        }
    }

    /**
     * Initializes provenance of given SE.
     * Pre-condition: traceKind != NONE.
     * (m, seToAdd): the SE whose provenance will be initialized.
     * (bb, predPE): the provenance of the SE.
     */
    protected void recordWSE(jq_Method m, SE seToAdd, BasicBlock bb, PE predPE) {
        assert (seToAdd != null && predPE != null);
        assert (!wseMap.containsKey(new Pair<jq_Method, SE>(m, seToAdd)));
        WrappedPE<PE, SE> wpe = wpeMap.get(new Pair<Inst, PE>(bb, predPE));
        assert (wpe != null);
        SE seCopy = getSECopy(seToAdd);
        int len = 1 + wpe.getLen();
        WrappedSE<PE, SE> wse = new WrappedSE<PE, SE>(seCopy, m, wpe, len);
        wseMap.put(new Pair<jq_Method, SE>(m, seCopy), wse);
    }

    /**
     * Initializes provenance of given PE.
     * Pre-condition: traceKind != NONE.
     * (i, peToAdd): the PE whose provenance will be initialized.
     * (predI, predPE, predM, predSE): the provenance of the PE.
     */
    private void recordWPE(Inst i, PE peToAdd, Inst predI, PE predPE, jq_Method predM, SE predSE) {
        assert (peToAdd != null);
        // predPE and/or predSE may be null
        assert (!wpeMap.containsKey(new Pair<Inst, PE>(i,peToAdd)));
        PE peCopy = getPECopy(peToAdd);
        WrappedPE<PE, SE> predWPE;
        int len;
        if (predPE == null) {
            assert (predI == null && predM == null && predSE == null);
            predWPE = null;
            len = 0;
        } else {
            assert (predI != null);
            predWPE = wpeMap.get(new Pair<Inst, PE>(predI, predPE));
            assert (predWPE != null);
            if (i instanceof BasicBlock && ((BasicBlock) i).isEntry())
                len = 0;
            else
                len = 1 + predWPE.getLen();
        }
        WrappedSE<PE, SE> predWSE;
        if (predSE == null) {
            assert (predM == null);
            predWSE = null;
        } else {
            assert (predM != null);
            predWSE = wseMap.get(new Pair<jq_Method, SE>(predM, predSE));
            assert (predWSE != null);
            if (traceKind == TraceKind.ALL)
            	len += 1;
            else
            	len += predWSE.getLen();
            
        }
        if(len < 0){
            System.out.println("Trace length overflowed.");
            throw new TraceOverflowException();
        }
        WrappedPE<PE, SE> wpe;
        
        if (traceKind == TraceKind.ALL) {
        	// When all predecessor PEs need to be tracked, add the current wpe,wse pair to the set instead of 
        	// setting the lone instance fields wpe/wse of WrappedPE.
        	wpe = new WrappedPE<PE, SE>(i, peCopy, null, null, len);
        	if (predWPE != null) {
        		Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> p = new Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>(predWPE, predWSE);
        		wpe.addToProvSet(p);
        	}
        } else {
        	wpe = new WrappedPE<PE, SE>(i, peCopy, predWPE, predWSE, len);
        }
        wpeMap.put(new Pair<Inst, PE>(i, peCopy), wpe);
    }


    /*********************************************************************************
     * Methods to do the forward top-down traversal of the provenance graph and compute the
     * SEs which can be reused.
     *********************************************************************************/
    
    protected HashMap<Pair<jq_Method, SE>, HeapAccessData> seToHeapAccessMap = new HashMap<Pair<jq_Method, SE>, HeapAccessData>();
    protected HashMap<Pair<Inst, PE>, HeapAccessData> peToHeapAccessMap = new HashMap<Pair<Inst, PE>, HeapAccessData>();
    
    private Map<WrappedPE<PE,SE>, ArraySet<Pair<Inst, PE>>> pewatchpe = Maps.newHashMap();
	private Map<WrappedSE<PE,SE>, ArraySet<Pair<Inst, PE>>> sewatchpe = Maps.newHashMap();
	private Map<WrappedPE<PE,SE>, Pair<jq_Method, SE>> pewatchse = Maps.newHashMap();
	
	private HashSet<WrappedPE<PE,SE>> currwpe = new HashSet<WrappedPE<PE,SE>>();
	private HashSet<WrappedPE<PE,SE>> nextwpe = new HashSet<WrappedPE<PE,SE>>();
	private HashSet<WrappedSE<PE,SE>> currwse = new HashSet<WrappedSE<PE,SE>>();
	private HashSet<WrappedSE<PE,SE>> nextwse = new HashSet<WrappedSE<PE,SE>>();
	
	private TObjectIntHashMap<SE> absStateId = new TObjectIntHashMap<SE>();
	
    private int peHeapData = 0;
    private int seHeapData = 0;
    protected boolean debug = false;
    
	private boolean isMethodEntry(WrappedPE<PE, SE> wpe) {
		Inst i = wpe.getInst();
		if (i instanceof BasicBlock) {
			BasicBlock bb = (BasicBlock)i;
			if (bb.isEntry()) return true;
		}
		return false;
	}
	
	private int getRPOid (WrappedPE<PE, SE> wpe) {
	    	Inst i = wpe.getInst();
	    	return quadToRPOid.get(i);
	}
	    
	private void dbgPrint(WrappedPE<PE, SE> wpe, int itNum) {
		if (!debug) return;
		Inst i = wpe.getInst();
		PE pe = wpe.getPE();
		SE idse = getIdSEWithPE(pe);
		int absState = absStateId.get(idse);
		
		String methName = "";
		if (!(i instanceof BasicBlock))
			methName = ((Quad)i).getMethod().toString();
		else {
			BasicBlock bb = (BasicBlock)i;
			methName = bb.getMethod().toString();
		}
		if (itNum == 0)
			System.out.println(getRPOid(wpe) + ": " + i.toString() + "::" + methName + "::" + absState);
		else
			System.out.println(getRPOid(wpe) + ": itnum:" + itNum + ": " + i.toString() + "::" + methName + "::" +absState);
	}
	
	private void dbgPrint(WrappedSE<PE, SE> wse, int itNum) {
		if (!debug) return;
		SE se = wse.getSE();
		SE idse = getIdSEWithSE(se);
		int absState = absStateId.get(idse);
		
		if (itNum == 0)
			System.out.println("wse : " + wse.getMethod().toString() + "::" + absState);
		else
			System.out.println("wse : itnum: " + itNum + ": " + wse.getMethod().toString() + "::" + absState);
	}
	
	private boolean isValid(Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> pr) {
		WrappedPE<PE, SE> wpe = (WrappedPE<PE, SE>)pr.val0;
		WrappedSE<PE, SE> wse = (WrappedSE<PE, SE>)pr.val1;
		boolean retVal = false;
		
		Inst i = wpe.getInst();
		PE pe = wpe.getPE();
		
		if (pathEdges.containsKey(i)) {
			Set<PE> peSet = pathEdges.get(i);
			boolean found = false;
			for (PE vpe : peSet) {
				if (vpe.equals(pe)) {
					found = true;
					break;
				}
			}
			if (found) retVal = true;
		}
		
		if (wse != null && retVal == true) {
			retVal = isValid(wse);
		}
		return retVal;
	}
	
	private boolean isValid(WrappedSE<PE, SE> wse) {
		boolean retVal = false;
		jq_Method m = wse.getMethod();
		SE se = wse.getSE();
		if (summEdges.containsKey(m)) {
			Set<SE> seSet = summEdges.get(m);
			boolean found = false;
			for (SE vse : seSet) {
				if (vse.equals(se)) {
					found = true;
					break;
				}
			}
			if (found) retVal = true;
		}
		return retVal;
	}

	private void initializeShadowSEandPEsets() {
		Map<Inst, ArraySet<PE>> pathEdgesCpy = new HashMap<Inst, ArraySet<PE>>();
	    Map<jq_Method, ArraySet<SE>> summEdgesCpy = new HashMap<jq_Method, ArraySet<SE>>();
	    
		int peInPathEdges = 0;
    	for (Inst i : pathEdges.keySet()) {
    		ArraySet<PE> peSet = new ArraySet<PE>();
    		for (PE pe : pathEdges.get(i))
    			peSet.add(pe);
    		peInPathEdges += peSet.size();
    		pathEdgesCpy.put(i, peSet);
    	}
    	
    	int seInSumEdges = 0;
    	int id = 0;
    	for (jq_Method m : summEdges.keySet()) {
    		ArraySet<SE> seSet = new ArraySet<SE>();
    		for (SE se : summEdges.get(m)) {
    			seSet.add(se);
    			if (debug) {
	    			SE idse = getIdSEWithSE(se);
	    			absStateId.put(idse, id++);
    			}
    		}
    		seInSumEdges += seSet.size();
    		summEdgesCpy.put(m, seSet);
    	}
    	pathEdges = pathEdgesCpy;
    	summEdges = summEdgesCpy;
    	System.out.println("CompoSBA: In postProcessHeapInfo - num PEs in pathEdgesCpy " + peInPathEdges);
    	System.out.println("CompoSBA: In postProcessHeapInfo - num SEs in summEdgesCpy " + seInSumEdges);
	}
	
	private void printCFG() {
		jq_Method m;
		
		System.out.println("*************************************************************");
		m = Program.g().getMainMethod();
		ControlFlowGraph cfg = m.getCFG();
		System.out.println(cfg.fullDump());
		System.out.println("*************************************************************");
	}
	
    private void createReverseProvMap() {
    	int peWithNullProv = 0;
    	int peWithProv = 0;
    	int seWithProv = 0;
    	
    	for (Inst i : pathEdges.keySet()) {
    		Set<PE> peSet = pathEdges.get(i);
    		for (PE pe : peSet) {
	    		Pair<Inst, PE> ent = new Pair<Inst, PE>(i, pe);
	    		WrappedPE<PE, SE> wpe = wpeMap.get(ent);
	    		ArraySet<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> validProvSet = new ArraySet<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>>();
	    		Set<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> provSet = wpe.getProvSet();
	    		if (debug) System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++");
	    		if (debug) System.out.print("Provenance of : ");
	    		dbgPrint(wpe, 0);
	    		if (debug) System.out.println("----------------");
	    		if (provSet != null) {
	    			boolean peWithProvFound = false;
		    		for (Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> pr : provSet) {
		    			WrappedPE<PE, SE> watcherWpe = (WrappedPE<PE, SE>)pr.val0;
		    			WrappedSE<PE, SE> watcherWse = (WrappedSE<PE, SE>)pr.val1;
		    			
		    			dbgPrint(watcherWpe, 0);
		    			if (watcherWse != null) dbgPrint(watcherWse, 0);   
		    			if (!isValid(pr))
                            continue;
		    			if (debug) System.out.println("Valid");
		    			validProvSet.add(pr);
		    			peWithProvFound = true;
		    			
		    			if (!pewatchpe.containsKey((WrappedPE<PE, SE>)watcherWpe)) {
		    				ArraySet<Pair<Inst, PE>> entSet = new ArraySet<Pair<Inst, PE>>();
		    				entSet.add(ent);
		    				pewatchpe.put(watcherWpe, entSet);
		    			} else {
		    				ArraySet<Pair<Inst, PE>> entSet = pewatchpe.get((WrappedPE<PE, SE>)watcherWpe);
	    					entSet.add(ent);
		    			}
		    			
		        		if (watcherWse != null) {
							if (!sewatchpe.containsKey(watcherWse)) {
		        				ArraySet<Pair<Inst, PE>> entSet = new ArraySet<Pair<Inst, PE>>();
			    				entSet.add(ent);
		        				sewatchpe.put(watcherWse, entSet);
		        			} else {
		        				ArraySet<Pair<Inst, PE>> entSet = sewatchpe.get((WrappedSE<PE, SE>)watcherWse);
		    					entSet.add(ent);
		        			} 
		        		}
		    		}
		    		if (peWithProvFound)
		    			peWithProv++;
		    		else {
		    			peWithNullProv++;
		    			nextwpe.add(wpe);
		    		}
		    		wpe.provSet = validProvSet;
	    		} else {
	    			peWithNullProv++;
	    			nextwpe.add(wpe);
	    		}
    		}
    	}
    	
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		for (SE se : seSet) {
    			Pair<jq_Method, SE> ent = new Pair<jq_Method, SE>(m, se);
    			WrappedSE<PE, SE> wse = wseMap.get(ent);
    			if (debug) System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++");
	    		if (debug) System.out.print("Provenance of : ");
	    		dbgPrint(wse, 0);
	    		if (debug) System.out.println("----------------");
				WrappedPE<PE, SE> watcherWpe = (WrappedPE<PE, SE>)(wse.getWPE());
				if (!isValid(new Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>(watcherWpe, null)))
    				continue;
				if (!pewatchse.containsKey(watcherWpe)) {
					pewatchse.put(watcherWpe, ent);
					dbgPrint(watcherWpe, 0);
					seWithProv++;
				}
    		}
    	}
    	   
    	System.out.println("CompoSBA: PEs with null prov: " + peWithNullProv + "   " + nextwpe.size() + "    PEs with prov: " + peWithProv);
    	System.out.println("CompoSBA: SEs with prov: " + seWithProv);
    	
    	int t1 = pewatchpe.keySet().size();
    	int t2 = pewatchse.keySet().size();
    	int t3 = sewatchpe.keySet().size();
    	System.out.println("CompoSBA: Key sizes: pewatchpe, pewatchse, sewatchpe: " + t1 + "   " + t2 + "   " + t3);
    }
    
    private void validateReverseProvMap() {
    	for (Inst i :pathEdges.keySet()) {
    		Set<PE> peSet = pathEdges.get(i);
    		for (PE pe : peSet) {
    			Pair<Inst, PE> pr = new Pair<Inst, PE>(i, pe);
    			WrappedPE<PE, SE> wpe = wpeMap.get(pr);
    			if (!pewatchpe.containsKey(wpe) && !pewatchse.containsKey(wpe)) {
    				System.out.println("CompoSBA: WPE not prov of anything: " + i.toString() + "::" + i.getMethod().toString());
    			}
    		}
    	}
    	
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		for (SE se : seSet) {
    			Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
    			WrappedSE<PE, SE> wse = wseMap.get(pr);
    			if (!sewatchpe.containsKey(wse)) {
    				//System.out.println("CompoSBA: missing wpe for method: " + m.toString());
    			}
    		}
    	}
    	
    	HashMap<WrappedSE<PE, SE>, Pair<jq_Method, SE>> visitedWse = new HashMap<WrappedSE<PE, SE>, Pair<jq_Method, SE>>();
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		for (SE se : seSet) {
    			Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
    			WrappedSE<PE, SE> wse = wseMap.get(pr);
    			if (!visitedWse.containsKey(wse))
    				visitedWse.put(wse, pr);
    			else {
    				Pair<jq_Method, SE> prOld = visitedWse.get(wse);
    				if (!prOld.val0.equals(pr.val0)) {
    					System.out.println ("CompoSBA: SEs of methods " + prOld.val0.toString() + " and " + pr.val0.toString() + " are mapping to same wse");
    				}else {
    					if (!prOld.val1.equals(pr.val1)) {
    						System.out.println("CompoSBA: Different SEs of method " + pr.val1 + " are mapping to the same wse");		
    					}else
    						System.out.println("CompoSBA: Two same SEs of method " + pr.val1 + " are mapping to the same wse");
    				}
    			}
    		}
    	}
    }
    	    	   	
    	
    private void doForwardAnalysis() {
        int iterationNum = 0; 	
    	Set<Pair<Loc, PE>> initPEs = getInitPathEdges();
        for (Pair<Loc, PE> pair : initPEs) {
            Loc loc = pair.val0;
            PE pe = pair.val1;
            nextwpe.add(wpeMap.get(new Pair<Inst, PE>(loc.i, pe)));             
        }
    
        while (!nextwpe.isEmpty() || !nextwse.isEmpty()) {
        	
        	iterationNum++;
        	
        	//currwpe.clear();
        	//currwpe.addAll(nextwpe);
        	//nextwpe.clear();
        	currwpe = nextwpe;
        	nextwpe = new HashSet<WrappedPE<PE,SE>>();
        	
           	//currwse.clear();
        	//currwse.addAll(nextwse);
        	//nextwse.clear();
        	currwse = nextwse;
        	nextwse = new HashSet<WrappedSE<PE,SE>>();
        	
        	if (debug) System.out.println("Iteration Num: " + iterationNum);
        	if (debug) System.out.println("======================================");
        	
        	for (WrappedPE<PE, SE> watcherWpe : currwpe) {
        		dbgPrint(watcherWpe, iterationNum);
        		boolean heapChanged = getHeapAccessForPE(watcherWpe);
        		if (heapChanged) {
        			ArraySet<Pair<Inst, PE>> watchedSet = pewatchpe.get(watcherWpe);
                    if (watchedSet != null) {
    		            for (Pair<Inst, PE> watched : watchedSet) {
    		            	WrappedPE<PE, SE> watchedWpe = wpeMap.get(watched);
    		            	nextwpe.add(watchedWpe);
    		            }
                    }
                    	
                	Pair<jq_Method, SE> watchedPr = pewatchse.get(watcherWpe);
                	if (watchedPr != null) {
                		WrappedSE<PE, SE> watchedWse = wseMap.get(watchedPr);
                		nextwse.add(watchedWse);
                	}
        		}
        	}
        	
        	for (WrappedSE<PE, SE> watcherWse : currwse) {
        		dbgPrint(watcherWse, iterationNum);
        		boolean heapChanged = getHeapAccessForSE(watcherWse);
        		if (heapChanged) {
        			ArraySet<Pair<Inst, PE>> watchedSet = sewatchpe.get(watcherWse);
            		if (watchedSet != null) {
    	        		for (Pair<Inst, PE> watched : watchedSet) {
    	        			WrappedPE<PE, SE> watchedWpe = wpeMap.get(watched);
    	        			nextwpe.add(watchedWpe);
    	        		}
            		}
        		}
        	}
        }
    }
     
    private void validateForwardAnalysis() {
        for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		HashSet<SE> missing = new HashSet<SE>();
    		HashSet<SE> present = new HashSet<SE>();
    		for (SE se : seSet) {
    			Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
    			if (!seToHeapAccessMap.containsKey(pr)) {
    				missing.add(se);
    			} else {
    				present.add(se);
    			}
    		}
    		for (SE se : missing) {
    			if (!present.contains(se)) 
    				System.out.println ("SE missing for method: " + m.toString() + " " + seSet.size());
    			for (SE se1 : present) {
    				if (se.isSrcEqual(se1))
    					System.out.println("Src nodes are same but dst nodes are different");
    			}
    		}
    	}
    } 
    
    private boolean getHeapAccessForPE(WrappedPE<PE, SE> wpe) {
    	PE currPE = wpe.getPE();
    	Inst i = wpe.getInst();
    	peHeapData++;	
    	HeapAccessData hp = new HeapAccessData();
    	HeapAccessData hpCurrInst = new HeapAccessData();
    	boolean modified = false;
    	int provCnt = 0;
		HeapAccessData reuseHp = null;
    	if (!isMethodEntry(wpe)) {
	    	Set<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> provSet = wpe.getProvSet();
			if (provSet != null) {
	    		for (Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> pr : provSet) {
	    			WrappedPE<PE, SE> provWpe = (WrappedPE<PE, SE>)pr.val0;
	    			WrappedSE<PE, SE> provWse = (WrappedSE<PE, SE>)pr.val1;
	    		
	    			//if (!isValid(pr)) continue;
	    			provCnt++;
	    			if (provWpe != null) {
	    				PE provPe = provWpe.getPE();
	    				Inst pi = provWpe.getInst();
	    				HeapAccessData provHp = peToHeapAccessMap.get(new Pair<Inst, PE>(pi, provPe));
	    				hp.merge(provHp);
	    				reuseHp = provHp;
	    			} 
	    			if (provWse != null) {
	    				provCnt++;
	    				SE provSe = provWse.getSE();
	    				jq_Method m = provWse.getMethod();
	    				HeapAccessData provHp = seToHeapAccessMap.get(new Pair<jq_Method, SE>(m, provSe));
	    				hp.merge(provHp);
	    			}
	    		}
			}
			computeHeapAccessForPE(i, currPE, hpCurrInst);
			modified = hp.merge(hpCurrInst);
			hpCurrInst = null;
    	}
    	Pair<Inst, PE> pkey = new Pair<Inst, PE>(i, currPE);
    	HeapAccessData existing = null;
    	boolean heapChanged = false;
    	
    	boolean scalarReturn = false;
    	if (isScalarReturn(i)) {
    		if (hp.appCallbkPres) {
    			hp = new HeapAccessData();
    			hp.appCallbkPres = true;
    		} else {
    			hp = new HeapAccessData();
    		}
    		scalarReturn = true;
    	}
    	if (peToHeapAccessMap.containsKey(pkey)) 
    		existing = peToHeapAccessMap.get(pkey);
    	
    	if (provCnt == 1 && reuseHp != null && !modified && !scalarReturn) {
    		if (debug) System.out.println("appCallbkPres in HP: " + reuseHp.appCallbkPres);
    		heapChanged = true;
			if (existing == null) {
	    		peToHeapAccessMap.put(pkey, reuseHp);
			}
    		hp = null;
    	} else {
    		if (debug) System.out.println("appCallbkPres in HP: " + hp.appCallbkPres);
    		if (existing != null) {
    			boolean retval = existing.merge(hp);
    			heapChanged = retval;
    		} else {
    			peToHeapAccessMap.put(pkey, hp);
    			heapChanged = true;
    		}
    	}
    	return heapChanged;
    }
    
    private boolean isScalarReturn(Inst i) {
    	boolean isScalarReturn = false;
    	if (!(i instanceof BasicBlock)) {
	    	Quad q = (Quad) i;
	    	Operator operator = q.getOperator();
	        if (operator instanceof Operator.Return) {
	        	if (Return.getSrc(q) instanceof RegisterOperand) {
	    			jq_Type tgtRtype = ((RegisterOperand) (Return.getSrc(q))).getType();
	    			if (tgtRtype.isPrimitiveType()) isScalarReturn = true;
	        	}
	        }
    	}
    	return isScalarReturn;
    }
    
    private boolean getHeapAccessForSE(WrappedSE<PE, SE> wse) {
    	SE currSE = wse.getSE();
    	jq_Method m = wse.getMethod();
    	seHeapData++;   	
    	HeapAccessData hp = new HeapAccessData(); 
    	WrappedPE<PE, SE> provWpe = (WrappedPE<PE, SE>)wse.getWPE();
    	PE provPe = provWpe.getPE();
    	Inst i = provWpe.getInst();
		HeapAccessData provHp = peToHeapAccessMap.get(new Pair<Inst, PE>(i, provPe));
		hp.merge(provHp);
		Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, currSE);
		if (debug) System.out.println("appCallbkPres in HP: " + hp.appCallbkPres);
		
		HeapAccessData existing = null;
		boolean heapChanged = false;
		if (seToHeapAccessMap.containsKey(pr)) 
			existing = seToHeapAccessMap.get(pr);
		if (existing != null) {
			boolean retval = existing.merge(hp);
			heapChanged = retval;
		} else {
			seToHeapAccessMap.put(pr, hp);
			heapChanged = true;
		}
		return heapChanged;
    }
    
    private void postProcessHeapInfoFwd() {
    	Timer revMap = new Timer();
    	revMap.init();
    	createReverseProvMap();
    	revMap.done();
    	long revTime = revMap.getInclusiveTime();
		System.out.println("PROVENANCE REV MAP CREATION TIME: " + revTime);
		System.out.println("PROVENANCE REV MAP CREATION TIME: " + Timer.getTimeStr(revTime));
    	validateReverseProvMap();
    	Timer fwdAn = new Timer();
    	fwdAn.init();
    	doForwardAnalysis();
    	fwdAn.done();
		long fwdTime = fwdAn.getInclusiveTime();
		System.out.println("PROVENANCE FWD ANALYSIS TIME: " + fwdTime);
		System.out.println("PROVENANCE FWD ANALYSIS TIME: " + Timer.getTimeStr(fwdTime));
    	validateForwardAnalysis();
    }
    
    
    /********************************************************************************************/
    
    private ArrayList<Trio<WrappedPE<PE,SE>, WrappedSE<PE,SE>, HeapAccessDataBkwd>> workListBkwd = 
    		new ArrayList<Trio<WrappedPE<PE,SE>, WrappedSE<PE,SE>, HeapAccessDataBkwd>>();
    private HashMap<Pair<Inst, PE>,Pair<jq_Method, SE>> initPeToSeMap = new HashMap<Pair<Inst, PE>, Pair<jq_Method, SE>>();
    private HashMap<Pair<jq_Method, SE>, Pair<Inst, PE>> seToExitPeMap = new HashMap<Pair<jq_Method, SE>, Pair<Inst, PE>>();
    private TObjectIntHashMap<Pair<Inst, PE>> exitPeVisited = new TObjectIntHashMap<Pair<Inst, PE>>();
    protected HashMap<Pair<jq_Method, SE>, HeapAccessDataBkwd> seToHeapAccessMapBkwd = new HashMap<Pair<jq_Method, SE>, HeapAccessDataBkwd>();
    protected HashMap<Pair<Inst, PE>, HeapAccessDataBkwd> peToHeapAccessMapBkwd = new HashMap<Pair<Inst, PE>, HeapAccessDataBkwd>();
	
    public abstract HeapAccessDataBkwd processMiscInst(Inst i, jq_Method m, PE currPE, HeapAccessDataBkwd prevHp);
    public abstract HeapAccessDataBkwd transferFromCallee(Inst i, HeapAccessDataBkwd prevHp, HeapAccessDataBkwd seHp, jq_Method tgtM, PE pe);
	
    private void postProcessHeapInfoBkwd() {
    	Timer revMap = new Timer();
    	revMap.init();
    	createReverseProvMap();
    	createInitPeToSeMap();
    	validateReverseProvMap();
    	revMap.done();
    	long revTime = revMap.getInclusiveTime();
		System.out.println("PROVENANCE REV MAP CREATION TIME: " + revTime);
		System.out.println("PROVENANCE REV MAP CREATION TIME: " + Timer.getTimeStr(revTime));
		
    	Timer bkwdAn = new Timer();
    	bkwdAn.init();
    	doBackwardAnalysis();
    	bkwdAn.done();
		long bkwdTime = bkwdAn.getInclusiveTime();
		System.out.println("PROVENANCE BKWD ANALYSIS TIME: " + bkwdTime);
		System.out.println("PROVENANCE BKWD ANALYSIS TIME: " + Timer.getTimeStr(bkwdTime));
    	validateBackwardAnalysis();
    }
    
    private void createInitPeToSeMap() {
    	HashSet<SE> mappedSes = new HashSet<SE>();
    	for (Inst i : pathEdges.keySet()) {
    		if (i instanceof BasicBlock) {
                BasicBlock bb = (BasicBlock) i;
                if (bb.isEntry() || bb.isExit()) {
                	jq_Method m = i.getMethod();
                	Set<PE> peSet = pathEdges.get(i);
		    		Set<SE> seSet = summEdges.get(m);
		    		if (peSet != null && seSet != null) {
			    		for (PE pe : peSet) {
				    		for (SE se : seSet) {
				    			if (pe.isSrcEqual(se)) {
				    				Pair<Inst, PE> pr = new Pair<Inst, PE>(i, pe);
				    				Pair<jq_Method, SE> msePr = new Pair<jq_Method, SE>(m, se);
				    				if (bb.isEntry())
				    					initPeToSeMap.put(pr, msePr);
				    				else if (bb.isExit())
				    					seToExitPeMap.put(msePr, pr);
				    				mappedSes.add(se);
				    				break;
				    			}
				    		}
			    		}
		    		} else {
		    			if (peSet == null)
		    				System.out.println("PE set for inst " + i.toString() + "::" + m.toString() + " is null");
		    			if (seSet == null)
		    				System.out.println("SE set for method " + m.toString() + " is null");
		    		}
                }
    		}
    	}
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		for (SE se : seSet) {
    			if (!mappedSes.contains(se)) {
    				System.out.println("UNMAPPED: Method " + m.toString() + " has an SE not mapped to an init/exit PE");
    			}
    		}
    	}
    }
    
    private void doBackwardAnalysis() {
    	seedWorkList();
    	while (!workListBkwd.isEmpty()) {
    		int last = workListBkwd.size() - 1;
	      	Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr = workListBkwd.remove(last);
	      	dbgPrint(wlpr.val0, 0);
	      	if (wlpr.val1 != null) dbgPrint(wlpr.val1, 0);
	      	
	      	Inst i = wlpr.val0.getInst();
	      	if (i instanceof BasicBlock) {
                // i is either method entry basic block, method exit basic block, or an empty basic block
                BasicBlock bb = (BasicBlock) i;
                if (bb.isEntry()) {
                    processEntryBkwd(wlpr);
                } else
                	processWorkItemBkwd(wlpr);
            } else
            	processWorkItemBkwd(wlpr);
    	}
    }
    
    private void seedWorkList() {
    	//for (Pair<jq_Method, SE> pr : wseMap.keySet()) {
    	for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		for (SE se : seSet) {
	    		Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
				WrappedSE<PE, SE> wse = wseMap.get(pr);
				if (!sewatchpe.containsKey(wse)) {
					WrappedPE<PE, SE> provWpe = (WrappedPE<PE, SE>)(wse.getWPE());
					Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr = 
							new Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd>(provWpe, null, null);
					addToWorkList(wlpr);
				}
    		}
    	}
    	//for (Pair<Inst, PE> pr : wpeMap.keySet()) {
    	for (Inst i : pathEdges.keySet()) {
    		Set<PE> peSet = pathEdges.get(i);
    		for (PE pe : peSet) {
    			Pair<Inst, PE> pr = new Pair<Inst, PE> (i, pe);
				WrappedPE<PE, SE> wpe = wpeMap.get(pr);
				if (!pewatchpe.containsKey(wpe) && !pewatchse.containsKey(wpe)) {
					Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr = 
							new Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd>(wpe, null, null);
					addToWorkList(wlpr);
				}
    		}
    	}
    	// clear these as they are no longer required after seeding the worklist.
    	sewatchpe = null;
    	pewatchpe = null;
    	pewatchse = null;
    }
    
    private void processEntryBkwd(Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr) {
    	WrappedPE<PE, SE> wpe = wlpr.val0;
    	PE pe = wpe.getPE();
    	Inst i = wpe.getInst();
    	Pair<Inst,PE> pr =  new Pair<Inst,PE>(i, pe);
    	HeapAccessDataBkwd hp = wlpr.val2;
    	HeapAccessDataBkwd seHp = new HeapAccessDataBkwd(hp);
    	Pair<jq_Method, SE> sepr = initPeToSeMap.get(pr);
    	jq_Method m;
    	if (sepr != null) {
	    	m = sepr.val0;
	    	HeapAccessDataBkwd prevSeHp = seToHeapAccessMapBkwd.get(sepr);
	    	if (prevSeHp == null) {
	    		seToHeapAccessMapBkwd.put(sepr, seHp);
	    	} else {
	    		//prevSeHp.merge(seHp);
	    		seHp.merge(prevSeHp);
	    		if (!prevSeHp.equals(seHp)) {
	    			seToHeapAccessMapBkwd.put(sepr, seHp);
	    		} else
	    			seHp = prevSeHp;
	    	}
    	} else {
    		m = i.getMethod();
    	}
    	if (debug) System.out.println("-------------------------------------------------------------------------");
		Set<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> provSet = wpe.getProvSet();
		if (provSet != null) {
    		for (Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> provPr : provSet) {
    			WrappedPE<PE, SE> provWpe = (WrappedPE<PE, SE>)provPr.val0;
    			// For a bb entry node provPr.val1 will always be null 
    			if (provWpe != null) {
    				Inst provI = provWpe.getInst();
    				PE provPe = provWpe.getPE();
    				Pair<Inst, PE> provPePr = new Pair<Inst, PE>(provI, provPe);
    				HeapAccessDataBkwd provHp = peToHeapAccessMapBkwd.get(provPePr);
    				HeapAccessDataBkwd tfrHp = transferFromCallee(provI, provHp, seHp, m, provPe);
    				boolean modified = false;
		        	if (tfrHp != null) {
		        		//modified |= provHp.merge(tfrHp);
		        		tfrHp.merge(provHp);
		        		if (!provHp.equals(tfrHp)) modified = true;
		        	}
		        	if (modified) {
		        		peToHeapAccessMapBkwd.put(provPePr, tfrHp);
	    				dbgPrint(provWpe, 0);
	    				Set<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> provSet1 = provWpe.getProvSet();
	    				if (provSet1 != null) {
	    		    		for (Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> provPr1 : provSet1) {
	    		    			WrappedPE<PE, SE> provWpe1 = (WrappedPE<PE, SE>)provPr1.val0;
	    		    			WrappedSE<PE, SE> provWse1 = (WrappedSE<PE, SE>)provPr1.val1;
	    		    			Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr11 = 
	    		    			   new Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd>(provWpe1, provWse1, tfrHp);
	    		    			addToWorkList(wlpr11);
	    		    		}
	    				}	
		        	} 
    			}
    		}
		}
		if (debug) System.out.println("========================================================================");
    }
    
    private void processWorkItemBkwd(Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr) {
    	WrappedPE<PE, SE> wpe = wlpr.val0;
    	PE pe = wpe.getPE();
    	Inst i = wpe.getInst();
    	Pair<Inst,PE> pr =  new Pair<Inst,PE>(i, pe);
    	
    	jq_Method m = null;
    	SE se = null;
    	Pair<jq_Method, SE> msePr = null;
    	WrappedSE<PE, SE> wse = wlpr.val1;
    	if (wse != null) {
	    	m = wse.getMethod();
	    	se = wse.getSE();
	    	msePr = new Pair<jq_Method, SE>(m, se);
    	}
    	
    	//For later verification
    	if (i instanceof BasicBlock) {
            BasicBlock bb = (BasicBlock) i;
            if (bb.isExit())
            	exitPeVisited.put(pr,1);
    	}
    	
    	HeapAccessDataBkwd hp = peToHeapAccessMapBkwd.get(pr);
    	HeapAccessDataBkwd inHp = wlpr.val2;
    	HeapAccessDataBkwd newHp = processMiscInst(i, m, pe, inHp); 
    	boolean modified = false;
    	if (hp == null) {
    		modified = true;
    		peToHeapAccessMapBkwd.put(pr, newHp);
    		hp = newHp;
    	} else {
    		//modified |= hp.merge(newHp);
    		newHp.merge(hp);
    		if (!hp.equals(newHp)) {
    			modified = true;
    			peToHeapAccessMapBkwd.put(pr, newHp);
    			hp = newHp;
    		}
    	}
    	
    	if (wse != null) {
	    	// if there is a memoized summary, use it
	    	HeapAccessDataBkwd seHp = seToHeapAccessMapBkwd.get(msePr);
	    	if (seHp != null) {
	    		HeapAccessDataBkwd tfrHp = transferFromCallee(i, hp, seHp, m, pe);
	    		if (tfrHp != null) {
	    			// tfrHp could be null if inst i is invoking a method returning void or if inst i is not a Invk
	    			// or if the return register of the Invk inst is not escaping or if method summary is not yet
	    			// computed.
	    			//modified |= hp.merge(tfrHp);
	    			tfrHp.merge(hp);
	    			if (!hp.equals(tfrHp)) {
	    				modified = true;
	    				peToHeapAccessMapBkwd.put(pr, tfrHp);
	        			hp = tfrHp;
	    			}
	    		}
	    	}
    	}
    	
    	if (debug) System.out.println("modified: " + modified);
    	if (debug) System.out.println("appCallbkPres in HP: " + hp.appCallbkPres);
    	if (debug) System.out.println("-------------------------------------------------------------------------");
    	if (modified) {
    		Set<Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>>> provSet = wpe.getProvSet();
			if (provSet != null) {
	    		for (Pair<IWrappedPE<PE, SE>, IWrappedSE<PE, SE>> provPr : provSet) {
	    			WrappedPE<PE, SE> provWpe = (WrappedPE<PE, SE>)provPr.val0;
	    			WrappedSE<PE, SE> provWse = (WrappedSE<PE, SE>)provPr.val1;
	    			Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr1 = 
	    					new Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd>(provWpe, provWse, hp);
	    			addToWorkList(wlpr1);
	    		}
			}	
    	}
    	if (wse != null) {
			WrappedPE<PE, SE> provWpeOfWse = (WrappedPE<PE, SE>)wse.getWPE();
			Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr2 = 
					new Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd>(provWpeOfWse, null, null);
			addToWorkList(wlpr2);
		}
    }
    
    private void addToWorkList(Trio<WrappedPE<PE, SE>, WrappedSE<PE, SE>, HeapAccessDataBkwd> wlpr) {
    	int j = workListBkwd.size() - 1;
    	Inst i = wlpr.val0.getInst();
        jq_Method m = i.getMethod();
        int rpoId = quadToRPOid.get(i);
        for (; j >= 0; j--) {
            WrappedPE<PE, SE> wpe = workListBkwd.get(j).val0;
            Inst i2 = wpe.getInst();
            if (i2.getMethod() != m) break;
            int rpoId2 = quadToRPOid.get(i2);
            if (rpoId2 < rpoId)
                break;
        }
        workListBkwd.add(j + 1, wlpr);
    }
    
    private void validateBackwardAnalysis() {
        for (jq_Method m : summEdges.keySet()) {
    		Set<SE> seSet = summEdges.get(m);
    		HashSet<SE> missing = new HashSet<SE>();
    		HashSet<SE> present = new HashSet<SE>();
    		TIntHashSet vset = null;
    		boolean vsetFail = false;
    		for (SE se : seSet) {
    			Pair<jq_Method, SE> pr = new Pair<jq_Method, SE>(m, se);
    			if (!seToHeapAccessMapBkwd.containsKey(pr)) {
    				missing.add(se);
    			} else {
    				present.add(se);
    				if (vset == null)
    					vset =  seToHeapAccessMapBkwd.get(pr).vSet;
    				else {
    					if(!vset.equals(seToHeapAccessMapBkwd.get(pr).vSet))
    						vsetFail = true;
    				}
    			}
    			Pair<Inst, PE> exitPePr = seToExitPeMap.get(pr);
    			if (!exitPeVisited.contains(exitPePr))
    				System.out.println("Exit PE not visited for method: " + m.toString());
    		}
    		if (vsetFail && summH.fullCG) 
    			System.out.println ("VSets don't match for method: " + m.toString());
    		for (SE se : missing) {
        		System.out.println ("SE missing for method: " + m.toString() + " " + seSet.size());
    		}
    	}
    } 
}