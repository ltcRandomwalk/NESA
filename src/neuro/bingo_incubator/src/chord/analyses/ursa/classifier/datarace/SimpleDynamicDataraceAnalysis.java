package chord.analyses.ursa.classifier.datarace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.dynamic.FldObj;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.point.DomP;
import chord.analyses.thread.cs.DomAS;
import chord.instr.InstrScheme;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.DynamicAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.integer.IntPent;
import chord.util.tuple.integer.IntQuad;
import chord.util.tuple.integer.IntTrio;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;

/**
 * We get labels the following relations: escE PathEdge_cs IM CFC(HFH)
 * 
 * The relations are divided to two kinds: 1. program point related 2. global.
 * For 1, we declare a tuple is spurious only if the program point is indeed
 * reached.
 * 
 * @author xin
 *
 */
@Chord(name = "datarace-oracle-dynamic", consumes = { "M", "P", "I", "C", "H", "AS", "F", "escE", "PathEdge_cs", "CICM", "IHM",
		"CFC" }, 
produces = { "susEscE", "unkEscE", "susPathEdge_cs", "unkPathEdge_cs", "susCICM",
				"susCFC" },
namesOfSigns = { "susEscE", "unkEscE", "susPathEdge_cs", "unkPathEdge_cs", "susCICM",
						"susCFC" }, 
signs = { "E0:E0", "E0:E0", "C0,P0,AS0,AS1,AS2:C0xP0xAS0xAS1xAS2",
								"C0,P0,AS0,AS1,AS2:C0xP0xAS0xAS1xAS2", "C0,I0,C1,M0:C0xI0xC1xM0", "C0,F0,C1:C0xF0xC1" })
public class SimpleDynamicDataraceAnalysis extends DynamicAnalysis {
	private InstrScheme instrScheme;

	protected TIntObjectHashMap<List<FldObj>> O2FOlist;
	// map from each object to the index in domH of its alloc site
	// maintained only if keepO2H() is true
	protected TIntIntHashMap O2H;

	protected Set<Trio<Integer, Integer, Integer>> observedHFH;

	// program point, thread it is in, another thread in parallel
	protected Set<Trio<Integer, Integer, Integer>> observedMHP;
	// program point p executed in threadP
	protected Set<Integer> observedMHPQs;

	protected Set<Integer> observedEscE;
	protected Set<Integer> observedE;

	protected Set<Pair<Integer, Integer>> observedIM;

	protected Set<Integer> startedThreads;
	protected Map<Integer, Integer> conToAbsThread;

	protected Set<Integer> threadInThreadStart;

	protected DomM domM;
	protected DomP domP;
	protected DomI domI;
	protected DomAS domAS;
	protected ProgramRel escE;
	protected Set<Integer> escESet;
	protected DomC domC;
	protected DomH domH;

	protected Set<Integer> escapedOs;

	protected int numF;
	
	protected Map<Pair<Integer,Integer>,Integer> ihmMap;

	@Override
	public InstrScheme getInstrScheme() {
		if (instrScheme != null)
			return instrScheme;
		instrScheme = new InstrScheme();
		// for thresc
		instrScheme.setPutfieldReferenceEvent(true, false, true, false, true);
		instrScheme.setAstoreReferenceEvent(true, false, true, false, true);
		instrScheme.setPutfieldPrimitiveEvent(true, false, true, false);
		instrScheme.setAstorePrimitiveEvent(true, false, true, false);

		instrScheme.setGetfieldReferenceEvent(true, true, false, false, false);
		instrScheme.setAloadReferenceEvent(true, true, false, false, false);
		instrScheme.setGetfieldPrimitiveEvent(true, true, false, false);
		instrScheme.setAloadPrimitiveEvent(true, true, false, false);

		instrScheme.setPutstaticReferenceEvent(false, false, true, false, true);

		instrScheme.setThreadStartEvent(true, true, true);

		// for callgraph
		instrScheme.setBefMethodCallEvent(true, true, true);
		instrScheme.setEnterMethodEvent(true, true);

		// for may-happen in parallel
		instrScheme.setLeaveMethodEvent(true, true);
		instrScheme.setEnterMainMethodEvent(true);
		instrScheme.setLeaveMethodEvent(true, true);

		// for HFH
		instrScheme.setBefNewEvent(true, false, true);
		instrScheme.setNewArrayEvent(true, false, true);

		return instrScheme;
	}

