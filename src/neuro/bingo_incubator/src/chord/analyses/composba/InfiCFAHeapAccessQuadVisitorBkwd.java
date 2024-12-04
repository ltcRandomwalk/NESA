package chord.analyses.composba;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.program.Program;
import chord.project.ClassicProject;
import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

public class InfiCFAHeapAccessQuadVisitorBkwd extends QuadVisitor.EmptyVisitor {
	public static HashMap<String, int[]> mark;
	
	public BitAbstractState istate;    // immutable, will never be null
	public BitEdge<Quad> pe;
	public HeapAccessDataBkwd currHp;
	public jq_Method tgtMethod;
	public boolean simulateFwd;
	public boolean fullCG;
	private DomF domF;
	private DomM domM;
	private DomV domV;
	private jq_Reference javaLangObject;
	private TObjectIntHashMap<Pair<Integer, jq_Field>> HFMap;
	
	
	public InfiCFAHeapAccessQuadVisitorBkwd(TObjectIntHashMap<Pair<Integer, jq_Field>> hfMap, boolean simFwd, boolean fullCG){
		javaLangObject = Program.g().getClass("java.lang.Object");
		assert (javaLangObject != null);
		this.domF = (DomF) ClassicProject.g().getTrgt("F");
		this.domM = (DomM) ClassicProject.g().getTrgt("M");
		this.domV = (DomV) ClassicProject.g().getTrgt("V");
		this.HFMap = hfMap;
		this.simulateFwd = simFwd;
		this.fullCG = fullCG;
	}
	
	@Override
	public void visitInvoke(Quad q) {
		//System.out.println("in Invoke");
		if (tgtMethod != null) {
			int idxCaller = domM.indexOf(q.getMethod()); 
			int idxTgt = domM.indexOf(tgtMethod);
			if (mBelongsToLib(idxCaller) && !mBelongsToLib(idxTgt)) {
				currHp.appCallbkPres = true;
		        System.out.println("CALLBACK: " + tgtMethod + ", CALLED BY: " + q.getMethod());
			}
		}
		if (!fullCG) {
			// Note that in absence of fullCG, conNewInstIM & objNewInstIM might need
			// to be separately recorded since these parts of the callgraph are not
			// constructed on the fly and could possibly differ in a test app.
			Operator op = q.getOperator();
			if(!(op instanceof InvokeStatic)) {
				ParamListOperand args = Invoke.getParamList(q);
				if (args.length() >= 1) {
					Register thisReg = args.get(0).getRegister();
					int thisRegNdx = domV.indexOf(thisReg);
					currHp.addToVSet(thisRegNdx);
					currHp.addToVSetC(thisRegNdx);
				}
			}
		}
	}
	
	
	@Override
	public void visitALoad(Quad q) {
		//System.out.println("in ALoad");
		Register dstR = ((RegisterOperand) ALoad.getDest(q)).getRegister();
		int dstRNdx = domV.indexOf(dstR);
		jq_Type dstRType = ALoad.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;
		
		//System.out.println("visitALoad");
		boolean inV;
		boolean inVC;
		if (simulateFwd) {
			inV = true;
			inVC = true;
		} else {
			inV = currHp.vSet.contains(dstRNdx);
			inVC = currHp.vSetC.contains(dstRNdx);
		}
		if (inV || inVC) {
			if (ALoad.getBase(q) instanceof RegisterOperand) {
				Register srcR = ((RegisterOperand) ALoad.getBase(q)).getRegister();
				BitSet basePointsTo = istate.envLocal.get(srcR);
	
				if(basePointsTo != null){
					for (int quad = basePointsTo.nextSetBit(0); quad >= 0; quad = basePointsTo.nextSetBit(quad+1)) {
						if(quad != 0){
							Pair<Integer, jq_Field> pair = new Pair<Integer, jq_Field>(quad, null);
							int prNdx = HFMap.get(pair);
							if (inV) currHp.addToHFSet(prNdx);
							if (inVC) currHp.addToHFSetC(prNdx);
						}
					}
				}
				if (inV) currHp.addToVSet(domV.indexOf(srcR));
				if (inVC) currHp.addToVSetC(domV.indexOf(srcR));
			}
		}
	}

	
	@Override
	public void visitGetstatic(Quad q) {
		//System.out.println("in GetStatic");
		Register dstR = ((RegisterOperand) Getstatic.getDest(q)).getRegister();
		int dstRNdx = domV.indexOf(dstR);
		jq_Type dstRType = Getstatic.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;

		//System.out.println("visitGetStatic");
		boolean inV;
		boolean inVC;
		if (simulateFwd) {
			inV = true;
			inVC = true;
		} else {
			inV = currHp.vSet.contains(dstRNdx);
			inVC = currHp.vSetC.contains(dstRNdx);
		}
		if (inV || inVC) {
			jq_Field srcF = Getstatic.getField(q).getField();
			if (inV) currHp.addToFSet(domF.indexOf(srcF));
			if (inVC) currHp.addToFSetC(domF.indexOf(srcF));
		}
	}

