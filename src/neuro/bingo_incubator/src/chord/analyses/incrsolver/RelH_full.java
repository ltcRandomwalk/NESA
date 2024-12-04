package chord.analyses.incrsolver;

import chord.project.Chord;
import chord.analyses.alloc.DomH;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all heap abstractions (allocation sites) of application
 */
@Chord(
	    name = "H_full",
	    sign = "H0:H0"
	)
public class RelH_full extends ProgramRel {
	public void fill() {
        DomH domH = (DomH) doms[0];
        add(0);
        for (int i = 1; i < domH.size(); i++) {
		add(i);
	}
    }
}
