package chord.analyses.ursa.classifier.cipa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.alloc.DomH;
import chord.analyses.dynamic.DynamicHeapAnalysis;
import chord.analyses.dynamic.FldObj;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.var.DomV;
import chord.instr.InstrScheme;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.integer.IntTrio;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import gnu.trove.iterator.TIntObjectIterator;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;

// only give labels on VH, HFH, FH, and IM
@Chord(
name = "cipa-oracle-dynamic", consumes = { "M", "E", "V","I", "H", "F", "VH", "HFH", "IM","FH","IHM"}, 
produces = { "susVH", "unkVH", "susHFH", "susFH", "susIM","unkIM" },
namesOfSigns = { "susVH", "unkVH", "susHFH", "susFH", "susIM","unkIM"}, 
signs = { "V0,H0:V0xH0", "V0,H0:V0xH0", "H0,F0,H1:H0xF0xH1", "F0,H0:F0xH0", "I0,M0:I0xM0", "I0,M0:I0xM0"}
)
public class DynamicPointerAnalysis extends DynamicHeapAnalysis{
	private DomE domE;	
	private DomV domV;
	private DomI domI;
	private DomH domH;
	private Set<Integer> metVs;
	private Set<Pair<Integer,Integer>> metVH;
	private Set<Pair<Integer,Integer>> metFH;
	private Set<Pair<Integer,Integer>> metIM;
	private Set<Trio<Integer,Integer,Integer>> metHFH;
	private Map<Pair<Integer,Integer>,Integer> ihmMap;
	
	// a map from each thread to an object that it returned last time
	private Map<Integer,Integer> thrToRetMap;
		
	
	@Override
	public InstrScheme getInstrScheme() {
		InstrScheme ret = super.getInstrScheme();
		ret.setGetfieldReferenceEvent(true, false, false, false, true);
		ret.setGetstaticReferenceEvent(true, false, false, false, true);
		ret.setAloadReferenceEvent(true, false, false, false, true);
		ret.setPutstaticReferenceEvent(false, false, false, true, true);
		ret.setBefMethodCallEvent(true, false, true);
		ret.setAftMethodCallEvent(true, true, false);
		ret.setReturnReferenceEvent(false, true, true);
		return ret;
	}

	public DynamicPointerAnalysis(){
		this.doStrongUpdates = false;
		this.smashArrayElems = true;
	}
	
	@Override
	public void initAllPasses() {
		super.initAllPasses();
		domE = (DomE)ClassicProject.g().getTrgt("E");
		domV = (DomV)ClassicProject.g().getTrgt("V");
		domI = (DomI)ClassicProject.g().getTrgt("I");
		domH = (DomH)ClassicProject.g().getTrgt("H");
		this.metVs = new HashSet<Integer>();
		this.metVH = new HashSet<Pair<Integer,Integer>>();
		this.metFH = new HashSet<Pair<Integer,Integer>>();
		this.metIM = new HashSet<Pair<Integer,Integer>>();
		this.metHFH = new HashSet<Trio<Integer,Integer,Integer>>();
		ProgramRel ihm = (ProgramRel)ClassicProject.g().getTrgt("IHM");
		ihm.load();
		this.ihmMap = new HashMap<Pair<Integer,Integer>,Integer>();
		for(IntTrio trio : ihm.getAry3IntTuples())
			this.ihmMap.put(new Pair<Integer,Integer>(trio.idx0,trio.idx1), trio.idx2);
	}

	@Override
	public void initPass() {
		super.initPass();
		this.thrToRetMap = new HashMap<Integer,Integer>();
	}

	@Override
	public void donePass() {
		// update HFH
		TIntObjectIterator<List<FldObj>> iter = this.O2FOlist.iterator();
		while(iter.hasNext()){
			iter.advance();
			int b = iter.key();
			List<FldObj> fos = iter.value();
			if(this.O2H.contains(b)){
				int bh = this.O2H.get(b);
				for(FldObj fo : fos){
					int f = fo.f;
					int o = fo.o;
					if(this.O2H.contains(o)){
						int oh = this.O2H.get(o);
						this.metHFH.add(new Trio<Integer,Integer,Integer>(bh,f,oh));		
					}
				}
			}
		}
	}

