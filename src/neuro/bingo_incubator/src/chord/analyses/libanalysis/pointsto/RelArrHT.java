package chord.analyses.libanalysis.pointsto;

import joeq.Class.jq_Array;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.NewArray;

import chord.analyses.alloc.DomH;
import chord.analyses.invk.StubRewrite;
import chord.analyses.type.DomT;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

@Chord(
		name = "arrHT",
		sign = "H1,T1,T0:H1_T0_T1"
		)
public class RelArrHT extends ProgramRel {
	private DomH domH;
	private DomT domT;

	public void fill() {
		domH = (DomH) doms[0];
		domT = (DomT) doms[1];

		int numA = domH.getLastA() + 1;
		for (int hIdx = 1; hIdx < numA; hIdx++) {
			Quad h = (Quad) domH.get(hIdx);
			Operator op = h.getOperator();
			//TODO:Deal with NewMultiArray?
			if (op instanceof NewArray) {
				jq_Array t = (jq_Array) NewArray.getType(h).getType();
				jq_Type t1 = t.getElementType();
				add(hIdx,domT.indexOf(t),domT.indexOf(t1));
				while(t1.isArrayType()){
					t1 = ((jq_Array)t1).getElementType();
					add(hIdx,domT.indexOf(t),domT.indexOf(t1));
				}
			}
		
				
		}
	}
}

