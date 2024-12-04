package chord.analyses.incrsolver;

import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all methods of application
 */
@Chord(
	    name = "M_full",
	    sign = "M0:M0"
	)
public class RelM_full extends ProgramRel {
	public void fill() {
        DomM domM = (DomM) doms[0];
        add(0);  // add Main method
		for (int i = 1; i < domM.size(); i++) { 
			add(i);
		}
    }
}
