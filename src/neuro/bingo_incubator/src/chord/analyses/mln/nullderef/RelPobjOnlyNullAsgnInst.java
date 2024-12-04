package chord.analyses.mln.nullderef;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.visitors.ICastInstVisitor;
import chord.program.visitors.IMoveInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,v) such that the statement at program
 * point p is an assignment statement where null is assigned to local
 *  reference variable v.
 * 
 * @author Ravi Mangal
 */
@Chord(name = "PobjOnlyNullAsgnInst", sign = "P0,V0:P0_V0")
public class RelPobjOnlyNullAsgnInst extends ProgramRel implements IMoveInstVisitor, ICastInstVisitor {
	public void visit(jq_Class c) {
	}

	public void visit(jq_Method m) {
	}

	public void visitMoveInst(Quad q) {
		Operand rx = Move.getSrc(q);
		if (!(rx instanceof RegisterOperand)) {
			if (rx instanceof AConstOperand) {
				jq_Reference srcType = ((AConstOperand) rx).getType();
				if (srcType == jq_NullType.NULL_TYPE) {
					RegisterOperand lo = Move.getDest(q);
					if (lo.getType().isReferenceType()) {
						Register l = lo.getRegister();
						add(q, l);
					}
				}
			}
		}
	}
	
	public void visitCastInst(Quad q) {
        Operand rx = CheckCast.getSrc(q);
        if (!(rx instanceof RegisterOperand)) {
			if (rx instanceof AConstOperand) {
				jq_Reference srcType = ((AConstOperand) rx).getType();
				if (srcType == jq_NullType.NULL_TYPE) {
					RegisterOperand lo = CheckCast.getDest(q);
					if (lo.getType().isReferenceType()) {
						Register l = lo.getRegister();
						add(q, l);
					}
				}
			}
        }
    }
}
