package chord.analyses.parsekintdata;

import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (v,t) such that variable v has type t
 */
@Chord(
    name = "VarTypeVT",
    sign = "KintV0,KintT0:KintV0_KintT0"
)
public class VarTypeVT extends ProgramRel {
   
	@Override
	public void fill() { }
}