	@Override
	public Class getInstrumentorClass() {
		return CheckExcludeInstrumentor.class;
	}

	@Override
	public void initPass() {
		super.initPass();
		this.O2FOlist = new TIntObjectHashMap<List<FldObj>>();
		this.O2H = new TIntIntHashMap();
		this.startedThreads = new HashSet<Integer>();
		this.conToAbsThread = new HashMap<Integer, Integer>();
		this.escapedOs = new HashSet<Integer>();
		this.threadInThreadStart = new HashSet<Integer>();
	}

	@Override
	public void initAllPasses() {
		super.initAllPasses();
		this.observedE = new HashSet<Integer>();
		this.observedEscE = new HashSet<Integer>();
		this.observedHFH = new HashSet<Trio<Integer, Integer, Integer>>();
		this.observedMHP = new HashSet<Trio<Integer, Integer, Integer>>();
		this.observedMHPQs = new HashSet<Integer>();
		this.observedIM = new HashSet<Pair<Integer, Integer>>();
		this.escE = (ProgramRel) ClassicProject.g().getTrgt("escE");
		this.escE.load();

		this.escESet = new HashSet<Integer>();
		for (int[] e : escE.getAryNIntTuples()) {
			escESet.add(e[0]);
		}

		domM = (DomM) ClassicProject.g().getTrgt("M");
		domP = (DomP) ClassicProject.g().getTrgt("P");
		domI = (DomI) ClassicProject.g().getTrgt("I");
		domAS = (DomAS) ClassicProject.g().getTrgt("AS");
		domC = (DomC) ClassicProject.g().getTrgt("C");
		domH = (DomH) ClassicProject.g().getTrgt("H");
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		this.numF = domF.size();
		
		ProgramRel ihm = (ProgramRel) ClassicProject.g().getTrgt("IHM");
		ihm.load();
		this.ihmMap = new HashMap<Pair<Integer, Integer>, Integer>();
		for (IntTrio trio : ihm.getAry3IntTuples())
			this.ihmMap.put(new Pair<Integer, Integer>(trio.idx0, trio.idx1), trio.idx2);

	}

	@Override
	public void doneAllPasses() {
		ProgramRel susEscE = (ProgramRel) ClassicProject.g().getTrgt("susEscE");
		susEscE.zero();
		ProgramRel unkEscE = (ProgramRel) ClassicProject.g().getTrgt("unkEscE");
		unkEscE.zero();

		for (int e : this.observedE)
			if (!this.observedEscE.contains(e))
				susEscE.add(e);
		
		for(int[] es : this.escE.getAryNIntTuples()){
			if(!this.observedE.contains(es[0]))
				unkEscE.add(es[0]);;
		}

		System.out.println(
				susEscE.size() + " out of " + this.escE.size() + " " + this.escE.getName() + " might be false.");
		System.out.println("Unknown: "+unkEscE.size());

		susEscE.save();
		unkEscE.save();

		ProgramRel susPathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("susPathEdge_cs");
		susPathEdge_cs.zero();
		
		ProgramRel unkPathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("unkPathEdge_cs");
		unkPathEdge_cs.zero();

		ProgramRel pathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("PathEdge_cs");
		pathEdge_cs.load();

		for (IntPent val : pathEdge_cs.getAry5IntTuples()) {
			if (val.idx4 == 0)
				continue;
			if (this.observedMHPQs.contains(val.idx1)) {
				if (!this.observedMHP.contains(new Trio<Integer, Integer, Integer>(val.idx1, val.idx2, val.idx4)))
					susPathEdge_cs.add(val.idx0, val.idx1, val.idx2, val.idx3, val.idx4);
			}
			else
				unkPathEdge_cs.add(val.idx0, val.idx1, val.idx2, val.idx3, val.idx4);
		}

		System.out.println(susPathEdge_cs.size() + " out of " + pathEdge_cs.size() + " " + pathEdge_cs.getName()
				+ " might be false.");
		System.out.println("Unkown: "+unkPathEdge_cs.size());

		susPathEdge_cs.save();
		unkPathEdge_cs.save();

		// process IM
		ProgramRel susCICM = (ProgramRel) ClassicProject.g().getTrgt("susCICM");
		susCICM.zero();

		ProgramRel cicm = (ProgramRel) ClassicProject.g().getTrgt("CICM");
		cicm.load();
		Set<Integer> observedIs = new HashSet<Integer>();
		for (Pair<Integer, Integer> im : this.observedIM)
			observedIs.add(im.val0);
		for (IntQuad iq : cicm.getAry4IntTuples()) {
			if (observedIs.contains(iq.idx1) && !this.observedIM.contains(new Pair<Integer, Integer>(iq.idx1, iq.idx3)))
				susCICM.add(iq.idx0, iq.idx1, iq.idx2, iq.idx3);
		}

		System.out.println(susCICM.size() + " out of " + cicm.size() + " " + cicm.getName() + " might be false.");

		susCICM.save();

		// process CFC
		ProgramRel susCFC = (ProgramRel) ClassicProject.g().getTrgt("susCFC");
		susCFC.zero();

		ProgramRel cfc = (ProgramRel) ClassicProject.g().getTrgt("CFC");
		cfc.load();

		for (IntTrio trio : cfc.getAry3IntTuples()) {
			Ctxt c1 = domC.get(trio.idx0);
			Ctxt c2 = domC.get(trio.idx2);
			Quad h1 = c1.last();
			Quad h2 = c2.last();
			int hi1 = domH.indexOf(h1);
			int hi2 = domH.indexOf(h2);
			if (!this.observedHFH.contains(new Trio<Integer, Integer, Integer>(hi1, trio.idx1, hi2)))
				susCFC.add(trio.idx0, trio.idx1, trio.idx2);
		}

		System.out.println(susCFC.size() + " out of " + cfc.size() + " " + cfc.getName() + " might be false.");

		susCFC.save();

	}

