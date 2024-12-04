package chord.analyses.libanalysis.pointsto;

import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "invkV",
		consumes = { "M" },
		sign = "V0"
		)
public class RelInvkV extends ProgramRel {
	public void fill() {
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");

		int numM = domM.size();
		for (int mIdx = 0; mIdx < numM; mIdx++) {
			jq_Method m = domM.get(mIdx);
			if (m.isAbstract())
				continue;
			addInvkVars(m);
		}
	}

	public void addInvkVars(jq_Method m) {
		ControlFlowGraph cfg = m.getCFG();
		RegisterFactory rf = cfg.getRegisterFactory();
		jq_Type[] paramTypes = m.getParamTypes();
		int numArgs = paramTypes.length;
		for (int i = 0; i < numArgs; i++) {
			jq_Type t = paramTypes[i];
			if (t.isReferenceType()) {
				Register v = rf.get(i);
				add(v);
			}
		}
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if(op instanceof Invoke){
					process(q.getOp1(), q);
					process(q.getOp2(), q);
					process(q.getOp3(), q);
					process(q.getOp4(), q);
				}
			}
		}
	}

	private void process(Operand op, Quad q) {
		if (op instanceof RegisterOperand) {
			RegisterOperand ro = (RegisterOperand) op;
			Register v = ro.getRegister();
			jq_Type t = ro.getType();
			if (t == null || t.isReferenceType()) {
				add(v);
			}
		} else if (op instanceof ParamListOperand) {
			ParamListOperand ros = (ParamListOperand) op;
			int n = ros.length();
			for (int i = 0; i < n; i++) {
				RegisterOperand ro = ros.get(i);
				if (ro == null)
					continue;
				jq_Type t = ro.getType();
				if (t == null || t.isReferenceType()) {
					Register v = ro.getRegister(); 
					add(v);
				}
			}
		}
	}

}
