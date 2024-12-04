package chord.analyses.incrsolver;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.Ctxt;
import chord.project.Chord;
import chord.analyses.alias.DomC;
import chord.project.analyses.ProgramRel;

import java.lang.String;

/**
 * Relation containing all contexts of application
 */
@Chord(
	    name = "C_app",
	    sign = "C0:C0"
	)
public class RelC_app extends ProgramRel {
	public void fill() {
		String defaultLibPrefix = "(CustomLib|java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
		DomC domC = (DomC) doms[0];
        add(0);
    	for (int i = 1; i < domC.size(); i++) {
			Ctxt ctxt = (Ctxt) domC.get(i);
			int cnt = ctxt.length();
			boolean valid;
			if (cnt > 0) {
				Quad[] qArr = ctxt.getElems();
				valid = false;
				for (int j = 0; j < cnt; j++) {
					jq_Class cl = qArr[j].getMethod().getDeclaringClass();
					if (!cl.getName().matches(defaultLibPrefix)) {
						valid = true;
						break;
					}
				}
			} else {
				valid = true;
			}
			if (valid)
				add(i);
		}
    }
}