	@Override
	public void visitGetfield(Quad q) {
		//System.out.println("in GetField");
		Register dstR = ((RegisterOperand) Getfield.getDest(q)).getRegister();
		int dstRNdx = domV.indexOf(dstR);
		jq_Type dstRType = Getfield.getDest(q).getType();
		dstRType = dstRType != null ? dstRType : javaLangObject;
		
		//System.out.println("visitGetField");
		boolean inV;
		boolean inVC;
		if (simulateFwd) {
			inV = true;
			inVC = true;
		} else {
			inV = currHp.vSet.contains(dstRNdx);
			inVC = currHp.vSetC.contains(dstRNdx);
		}
		if (inV || inVC) {
			if (Getfield.getBase(q) instanceof RegisterOperand) {
				Register srcR = ((RegisterOperand) Getfield.getBase(q)).getRegister();
				jq_Field srcF = Getfield.getField(q).getField();
				
				BitSet basePointsTo = istate.envLocal.get(srcR);
				
				if(basePointsTo != null){
					for (int quad = basePointsTo.nextSetBit(0); quad >= 0; quad = basePointsTo.nextSetBit(quad+1)) {
						if(quad != 0){
							Pair<Integer, jq_Field> pair = new Pair<Integer, jq_Field>(quad, srcF);
							int prNdx = HFMap.get(pair);
							if (inV) currHp.addToHFSet(prNdx);
							if (inVC) currHp.addToHFSetC(prNdx);
						}
					}
				}
				if (inV) currHp.addToVSet(domV.indexOf(srcR));
				if (inVC) currHp.addToVSetC(domV.indexOf(srcR));
			}
		}
	}
	
	@Override
	public void visitMove(Quad q) {
		//System.out.println("in Move");
		Register dstR = Move.getDest(q).getRegister();
		int dstRNdx = domV.indexOf(dstR);
		boolean inV;
		boolean inVC;
		if (simulateFwd) {
			inV = true;
			inVC = true;
		} else {
			inV = currHp.vSet.contains(dstRNdx);
			inVC = currHp.vSetC.contains(dstRNdx);
		}
		if (inV || inVC) {
			if (Move.getSrc(q) instanceof RegisterOperand) {
				Register srcR = ((RegisterOperand) Move.getSrc(q)).getRegister();
				if (inV) currHp.addToVSet(domV.indexOf(srcR));
				if (inVC) currHp.addToVSetC(domV.indexOf(srcR));
			}
		}
	}
	
	@Override
	public void visitCheckCast(Quad q) {
		Register dstR = CheckCast.getDest(q).getRegister();
		int dstRNdx = domV.indexOf(dstR);
		boolean inV;
		boolean inVC;
		if (simulateFwd) {
			inV = true;
			inVC = true;
		} else {
			inV = currHp.vSet.contains(dstRNdx);
			inVC = currHp.vSetC.contains(dstRNdx);
		}
		if (inV || inVC) {
			if (CheckCast.getSrc(q) instanceof RegisterOperand) {
				Register srcR = ((RegisterOperand) CheckCast.getSrc(q)).getRegister();
				if (inV) currHp.addToVSet(domV.indexOf(srcR));
				if (inVC) currHp.addToVSetC(domV.indexOf(srcR));
			}
		}
	}
	
	@Override
	public void visitReturn(Quad q) {
		//System.out.println("in return");
		if (q.getOperator() instanceof THROW_A) return;
		if (Return.getSrc(q) instanceof RegisterOperand) {
			Register tgtR = ((RegisterOperand) (Return.getSrc(q))).getRegister();
			jq_Type tgtRtype = ((RegisterOperand) (Return.getSrc(q))).getType();
			if (!tgtRtype.isPrimitiveType()) {
				currHp.addToVSet(domV.indexOf(tgtR));
				//System.out.println("in return: ret val non-primitive type");
			} else {
				//System.out.println("in return: ret val primitive type");
			}
		}
	}
	
	@Override
	public void visitPhi(Quad q) {
		assert false : "Use no PHI version of quad code!";
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
