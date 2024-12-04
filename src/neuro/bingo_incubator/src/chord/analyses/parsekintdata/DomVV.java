package chord.analyses.parsekintdata;

import chord.project.Chord;
import chord.project.analyses.ProgramDom;

/**
 * Domain of local variables represented as fully-qualified strings: <filename:methodname:varname>
 */
@Chord(name = "KintV")
public class DomVV extends ProgramDom<String> {
    
	@Override
	public void fill() { }
}
