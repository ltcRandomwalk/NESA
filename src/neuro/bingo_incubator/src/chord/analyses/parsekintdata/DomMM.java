 package chord.analyses.parsekintdata;

import chord.project.Chord;
import chord.project.analyses.ProgramDom;

/**
 * Domain of methods
 * This domain contains all the methods appearing in the kint bug reports.
 * The methods are stored as strings which give their fully-qualified names.
 */

@Chord(name = "KintM")
public class DomMM extends ProgramDom<String> {
 
	@Override
	public void fill() { }
}
