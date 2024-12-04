package chord.analyses.libanalysis.pointsto;


import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "statRefF",
		sign = "F0"
	)
	public class RelStatRefF extends ProgramRel implements IFieldVisitor {
		
		public void visit(jq_Class c) { }
		public void visit(jq_Field f) {
			if (f.getType().isReferenceType() && f.isStatic()) {
				add(f);
			}

		}
		
	}
