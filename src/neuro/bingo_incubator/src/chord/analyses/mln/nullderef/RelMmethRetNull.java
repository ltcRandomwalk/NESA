package chord.analyses.mln.nullderef;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.visitors.IReturnInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,z) such the zth return variable of method m is null.
 *
 * @author Ravi Mangal
 */
@Chord(
    name = "MmethRetNull",
    sign = "M0,Z0:M0_Z0"
)
public class RelMmethRetNull extends ProgramRel implements IReturnInstVisitor {
    private static Integer ZERO = new Integer(0);
    private jq_Method ctnrMethod;

    @Override
    public void visit(jq_Class c) { }

    @Override
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }

    @Override
    public void visitReturnInst(Quad q) {
        Operand rx = Return.getSrc(q);
        // note: rx is null if this method returns void
        if (rx != null && !(rx instanceof RegisterOperand)) {
			if (rx instanceof AConstOperand) {
				jq_Reference tgtType = ((AConstOperand) rx).getType();
				if (tgtType == jq_NullType.NULL_TYPE) {
					add(ctnrMethod, ZERO);
				}
			}
        }
    }
}