	@Override
	public void processEnterMethod(int m, int t) {
		// process mhp
		if (this.threadInThreadStart.contains(t))
			return;

		jq_Method meth = domM.get(m);
		Inst entry = meth.getCFG().entry();
		int entryId = this.domP.indexOf(entry);
		if (!this.conToAbsThread.containsKey(t)) {
			System.out.println("Warning: missing " + t + " in " + this.conToAbsThread);
			return;
		}
		int absCTId = this.conToAbsThread.get(t);
		this.observedMHPQs.add(entryId);
		for (int ot : this.startedThreads) {
			if (ot == t)
				continue;
			int absOTId = this.conToAbsThread.get(ot);
			this.observedMHP.add(new Trio<Integer, Integer, Integer>(entryId, absCTId, absOTId));
		}

	}

	@Override
	public void processLeaveMethod(int m, int t) {
		if (!this.threadInThreadStart.contains(t)) {
			jq_Method meth = domM.get(m);
			Inst exit = meth.getCFG().exit();
			int exitId = this.domP.indexOf(exit);
			if (!this.conToAbsThread.containsKey(t)) {
				System.out.println("Warning: missing " + t + " in " + this.conToAbsThread);
				return;
			}
			int absCTId = this.conToAbsThread.get(t);
			this.observedMHPQs.add(exitId);
			for (int ot : this.startedThreads) {
				if (ot == t)
					continue;
				int absOTId = this.conToAbsThread.get(ot);
				this.observedMHP.add(new Trio<Integer, Integer, Integer>(exitId, absCTId, absOTId));
			}
		}
		if (this.domM.get(m) == Program.g().getThreadStartMethod())
			this.threadInThreadStart.remove(t);
	}

	@Override
	public void processBefMethodCall(int i, int t, int o) {
		if (i < 0) {
			return;
		}
		if(!this.O2H.contains(o))
			return;
		Quad qi = domI.get(i);
		if(Invoke.getMethod(qi).getMethod().isStatic())
			return;
		int h = this.O2H.get(o);
		Pair<Integer,Integer> ih = new Pair<Integer,Integer>(i,h);
		if (this.ihmMap.containsKey(ih)) {
			int m = this.ihmMap.get(ih);
			this.observedIM.add(new Pair<Integer, Integer>(i, m));
		}
	}

	@Override
	public void processBefNew(int h, int t, int o) {
		this.processNew(h, o);
	}

	@Override
	public void processNewArray(int h, int t, int o) {
		this.processNew(h, o);
	}

