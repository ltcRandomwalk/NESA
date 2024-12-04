package chord.analyses.parsekintdata;

import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,k) such that method m has a total number of parameters k.
 */
@Chord(
    name = "MethodTotalArgsMK",
    sign = "KintM0,KintK0:KintM0_KintK0"
)
public class MethodTotalArgsMK extends ProgramRel {
   
	@Override
	public void fill() { }
}

