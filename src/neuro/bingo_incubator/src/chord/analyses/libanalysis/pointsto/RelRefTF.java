package chord.analyses.libanalysis.pointsto;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "refTF",
		sign = "T0,F0:F0_T0"
	)
	public class RelRefTF extends ProgramRel implements IFieldVisitor {
		private jq_Class ctnrClass;
		public void visit(jq_Class c) {
			ctnrClass = c;
		}
		public void visit(jq_Field f) {
			if (f.getType().isReferenceType()) {
				add(ctnrClass, f);
			}
		}
	}
