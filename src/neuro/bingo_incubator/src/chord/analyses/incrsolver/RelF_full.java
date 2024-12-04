package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.field.DomF;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all fields of application
 */
@Chord(
	    name = "F_full",
	    sign = "F0:F0"
	)
public class RelF_full extends ProgramRel {
	public void fill() {
		DomF domF = (DomF) doms[0];
		add(0);
		for (int i = 1; i < domF.size(); i++) {
			add(i);
		}
    }
}
