package chord.analyses.incrsolver;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

import java.lang.String;

/**
 * Relation containing all methods of application
 */
@Chord(
	    name = "M_rest",
	    sign = "M0:M0"
	)
public class RelM_rest extends ProgramRel {
	private String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";

	public void fill() {
        DomM domM = (DomM) doms[0];
        String libstr = System.getProperty("chord.superopt.libprefix");
        String libraryPrefix;
        if (libstr != null)
            libraryPrefix = "(" + libstr + ")" + ".*";
        else
            libraryPrefix  = defaultLibPrefix;
        System.out.println ("Using library prefix: " + libraryPrefix);
        IndexSet<jq_Method> isectMethods = Program.g().getIsectMethods();
        add(0);  // add Main method
		for (int i = 1; i < domM.size(); i++) { 
			jq_Method m = domM.get(i);
                        if (m == null) continue;
			jq_Class cl = m.getDeclaringClass();
			String clName = cl.getName();
			if (isectMethods == null) {
				if (!clName.matches(libraryPrefix))
					add(i);
			} else {
				if (!isectMethods.contains(m))
					add(i);
			}
		}
    }
}
