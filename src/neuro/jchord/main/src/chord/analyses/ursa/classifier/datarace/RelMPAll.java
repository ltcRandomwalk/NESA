package chord.analyses.ursa.classifier.datarace;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import chord.analyses.point.DomP;
import chord.program.visitors.IInstVisitor;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,p) such that method m contains program point p.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MPAll",
    sign = "M0,P0:M0xP0"
)
public class RelMPAll extends ProgramRel implements IMethodVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        ControlFlowGraph cfg = m.getCFG();
        for (BasicBlock bb : cfg.reversePostOrder()) {
            int n = bb.size();
            if (n == 0)
                add(m,bb);
            else {
                for (Quad q : bb.getQuads())
                    add(m,q);
            }
        }
    }
}
