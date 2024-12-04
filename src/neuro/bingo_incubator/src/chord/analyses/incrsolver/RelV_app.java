package chord.analyses.incrsolver;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;
import chord.project.Chord;
import chord.analyses.var.DomV;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

import java.lang.String;

/**
 * Relation containing all variables of application
 */
@Chord(
	    name = "V_app",
	    sign = "V0:V0"
	)
public class RelV_app extends ProgramRel {
	public void fill() {
		String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
		IndexSet<jq_Method> isectMethods = Program.g().getIsectMethods();
        DomV domV = (DomV) doms[0];
        for (int i = 0; i < domV.size(); i++) {
			Register v = domV.get(i);
			jq_Method m = domV.getMethod(v);
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
