package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.type.DomT;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all types defined in the application
 */
@Chord(
	    name = "T_full",
	    sign = "T0:T0"
	)
public class RelT_full extends ProgramRel {
	public void fill() {
        DomT domT = (DomT) doms[0];
        add(0);
        for (int i = 1; i < domT.size(); i++) {
		add(i);
	}
    }
}
