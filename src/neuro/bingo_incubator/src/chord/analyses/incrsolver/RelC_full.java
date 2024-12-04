package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.alias.DomC;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all contexts of application
 */
@Chord(
	    name = "C_full",
	    sign = "C0:C0"
	)
public class RelC_full extends ProgramRel {
	public void fill() {
        DomC domC = (DomC) doms[0];
        add(0);
    	for (int i = 1; i < domC.size(); i++) {
		add(i);
	}
    }
}
