package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.var.DomV;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all variables of application
 */
@Chord(
	    name = "V_full",
	    sign = "V0:V0"
	)
public class RelV_full extends ProgramRel {
	public void fill() {
        DomV domV = (DomV) doms[0];
        for (int i = 0; i < domV.size(); i++) {
		add(i);
	}
    }
}
