package chord.analyses.mln.kobj;

import chord.analyses.invk.DomI;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;


@Chord(
    name = "sinkV",
    sign = "V0:V0",
    consumes = {"checkIncludedI"}
)
public class RelSinkV extends ProgramRel {
	@Override
	public void fill() {
		ProgramRel relCheckIncludedI = (ProgramRel) ClassicProject.g().getTrgt("checkIncludedI");
		relCheckIncludedI.load();
		
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		ClassicProject.g().runTask(domI);
		for (int idx = 0; idx < domI.size() ; idx++) {
			Quad q = (Quad) domI.get(idx);
			jq_Method m = Invoke.getMethod(q).getMethod();
			if (m.getName().toString().equals("sink") && relCheckIncludedI.contains(idx)) {
				ParamListOperand args = Invoke.getParamList(q);
				for (int i = 0; i < args.length(); i++) {
	                Register actualReg = args.get(i).getRegister();
	                add(actualReg);
	            }
			}
		}
		relCheckIncludedI.close();
	}
}
