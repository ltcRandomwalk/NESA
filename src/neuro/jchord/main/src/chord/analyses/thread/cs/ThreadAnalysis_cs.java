package chord.analyses.thread.cs;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.program.Program;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;

/**
 * Static analysis computing reachable abstract threads.
 * <p>
 * Domain A is the domain of reachable abstract threads.
 * The 0th element does not denote any abstract thread; it is a placeholder for convenience.
 * The 1st element denotes the main thread.
 * The remaining elements denote threads explicitly created by calling the
 * {@code java.lang.Thread.start()} method; there is a separate element for each abstract
 * object to which the {@code this} argument of that method may point, as dictated by the
 * points-to analysis used.
 * <p>
 * Relation threadACM contains each tuple (a,c,m) such that abstract thread 'a' is started
 * at thread-root method 'm' in abstract context 'c'.  Thread-root method 'm' may be either:
 * <ul>
 *   <li>
 *     the main method, in which case 'c' is epsilon (element 0 in domain C), or
 *   </li>
 *   <li>
 *     the {@code java.lang.Thread.start()} method, in which case 'c' may be epsilon
 *     (if the call graph is built using 0-CFA) or it may be a chain of possibly
 *     interspersed call/allocation sites (if the call graph is built using k-CFA or
 *     k-object-sensitive analysis or a combination of the two).
 *   </li>
 * </ul>
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "thread-cs-java",
  //  consumes = { "nonMainThreadCM" },
    consumes = { "nonMainThreadCIM" },
    produces = { "AS", "threadACM" },
    namesOfSigns = { "threadACM" },
    signs = { "AS0,C0,M0:AS0_M0_C0" },
    namesOfTypes = { "AS" },
    types = { DomAS.class }
)
public class ThreadAnalysis_cs extends JavaAnalysis {
  /*  public void run() {
        ClassicProject project = ClassicProject.g();
        Program program = Program.g();
        DomC domC = (DomC) project.getTrgt("C");
        DomI domI = (DomI) project.getTrgt("I");
        DomM domM = (DomM) project.getTrgt("M");
        DomAS domAS = (DomAS) project.getTrgt("AS");
        domAS.clear();
        domAS.add(null);
        jq_Method mainMeth = program.getMainMethod();
        Ctxt epsilon = domC.get(0);
        domAS.add(new Pair<Ctxt, jq_Method>(epsilon, mainMeth));
        
        ProgramRel relThreadCM = (ProgramRel) project.getTrgt("nonMainThreadCM");
        relThreadCM.load();
        Iterable<Pair<Ctxt, jq_Method>> tuples = relThreadCM.getAry2ValTuples();
        for (Pair<Ctxt, jq_Method> p : tuples)
            domAS.add(p);
        relThreadCM.close();
        domAS.save();
        ProgramRel relThreadACM = (ProgramRel) project.getTrgt("threadACM");
        relThreadACM.zero();
        for (int aIdx = 1; aIdx < domAS.size(); aIdx++) {
        	Pair<Ctxt, jq_Method> cm = domAS.get(aIdx);
        	int c = domC.indexOf(cm.val0);
        	int m = domM.indexOf(cm.val1);
        	relThreadACM.add(aIdx, c, m);
        }
        relThreadACM.save();
    }
  */  
    
    public void run() {
        ClassicProject project = ClassicProject.g();
        Program program = Program.g();
        DomI domI = (DomI) project.getTrgt("I");
        DomM domM = (DomM) project.getTrgt("M");
        DomAS domAS = (DomAS) project.getTrgt("AS");
        DomC domC = (DomC) project.getTrgt("C");
        domAS.clear();
        domAS.add(null);
        jq_Method mainMeth = program.getMainMethod();
        Quad epsilon = domI.get(0); //DomI doesn't really have any epsilon quad.
        domAS.add(new Pair<Quad, jq_Method>(epsilon, mainMeth));
        
        ProgramRel relThreadCIM = (ProgramRel) project.getTrgt("nonMainThreadCIM");
        relThreadCIM.load();
        Iterable<Trio<Ctxt, Quad, jq_Method>> tuples = relThreadCIM.getAry3ValTuples();
        for (Trio<Ctxt, Quad, jq_Method> p : tuples)
            domAS.add(new Pair<Quad, jq_Method>(p.val1, p.val2));
        domAS.save();
        
        ProgramRel relThreadACM = (ProgramRel) project.getTrgt("threadACM");
        relThreadACM.zero();

    	relThreadACM.add(1, 0, 0);
    	for (Trio<Ctxt, Quad, jq_Method> p : tuples) {
            Pair<Quad, jq_Method> qm = new Pair<Quad, jq_Method>(p.val1, p.val2);
            int a = domAS.indexOf(qm);
            int c = domC.indexOf(p.val0);
            int m = domM.indexOf(p.val2);
            relThreadACM.add(a, c, m);
    	}
        relThreadACM.save();
        relThreadCIM.close();
    }
}