	@Override
	public void doneAllPasses() {
		super.doneAllPasses();
		//VH
		ProgramRel relVH = (ProgramRel)ClassicProject.g().getTrgt("VH");
		relVH.load();
		
		ProgramRel susVH = (ProgramRel)ClassicProject.g().getTrgt("susVH");
		susVH.zero();
		
		ProgramRel unkVH = (ProgramRel)ClassicProject.g().getTrgt("unkVH");
		unkVH.zero();
		
		for(IntPair p : relVH.getAry2IntTuples()){
			Pair<Integer,Integer> vhPair = new Pair<Integer,Integer>(p.idx0,p.idx1);
			Quad qh = (Quad)domH.get(vhPair.val1);
			if((qh.getOperator() instanceof New || 
					qh.getOperator() instanceof NewArray || 
					qh.getOperator() instanceof MultiNewArray) 
					&& this.metVs.contains(p.idx0)){
				if(!this.metVH.contains(vhPair))
					susVH.add(p.idx0, p.idx1);
			}
			else
				unkVH.add(p.idx0, p.idx1);
		}
		
		susVH.save();
		unkVH.save();
		
		//HFH
		ProgramRel relHFH = (ProgramRel)ClassicProject.g().getTrgt("HFH");
		relHFH.load();
		
		ProgramRel susHFH = (ProgramRel)ClassicProject.g().getTrgt("susHFH");
		susHFH.zero();
		
		for(IntTrio trio : relHFH.getAry3IntTuples()){
			Trio<Integer,Integer,Integer> hfhTrio = new Trio<Integer,Integer,Integer>(trio.idx0, trio.idx1, trio.idx2);
			if(!this.metHFH.contains(hfhTrio)){
				susHFH.add(trio.idx0, trio.idx1, trio.idx2);
			}
		}
		
		susHFH.save();
		
		//FH
		ProgramRel relFH = (ProgramRel)ClassicProject.g().getTrgt("FH");
		relFH.load();
		
		ProgramRel susFH = (ProgramRel)ClassicProject.g().getTrgt("susFH");
		susFH.zero();
		
		for(IntPair p : relFH.getAry2IntTuples()){
			Pair<Integer,Integer> fhPair = new Pair<Integer,Integer>(p.idx0, p.idx1);
			if(!this.metFH.contains(fhPair)){
				susFH.add(p.idx0, p.idx1);
			}
		}
		susFH.save();
		
		//IM
		ProgramRel relIM = (ProgramRel)ClassicProject.g().getTrgt("IM");
		relIM.load();
		
		ProgramRel susIM = (ProgramRel)ClassicProject.g().getTrgt("susIM");
		susIM.zero();
		
		ProgramRel unkIM = (ProgramRel)ClassicProject.g().getTrgt("unkIM");
		unkIM.zero();
		
		Set<Integer> metIs = new HashSet<Integer>();
		
		for(Pair<Integer,Integer> imPair : metIM)
			metIs.add(imPair.val0);
		
		for(IntPair p : relIM.getAry2IntTuples()){
			Pair<Integer,Integer> imPair = new Pair<Integer,Integer>(p.idx0, p.idx1);
			if(metIs.contains(p.idx0)){
				if(!this.metIM.contains(imPair)){
					susIM.add(p.idx0, p.idx1);
				}
			}
			else
				unkIM.add(p.idx0, p.idx1);
		}
		susIM.save();
		unkIM.save();	
	}

	@Override
	public boolean keepO2H() {
		return true;
	}

	@Override
	public void processGetstaticReference(int e, int t, int b, int f, int o) {
		super.processGetstaticReference(e, t, b, f, o);
		if (e >=0 && this.O2H.contains(o)) {
			Quad eq = domE.get(e);
			Register r = Getstatic.getDest(eq).getRegister();
			int ri = domV.indexOf(r);
			this.metVs.add(ri);
			int h = this.O2H.get(o);
			this.metVH.add(new Pair<Integer, Integer>(ri, h));
		}
	}

	@Override
	public void processGetfieldReference(int e, int t, int b, int f, int o) {
		super.processGetfieldReference(e, t, b, f, o);
		if (e >= 0 && this.O2H.contains(o)) {
			Quad eq = domE.get(e);
			Register r = Getfield.getDest(eq).getRegister();
			int ri = domV.indexOf(r);
			this.metVs.add(ri);
			int h = this.O2H.get(o);
			this.metVH.add(new Pair<Integer, Integer>(ri, h));
		}
	}

	@Override
	public void processAloadReference(int e, int t, int b, int i, int o) {
		super.processAloadReference(e, t, b, i, o);
		if (e >= 0 && this.O2H.contains(o)) {
			Quad eq = domE.get(e);
			Register r = ALoad.getDest(eq).getRegister();
			int ri = domV.indexOf(r);
			this.metVs.add(ri);
			int h = this.O2H.get(o);
			this.metVH.add(new Pair<Integer, Integer>(ri, h));
		}

	}

	@Override
	public void processPutstaticReference(int e, int t, int b, int f, int o) {
		super.processPutstaticReference(e, t, b, f, o);
		if(this.O2H.contains(o)){
			int oh = this.O2H.get(o);
			this.metFH.add(new Pair<Integer,Integer>(f,oh));
		}
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
			this.metIM.add(new Pair<Integer, Integer>(i, m));
		}
	}

	@Override
	public void processAftMethodCall(int i, int t, int o) {
		super.processAftMethodCall(i, t, o);
		if(i < 0)
			return;
		Quad iq = domI.get(i);
		RegisterOperand ro = Invoke.getDest(iq);
		if(ro != null && ro.getType().isReferenceType()){
			if (this.O2H.contains(o)) {
				Register rec = ro.getRegister();
				int ri = domV.indexOf(rec);
				this.metVs.add(ri);
				int h = this.O2H.get(o);
				this.metVH.add(new Pair<Integer, Integer>(ri, h));
			}
		}
	}

	@Override
	public void processReturnReference(int p, int t, int o) {
		super.processReturnReference(p, t, o);
		this.thrToRetMap.put(t, o);
	}
	
}