	@Override
	public void processPutstaticReference(int e, int t, int b, int f, int o) {
		this.escapedOs.addAll(this.getReachableOs(o));
	}

	@Override
	public void processGetfieldPrimitive(int e, int t, int b, int f) {
		this.processHeapWr(e, b, f, -1);
	}

	@Override
	public void processGetfieldReference(int e, int t, int b, int f, int o) {
		this.processHeapWr(e, b, f, -1);
	}

	@Override
	public void processPutfieldPrimitive(int e, int t, int b, int f) {
		this.processHeapWr(e, b, f, -1);
	}

	@Override
	public void processPutfieldReference(int e, int t, int b, int f, int o) {
		this.processHeapWr(e, b, f, o);
	}

	@Override
	public void processAloadPrimitive(int e, int t, int b, int i) {
		this.processHeapWr(e, b, numF + i, -1);
	}

	@Override
	public void processAloadReference(int e, int t, int b, int i, int o) {
		this.processHeapWr(e, b, numF + i, -1);
	}

	@Override
	public void processAstorePrimitive(int e, int t, int b, int i) {
		this.processHeapWr(e, b, numF + i, -1);
	}

	@Override
	public void processAstoreReference(int e, int t, int b, int i, int o) {
		this.processHeapWr(e, b, numF + i, o);
	}

	@Override
	public void processThreadStart(int i, int t, int o) {
		Quad qi = this.domI.get(i);
		jq_Method meth = Program.g().getThreadStartMethod();
		int absTid = domAS.indexOf(new Pair<Quad, jq_Method>(qi, meth));
		this.conToAbsThread.put(o, absTid);

		this.escapedOs.addAll(this.getReachableOs(o));
		this.startedThreads.add(o);
		this.threadInThreadStart.add(t);
	}

	@Override
	public void processEnterMainMethod(int t) {
		int absThread = 1;
		this.startedThreads.add(t);
		this.conToAbsThread.put(t, absThread);
	}

	/*****************************************************************/
	// Auxiliary routines
	/*****************************************************************/

	protected void processNew(int h, int o) {
		if (o != 0 && h >= 0)
			O2H.put(o, h);
	}

	// assumes b != 0 && f >= 0
	protected void processHeapWr(int e, int b, int f, int r) {
		if (e >= 0 && this.escESet.contains(e) && !this.observedEscE.contains(e)) {
			this.observedE.add(e);
			if (this.escapedOs.contains(b))
				this.observedEscE.add(e);
		}

		if (r < 0) // really a read
			return;

		int hb = this.O2H.get(b);
		int hr = this.O2H.get(r);
		if (f > numF)
			this.observedHFH.add(new Trio<Integer, Integer, Integer>(hb, 0, hr));
		else
			this.observedHFH.add(new Trio<Integer, Integer, Integer>(hb, f, hr));

		// update heap graph
		if (r == 0) {
			// this is a strong update; so remove field f if it is there
			List<FldObj> fwd = O2FOlist.get(b);
			if (fwd == null)
				return;
			int n = fwd.size();
			for (int i = 0; i < n; i++) {
				FldObj fo = fwd.get(i);
				if (fo.f == f) {
					fwd.remove(i);
					break;
				}
			}
			return;
		}
		List<FldObj> fwd = O2FOlist.get(b);
		boolean added = false;
		if (fwd == null) {
			fwd = new ArrayList<FldObj>();
			O2FOlist.put(b, fwd);
		} else {
			int n = fwd.size();
			for (int i = 0; i < n; i++) {
				FldObj fo = fwd.get(i);
				if (fo.f == f) {
					fo.o = r;
					added = true;
					break;
				}
			}
		}

		if (!added)
			fwd.add(new FldObj(f, r));
	}

	protected Set<Integer> getReachableOs(int o) {
		Set<Integer> ret = new HashSet<Integer>();
		ret.add(o);
		Queue<Integer> workList = new LinkedList<Integer>();
		workList.add(o);
		while (!workList.isEmpty()) {
			int no = workList.remove();
			if (O2FOlist.contains(no))
				for (FldObj fo : O2FOlist.get(no))
					if (ret.add(fo.o))
						workList.add(fo.o);
		}
		return ret;
	}
}
