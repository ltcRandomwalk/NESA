package chord.analyses.incrsolver;

import joeq.Class.jq_Type;
import chord.program.Program;
import chord.project.Chord;
import chord.analyses.type.DomT;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

import java.lang.String;

/**
 * Relation containing all types defined in the application
 */
@Chord(
	    name = "T_app",
	    sign = "T0:T0"
	)
public class RelT_app extends ProgramRel {
	public void fill() {
		IndexSet<jq_Type> isectTypes = Program.g().getIsectTypes();
		String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
		
		DomT domT = (DomT) doms[0];
        add(0);
        for (int i = 1; i < domT.size(); i++) {
        	jq_Type typ = domT.get(i);
        	if (isectTypes == null) {
	        	if (!typ.getName().matches(defaultLibPrefix)) 
					add(i);
        	} else {
        		if (!isectTypes.contains(typ)) 
					add(i);
        	}
		}
    }
}
