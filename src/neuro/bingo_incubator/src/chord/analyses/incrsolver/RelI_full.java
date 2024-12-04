package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.invk.DomI;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all invoke instructions of application
 */
@Chord(
	    name = "I_full",
	    sign = "I0:I0"
	)
public class RelI_full extends ProgramRel {
	public void fill() {
        DomI domI = (DomI) doms[0];
        for (int i = 0; i < domI.size(); i++) {
		add(i);
	}
    }
}
