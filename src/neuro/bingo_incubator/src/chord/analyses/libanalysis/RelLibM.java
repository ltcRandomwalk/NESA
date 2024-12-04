package chord.analyses.libanalysis;

import joeq.Class.jq_Method;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;
import chord.util.Utils;

@Chord(name = "libM", sign = "M0:M0")
public class RelLibM extends ProgramRel {
    @Override
    public void fill() {
    	/* chord.analysis.exclude defines the code to be skipped,
    	 * while chord.check.exclude defines the queries to be skipped.
    	 * Hence, chord.check.exclude is used by the oracle too, to get
    	 * the set of tracked queries
    	 */
        String excludedPkgs = System.getProperty("chord.analysis.exclude", "");
        Program program = Program.g();
        IndexSet<jq_Method> methods = program.getMethods();
        String[] excludedPkgsArr = Utils.toArray(excludedPkgs);
        for (jq_Method m: methods) {
            String cName = m.getDeclaringClass().getName();
            if (cName.startsWith("java.lang.Object") && m.getName().toString().equals("<init>"))
		/*|| m == program.getThreadStartMethod())*/
                continue;
            if (Utils.prefixMatch(cName, excludedPkgsArr))
                add(m);
        }
    }
}

