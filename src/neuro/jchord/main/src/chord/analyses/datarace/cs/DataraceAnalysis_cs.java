package chord.analyses.datarace.cs;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.DomO;
import chord.analyses.alias.ICICG;
import chord.analyses.alias.ICSCG;
import chord.analyses.thread.ThrSenCICGAnalysis;
import chord.analyses.alloc.DomH;
import chord.analyses.thread.DomA;
import chord.analyses.thread.cs.DomAS;
import chord.analyses.thread.cs.ThrSenCSCGAnalysis;
import chord.bddbddb.Rel.RelView;
import chord.analyses.field.DomF;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.lock.DomL;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.SetUtils;
import chord.util.graph.IPathVisitor;
import chord.util.graph.ShortestPathBuilder;
import chord.util.tuple.object.Pair;

/**
 * Static datarace analysis.
 * <p>
 * Outputs relation 'datarace' containing each tuple (a1,e1,a2,e2) denoting a possible race between abstract threads
 * a1 and a2 executing accesses e1 and e2, respectively.
 * <p>
 * Recognized system properties:
 * <ul>
 *   <li>chord.datarace.exclude.init (default is true): Suppress checking races on accesses in constructors.</li>
 *   <li>chord.datarace.exclude.eqth (default is true): Suppress checking races between the same abstract thread.</li>
 *   <li>chord.datarace.exclude.escaping (default is false): Suppress the thread-escape analysis stage.</li>
 *   <li>chord.datarace.exclude.parallel (default is false): Suppress the may-happen-in-parallel analysis stage.</li>
 *   <li>chord.datarace.exclude.nongrded (default is false): Suppress the lockset analysis stage.</li>
 *   <li>chord.print.results (default is false): Print race results in HTML.</li>
 * </ul>
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name="datarace-cs-java")
public class DataraceAnalysis_cs extends JavaAnalysis {
    private DomM domM;
    private DomI domI;
    private DomF domF;
    private DomE domE;
    private DomAS domAS;
    private DomH domH;
    private DomL domL;
    private ThrSenCSCGAnalysis thrSenCSCGAnalysis;

    private void init() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        domI = (DomI) ClassicProject.g().getTrgt("I");
        domF = (DomF) ClassicProject.g().getTrgt("F");
        domE = (DomE) ClassicProject.g().getTrgt("E");
        domAS = (DomAS) ClassicProject.g().getTrgt("AS");
        domH = (DomH) ClassicProject.g().getTrgt("H");
        domL = (DomL) ClassicProject.g().getTrgt("L");
        thrSenCSCGAnalysis = (ThrSenCSCGAnalysis) ClassicProject.g().getTrgt("thrsen-cscg-java");
    }

    public void run() {
        boolean excludeParallel = Boolean.getBoolean("chord.datarace.exclude.parallel");
        boolean excludeEscaping = Boolean.getBoolean("chord.datarace.exclude.escaping");
        boolean excludeNongrded = Boolean.getBoolean("chord.datarace.exclude.nongrded");

        init();

        if (excludeParallel)
            ClassicProject.g().runTask("datarace-parallel-exclude-cs-dlog");
        else
            ClassicProject.g().runTask("datarace-parallel-include-cs-dlog");
        if (excludeEscaping)
            ClassicProject.g().runTask("datarace-escaping-exclude-cs-dlog");
        else
            ClassicProject.g().runTask("datarace-escaping-include-cs-dlog");
        if (excludeNongrded)
            ClassicProject.g().runTask("datarace-nongrded-exclude-cs-dlog");
        else
            ClassicProject.g().runTask("datarace-nongrded-include-cs-dlog");
        
      //  ClassicProject.g().runTask("datarace-cs-init-dlog");
      //  ClassicProject.g().runTask("datarace-cs-noneg-dlog");
        ClassicProject.g().runTask("datarace-cs-dlog");
        
        if (Config.printResults)
            printResults();
    }

    private void printResults() {}
}
