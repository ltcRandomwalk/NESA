package chord.analyses.bigcode.callgraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.bddbddb.Rel.RelView;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.graph.AbstractGraph;
import chord.util.ArraySet;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Invoke;

/**
 * Implementation of a context-insensitive call graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CICG extends AbstractGraph<jq_Method> implements ICICG {
    private DomM domM;
    private Set<jq_Method> rootM;
    private Set<jq_Method> reachableM;
    private Map<Quad, Set<jq_Method>> IM;
    private Map<jq_Method, Set<Quad>> MI;
    private Map<jq_Method, Set<jq_Method>> MM;
    public CICG(DomM domM, Set<jq_Method> rootM, Set<jq_Method> reachableM,
    	Map<Quad, Set<jq_Method>> IM, Map<jq_Method, Set<Quad>> MI) {
        this.domM = domM;
        this.rootM = rootM;
        this.reachableM = reachableM;
        this.IM = IM;
        this.MI = MI;
        this.MM = new HashMap<jq_Method, Set<jq_Method>>();
        for (jq_Method m : this.MI.keySet()) {
        	Set<Quad> invks = this.MI.get(m);
        	for (Quad invk : invks) {
        		Set<jq_Method> tgts = this.IM.get(invk);
        		if (tgts != null) {
        			for (jq_Method tgt : tgts) {
        				Set<jq_Method> succs = MM.get(m);
        				if (succs == null) {
        					succs = new ArraySet<jq_Method>();
        					MM.put(m, succs);
        				}
        				succs.add(tgt);
        			}
        		}
        	}
        }
    }
    public Set<Quad> getCallers(jq_Method meth) {
        Set<Quad> invks = new ArraySet<Quad>();
        for (Quad invk : IM.keySet()) {
        	if (IM.get(invk).contains(meth)) 
        		invks.add(invk);
        }
        return invks;
    }
    public ArraySet<Quad> getCallersOrdered(jq_Method meth) {
    	throw new UnsupportedOperationException();
    }
    public Set<jq_Method> getTargets(Quad invk) {
        Set<jq_Method> meths = new ArraySet<jq_Method>();
        Set<jq_Method> methsE = IM.get(invk);
        if (methsE != null) meths.addAll(methsE);
        return meths;
    }
    public ArraySet<jq_Method> getTargetsOrdered(Quad invk) {
    	throw new UnsupportedOperationException();
    }
    public int numRoots() {
        return rootM.size();
    }
    public int numNodes() {
        return reachableM.size();
    }
    public int numPreds(jq_Method node) {
        throw new UnsupportedOperationException();
    }
    public int numSuccs(jq_Method node) {
        throw new UnsupportedOperationException();
    }
    public Set<jq_Method> getRoots() {
        return rootM;
    }
    public Set<jq_Method> getRootsOrdered() {
    	throw new UnsupportedOperationException();
    }
    public Set<jq_Method> getNodes() {
    	return reachableM;
    }
    public ArraySet<jq_Method> getNodesOrdered() {
    	throw new UnsupportedOperationException();
    }
    public Set<jq_Method> getPreds(jq_Method meth) {
    	Set<jq_Method> preds = new ArraySet<jq_Method>();
        for (jq_Method pred : MM.keySet()) {
        	if (MM.get(pred).contains(meth)) 
        		preds.add(pred);
        }
        return preds;
    }
    public Set<jq_Method> getSuccs(jq_Method meth) {
    	Set<jq_Method> succs = new ArraySet<jq_Method>();
        Set<jq_Method> meths = MM.get(meth);
     //   if (meths != null) succs.addAll(meths);
        if (meths != null) return meths;
        return succs;
    }
    public Set<Quad> getLabels(jq_Method srcMeth, jq_Method dstMeth) {
        Set<Quad> invks = new ArraySet<Quad>();
        ControlFlowGraph cfg = srcMeth.getCFG();
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                Operator op = q.getOperator();
                if (op instanceof Invoke && calls(q, dstMeth))
                    invks.add(q);
            }
        }
        return invks;
    }
    public boolean calls(Quad invk, jq_Method meth) {
    	Set<jq_Method> tgts = IM.get(invk);
    	if (tgts != null) return tgts.contains(meth);
    	return false;
    }
    public boolean hasRoot(jq_Method meth) {
        return domM.indexOf(meth) == 0;
    }
    public boolean hasNode(jq_Method meth) {
        return reachableM.contains(meth);
    }
    public boolean hasEdge(jq_Method meth1, jq_Method meth2) {
    	Set<jq_Method> succs = MM.get(meth1);
    	if (succs != null) return succs.contains(meth2);
    	return false;
    }
    /**
     * Frees relations used by this call graph if they are in memory.
     * <p>
     * This method must be called after clients are done exercising
     * the interface of this call graph.
     */
    public void free() {
    	MM.clear();
    }
}

