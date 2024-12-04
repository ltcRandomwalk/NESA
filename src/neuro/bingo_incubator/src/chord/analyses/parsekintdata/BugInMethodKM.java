package chord.analyses.parsekintdata;

import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (k,m) such that the kth bug is in method m
 */
@Chord(
    name = "BugInMethodKM",
    sign = "KintK0,KintM0:KintK0_KintM0"
)
public class BugInMethodKM extends ProgramRel {
   
	@Override
	public void fill() { }
}

