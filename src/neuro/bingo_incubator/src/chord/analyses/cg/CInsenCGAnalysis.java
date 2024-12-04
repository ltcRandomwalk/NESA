package chord.analyses.cg;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.ProgramDom;
import chord.bddbddb.Rel.IntPairIterable;
import chord.util.tuple.integer.IntPair;
import chord.project.OutDirUtils;
import java.io.PrintWriter;

@Chord(name="cinsencg-java")
public class CInsenCGAnalysis extends JavaAnalysis {
	@Override
	public void run() {
		System.setProperty("chord.ctxt.kind", "cs");
		System.setProperty("chord.kcfa.k", "0");
		ClassicProject.g().runTask("cspa-kcfa-dlog");
		ClassicProject.g().runTask("reachMM-kcfa-init-dlog");	
	}
}

