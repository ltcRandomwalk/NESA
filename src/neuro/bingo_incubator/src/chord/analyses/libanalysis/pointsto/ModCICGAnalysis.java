package chord.analyses.libanalysis.pointsto;

import chord.analyses.alias.CICG;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 * Context-insensitive call graph analysis.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
	name = "mod-cicg-java",
	consumes = { "rootM1", "reachableM1", "IM1", "MM1" }
)
public class ModCICGAnalysis extends JavaAnalysis {
	protected DomM domM;
	protected ProgramRel relRootM;
	protected ProgramRel relReachableM;
	protected ProgramRel relIM;
	protected ProgramRel relMM;
	protected CICG callGraph;
	public void run() {
		domM = (DomM) ClassicProject.g().getTrgt("M");
		relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM1");
		relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM1");
		relIM = (ProgramRel) ClassicProject.g().getTrgt("IM1");
		relMM = (ProgramRel) ClassicProject.g().getTrgt("MM1");
	}
	/**
	 * Provides the program's context-insensitive call graph.
	 * 
	 * @return	The program's context-insensitive call graph.
	 */
	public ICICG getCallGraph() {
		if (callGraph == null) {
			callGraph = new CICG(domM, relRootM, relReachableM, relIM, relMM);
		}
		return callGraph;
	}
	/**
	 * Frees relations used by this program analysis if they are in
	 * memory.
	 * <p>
	 * This method must be called after clients are done exercising
	 * the interface of this analysis.
	 */
	public void free() {
		if (callGraph != null)
			callGraph.free();
	}
}

