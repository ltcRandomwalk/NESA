package chord.analyses.composba;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.bddbddb.Dom;
import chord.program.Program;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

public class InfiCFAHeapAccessQuadVisitor extends QuadVisitor.EmptyVisitor {
	public static RelTupleIndex HFHIndex;
	public static RelTupleIndex FHIndex;
	public static HashMap<String, int[]> mark;
	
	public BitAbstractState istate;    // immutable, will never be null
	public BitEdge<Quad> pe;
	public HeapAccessData heapData;
	private DomF domF;
	private DomM domM;
	private ProgramRel relHFH;
	private ProgramRel relFH;
	private Map<jq_Field, BitSet> FHMap;
	private Map<Pair<Integer, jq_Field>, BitSet> HFHMap;
	private jq_Reference javaLangObject;
	private Map<Pair<Quad, BitEdge<Quad>>, ArraySet<jq_Method>> targetsMapRefl;
	
	
	public InfiCFAHeapAccessQuadVisitor(Map<jq_Field, BitSet> FHMap,
			Map<Pair<Integer, jq_Field>, BitSet> HFHMap, Map<Pair<Quad, BitEdge<Quad>>, ArraySet<jq_Method>> targetsMapRefl){
		javaLangObject = Program.g().getClass("java.lang.Object");
		assert (javaLangObject != null);
		this.domF = (DomF) ClassicProject.g().getTrgt("F");
		this.domM = (DomM) ClassicProject.g().getTrgt("M");
		this.FHMap = FHMap;
		this.HFHMap = HFHMap;
		this.targetsMapRefl = targetsMapRefl;
		relHFH = (ProgramRel) ClassicProject.g().getTrgt("HFH");
		relHFH.load();
		relFH = (ProgramRel) ClassicProject.g().getTrgt("FH");
		relFH.load();
	}
	
	@Override
	public void visitInvoke(Quad q) {
		Set<jq_Method> targets = targetsMapRefl.get(new Pair<Quad, BitEdge<Quad>> (q, pe));
		for (jq_Method m : targets) {
			int idx = domM.indexOf(m);
			if (!mBelongsToLib(idx)) {
				heapData.appCallbkPres = true;
				break;
			}
		}
	}
	
	
	@Override
	public void visitALoad(Quad q) {
		jq_Type dstRType = ALoad.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;
		
		//System.out.println("visitALoad");
		if (ALoad.getBase(q) instanceof RegisterOperand) {
			Register srcR = ((RegisterOperand) ALoad.getBase(q)).getRegister();
			BitSet basePointsTo = istate.envLocal.get(srcR);

			if(basePointsTo != null){
				//BitSet filterSet = THFilterMap.get(dstRType);
				// if (filterSet != null) {
					for (int quad = basePointsTo.nextSetBit(0); quad >= 0; quad = basePointsTo.nextSetBit(quad+1)) {
						if(quad != 0){
							Pair<Integer, jq_Field> pair = new Pair<Integer, jq_Field>(quad, null);
							BitSet fieldPointsTo = HFHMap.get(pair);
							if(fieldPointsTo != null){
							//	fieldPointsTo.and(filterSet);
								if(fieldPointsTo.cardinality() > 0) {
									for (int hidx = fieldPointsTo.nextSetBit(0); hidx >= 0; hidx = fieldPointsTo.nextSetBit(hidx+1)){
										int[] relElem = new int[3];
										relElem[0] = quad;
										relElem[1] = 0;
										relElem[2] = hidx;
										Tuple t = new Tuple(relHFH, relElem);
										int ndxInRel = HFHIndex.getIndex(t);
										heapData.addToHFHNdx(ndxInRel);
										if (!belongsToLib(t)) heapData.appAccessPres = true;
									}
								}
							}
						}
					}
			//	}	
			}
		}
	}

	
	@Override
	public void visitGetstatic(Quad q) {
		jq_Type dstRType = Getstatic.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;

		//System.out.println("visitGetStatic");
		jq_Field srcF = Getstatic.getField(q).getField();
		BitSet staticPointsTo = FHMap.get(srcF);
		
		if(staticPointsTo != null){
			BitSet dstFiltered = new BitSet();
			dstFiltered.or(staticPointsTo);
			//BitSet filterSet = THFilterMap.get(dstRType);
			//if(filterSet != null) dstFiltered.and(filterSet); else dstFiltered.clear();	
			if(!dstFiltered.isEmpty()){
				int fidx = domF.getOrAdd(srcF);
				for (int hidx = dstFiltered.nextSetBit(0); hidx >= 0; hidx = dstFiltered.nextSetBit(hidx+1)){
					int[] relElem = new int[2];
					relElem[0] = fidx;
					relElem[1] = hidx;
					Tuple t = new Tuple(relFH, relElem);
					int ndxInRel = FHIndex.getIndex(t);
					heapData.addToFHNdx(ndxInRel);
					if (!belongsToLib(t)) heapData.appAccessPres = true;
				}
				return;
			}
		}
	}

	@Override
	public void visitGetfield(Quad q) {
		jq_Type dstRType = Getfield.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;
		
		//System.out.println("visitGetField");
		if (Getfield.getBase(q) instanceof RegisterOperand) {
			Register srcR = ((RegisterOperand) Getfield.getBase(q)).getRegister();
			jq_Field srcF = Getfield.getField(q).getField();
			
			BitSet basePointsTo = istate.envLocal.get(srcR);
			
			if(basePointsTo != null){
				//BitSet filterSet = THFilterMap.get(dstRType);
				//if (filterSet != null) {
					int fidx = domF.getOrAdd(srcF);
					for (int quad = basePointsTo.nextSetBit(0); quad >= 0; quad = basePointsTo.nextSetBit(quad+1)) {
						if(quad != 0){
							Pair<Integer, jq_Field> pair = new Pair<Integer, jq_Field>(quad, srcF);
							BitSet fieldPointsTo = HFHMap.get(pair);
							if(fieldPointsTo != null){
								//fieldPointsTo.and(filterSet);
								if(fieldPointsTo.cardinality() > 0) {
									for (int hidx = fieldPointsTo.nextSetBit(0); hidx >= 0; hidx = fieldPointsTo.nextSetBit(hidx+1)){
										int[] relElem = new int[3];
										relElem[0] = quad;
										relElem[1] = fidx;
										relElem[2] = hidx;
										Tuple t = new Tuple(relHFH, relElem);
										int ndxInRel = HFHIndex.getIndex(t);
										heapData.addToHFHNdx(ndxInRel);
										if (!belongsToLib(t)) heapData.appAccessPres = true;
									}
								}
							}
						}
					}
				//}
			}
		}
	}
	
	private boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		for (int i = 0; i < dArr.length; i++) {
			if (mark.containsKey(dArr[i].getName()))
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
	
	private boolean mBelongsToLib(int i){
		int type = 0;
		if (mark.containsKey("M")) {
			type |= ((int[])mark.get("M"))[i];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
}


