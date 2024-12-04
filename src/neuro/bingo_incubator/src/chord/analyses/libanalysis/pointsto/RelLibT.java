package chord.analyses.libanalysis.pointsto;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import chord.analyses.type.DomT;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;
import chord.util.Utils;

/**
 * Relation containing each type t the prefix of whose name is contained in the value of system property chord.analysis.exclude.
 */
@Chord(name = "libT", sign = "T0:T0")
public class RelLibT extends ProgramRel {
    public void fill() {
        String excludedPkgs = System.getProperty("chord.analysis.exclude", "");
        DomT domT = (DomT) doms[0];
        Program program = Program.g();
        IndexSet<jq_Reference> classes = program.getClasses();
        String[] excludedPkgsArr = Utils.toArray(excludedPkgs);  // empty array if chord.analysis.exclude=""
        for (jq_Reference c : classes) {
            String cName = c.getName();
            if (Utils.prefixMatch(cName, excludedPkgsArr)) {
                add(c);
            }
        }
    }
}

