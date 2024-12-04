	package chord.analyses.incrsolver;

	import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Reference;
import chord.program.Program;
import chord.project.Chord;
import chord.analyses.field.DomF;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

import java.lang.String;

	/**
	 * Relation containing all fields of application
	 */
	@Chord(
		    name = "F_app",
		    sign = "F0:F0"
		)
	public class RelF_app extends ProgramRel {
		public void fill() {
			IndexSet<jq_Reference> isectClasses = Program.g().getIsectClasses();
			String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
			DomF domF = (DomF) doms[0];
			add(0);
			for (int i = 1; i < domF.size(); i++) {
				jq_Field f = domF.get(i);
				jq_Class cl = f.getDeclaringClass();
				if (isectClasses == null) {
					if (!cl.getName().matches(defaultLibPrefix))
						add(i);
				} else {
					if (!isectClasses.contains(cl))
						add(i);
				}
			}
    }
}
