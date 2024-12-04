package chord.analyses.incrsolver;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.program.Program;
import chord.project.Chord;
import chord.analyses.alloc.DomH;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

import java.lang.String;

/**
 * Relation containing all heap abstractions (allocation sites) of application
 */
@Chord(
	    name = "H_app",
	    sign = "H0:H0"
	)
public class RelH_app extends ProgramRel {
	public void fill() {
		IndexSet<jq_Method> isectMethods = Program.g().getIsectMethods();
		String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
		DomH domH = (DomH) doms[0];
        add(0);
        for (int i = 1; i < domH.size(); i++) {
			Quad q = (Quad) domH.get(i);
			jq_Method m = q.getMethod();
			jq_Class cl = m.getDeclaringClass();
			if (isectMethods == null) {
				if (!cl.getName().matches(defaultLibPrefix))
					add(i);
			} else {
				if (!isectMethods.contains(m))
					add(i);
			}
		}
    }
}
