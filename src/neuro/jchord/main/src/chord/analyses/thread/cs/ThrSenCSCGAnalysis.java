package chord.analyses.thread.cs;

import chord.analyses.method.DomM;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CSCGAnalysis;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

/**
 * Call graph analysis producing a thread-sensitive and context-insensitive
 * call graph of the program.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "thrsen-cscg-java",
    consumes = { "thrSenRootCM", "thrSenReachableCM", "thrSenCICM", "thrSenCMCM" }
)
public class ThrSenCSCGAnalysis extends CSCGAnalysis {
    public void run() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        relRootCM = (ProgramRel) ClassicProject.g().getTrgt("thrSenRootCM");
        relReachableCM = (ProgramRel) ClassicProject.g().getTrgt("thrSenReachableCM");
        relCICM = (ProgramRel) ClassicProject.g().getTrgt("thrSenCICM");
        relCMCM = (ProgramRel) ClassicProject.g().getTrgt("thrSenCMCM");
    }
}
