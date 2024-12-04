package chord.analyses.typestate.tdbu;

import java.util.List;
import java.util.Map;

import chord.project.Chord;
import chord.project.analyses.JavaAnalysis;

@Chord(name="typestate-dfa-java")
public class TSDFAAnalysis extends JavaAnalysis {
	private Map<String,List<String>> traceMap;
	private String FOLDERLIST = "chord.typestate.dfaFolder";

	private void constructTraceMap(){
		
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
	}

}
