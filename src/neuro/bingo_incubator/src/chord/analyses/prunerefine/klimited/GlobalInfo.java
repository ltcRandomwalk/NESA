package chord.analyses.prunerefine.klimited;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.alloc.DomH;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.point.DomP;
import chord.analyses.prunerefine.Query;
import chord.analyses.prunerefine.QueryFactory;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.AryNIterable;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

public class GlobalInfo {

	static GlobalInfo G;

	// Ctxts
	final Ctxt emptyCtxt = new Ctxt(new Quad[0]);
	boolean isAlloc(Quad q) { return domH.indexOf(q) != -1; } // Is an allocation site?
	boolean hasHeadSite(Ctxt c) { return c.length() > 0 && c.head() != null; }
	boolean isSummary(Ctxt c) { return c.length() > 0 && c.last() == null; }
	boolean isAtom(Ctxt c) { return c.length() == 0 || c.last() != null; }
	Ctxt summarize(Ctxt c) { assert isAtom(c); return c.append(null); } // add null
	Ctxt atomize(Ctxt c) { assert isSummary(c); return c.prefix(c.length()-1); } // remove null
	int summaryLen(Ctxt c) { assert isSummary(c); return c.length()-1; } // don't count null
	int atomLen(Ctxt c) { assert isAtom(c); return c.length(); }
	int len(Ctxt c) { return isAtom(c) ? c.length() : c.length()-1; }

	// Technical special case designed to handle 0-CFA.
	// Because we always have allocation sites (minH > 0),
	// [*] means any chain starting with a call site.
	// Use [*] if we need to capture those contexts and [] otherwise.
	Ctxt initEmptyCtxt(int minI) { return minI == 0 ? G.summarize(G.emptyCtxt) : G.emptyCtxt; }

	DomV domV;
	DomM domM;
	DomI domI;
	DomH domH;
	DomE domE;
	DomP domP;

	// Compute once using 0-CFA
	HashMap<Quad,List<jq_Method>> jm;
	HashMap<jq_Method,List<Quad>> mj;
	HashMap<jq_Method,List<Quad>> rev_jm;
	Set<Quad> hSet;
	Set<Quad> iSet;
	Set<Quad> jSet;
	
	//Flags & Options
	boolean useObjectSensitivity, pruneCtxts, refineSites, verifyAfterPrune, inspectTransRels;
	String inQueryRel, outQueryRel, queryRel;
	
	int minH, minI, maxH, maxI;
	
	List<String> initTasks;
	List<String> tasks;
	String relevantTask;
	String transTask;
	
	
	void readQueries(ProgramRel rel, Collection<Query> queries, QueryFactory qFactory){
		AryNIterable result = rel.getAryNValTuples();
		for (Object[] p : result){ 
			queries.add(qFactory.create(p));
		}
	}

	void sleep(int seconds) {
		try { Thread.sleep(seconds*1000); } catch(InterruptedException e) { }
	}

	public GlobalInfo() {
		domV = (DomV) ClassicProject.g().getTrgt("V"); ClassicProject.g().runTask(domV);
		domM = (DomM) ClassicProject.g().getTrgt("M"); ClassicProject.g().runTask(domM);
		domI = (DomI) ClassicProject.g().getTrgt("I"); ClassicProject.g().runTask(domI);
		domH = (DomH) ClassicProject.g().getTrgt("H"); ClassicProject.g().runTask(domH);
		domE = (DomE) ClassicProject.g().getTrgt("E"); ClassicProject.g().runTask(domE);
		domP = (DomP) ClassicProject.g().getTrgt("P"); ClassicProject.g().runTask(domP);
	}

	// Map allocation site to its containing class
	jq_Type h2c(Quad h) { return h.getMethod().getDeclaringClass(); }

	jq_Type h2t(Quad h) {
		Operator op = h.getOperator();
		if (op instanceof New) 
			return New.getType(h).getType();
		else if (op instanceof NewArray)
			return NewArray.getType(h).getType();
		else if (op instanceof MultiNewArray)
			return MultiNewArray.getType(h).getType();
		else
			return null;
	}

	// Helpers for displaying stuff
	String pstr(Quad p) { return new File(p.toJavaLocStr()).getName(); }
	String hstr(Quad h) {
		jq_Type t = h2t(h);
		return pstr(h)+"("+(t == null ? "?" : t.shortName())+")";
	}
	String istr(Quad i) {
		jq_Method m = InvokeStatic.getMethod(i).getMethod();
		return pstr(i)+"("+m.getName()+")";
	}
	String jstr(Quad j) { return isAlloc(j) ? hstr(j) : istr(j); }
	String estr(Quad e) {
		Operator op = e.getOperator();
		return pstr(e)+"("+op+")";
	}
	String cstr(Ctxt c) {
		StringBuilder buf = new StringBuilder();
		//buf.append(domC.indexOf(c));
		buf.append('{');
		for (int i = 0; i < c.length(); i++) {
			if (i > 0) buf.append(" | ");
			Quad q = c.get(i);
			buf.append(q == null ? "+" : jstr(q));
		}
		buf.append('}');
		return buf.toString();
	}
	String fstr(jq_Field f) { return f.getDeclaringClass()+"."+f.getName(); }
	String vstr(Register v) { return v+"@"+mstr(domV.getMethod(v)); }
	String mstr(jq_Method m) { return m.getDeclaringClass().shortName()+"."+m.getName(); }

	String render(Object o) {
		if (o == null) return "NULL";
		if (o instanceof String) return (String)o;
		if (o instanceof Integer) return o.toString();
		if (o instanceof Ctxt) return cstr((Ctxt)o);
		if (o instanceof jq_Field) return fstr((jq_Field)o);
		if (o instanceof jq_Method) return mstr((jq_Method)o);
		if (o instanceof Register) return vstr((Register)o);
		if (o instanceof Quad) {
			Quad q = (Quad)o;
			if (domH.indexOf(q) != -1) return hstr(q);
			if (domI.indexOf(q) != -1) return istr(q);
			if (domE.indexOf(q) != -1) return estr(q);
			return q.toString();
			//throw new RuntimeException("Quad not H, I, or E: " + q);
		}
		if (o instanceof Pair) {
			Pair p = (Pair)o;
			return "<"+render(p.val0)+","+render(p.val1)+">";
		}
		return o.toString();
		//throw new RuntimeException("Unknown object (not abstract object, contextual variable or field: "+o+" has type "+o.getClass());
	}

	PrintWriter getOut(Socket s) throws IOException { return new PrintWriter(s.getOutputStream(), true); }
	BufferedReader getIn(Socket s) throws IOException { return new BufferedReader(new InputStreamReader(s.getInputStream())); }

}
