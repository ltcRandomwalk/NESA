package chord.analyses.escape.cs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.heapacc.DomE;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (e1,e2) such that heap access point e1 is
 * downstream of e2 (ignoring back edges) and both access the same variable, or
 * copied variables.
 *
 * @author Ravi Mangal
 */
@Chord(name = "OrderedEEBase", sign = "E0,E1:E0xE1", consumes = { "EV" })
public class RelOrderedEE extends ProgramRel implements IMethodVisitor {
	private DomE domE;
	private Map<Quad, Map<Register, Set<Quad>>> lastAccessMap;
	private Map<Quad, Map<Register, Set<Register>>> equivalenceMap;
	private ProgramRel relEV;

	@Override
	public void init() {
		domE = (DomE) doms[0];
		lastAccessMap = new HashMap<Quad, Map<Register, Set<Quad>>>();
		equivalenceMap = new HashMap<Quad, Map<Register, Set<Register>>>();
		relEV = (ProgramRel) ClassicProject.g().getTrgt("EV");
		relEV.load();
	}

	@Override
	public void visit(jq_Class c) {
	}

	@Override
	public void visit(jq_Method m) {
		lastAccessMap.clear();
		equivalenceMap.clear();
		if (m.isAbstract())
			return;
		ControlFlowGraph cfg = m.getCFG();
		for (BasicBlock bq : cfg.reversePostOrder()) {
			int n = bq.size();
			if (n == 0)
				continue;

			Quad o = bq.getQuad(0);
			Map<Register, Set<Quad>> joinMappedP = new HashMap<Register, Set<Quad>>();
			Map<Register, Set<Register>> joinMappedV = new HashMap<Register, Set<Register>>();

			for (BasicBlock bp : bq.getPredecessors()) {
				int l = bp.size();
				if (l == 0)
					continue;
				Quad p = bp.getQuad(l - 1);
				Map<Register, Set<Quad>> lastAccess = lastAccessMap.get(p);
				Map<Register, Set<Register>> equivalence = equivalenceMap.get(p);

				// null data structures means that this is a back edge
				if (lastAccess == null && equivalence == null)
					continue;

				for (Register v : lastAccess.keySet()) {
					Set<Quad> accesses1 = lastAccess.get(v);
					Set<Quad> accesses2 = joinMappedP.get(v);
					if (accesses2 == null) {
						accesses2 = new HashSet<Quad>();
						joinMappedP.put(v, accesses2);
					}
					accesses2.addAll(accesses1);
				}

				for (Register v : equivalence.keySet()) {
					Set<Register> equiReg1 = equivalence.get(v);
					Set<Register> equiReg2 = joinMappedV.get(v);
					if (equiReg2 == null) {
						equiReg2 = new HashSet<Register>();
						joinMappedV.put(v, equiReg2);
					}
					equiReg2.addAll(equiReg1);
				}

			}
			updateState(o, joinMappedP, joinMappedV);

			for (int i = 1; i < n; ++i) {
				Quad q = bq.getQuad(i);
				Quad p = bq.getQuad(i - 1);
				updateState(q, lastAccessMap.get(p), equivalenceMap.get(p));
			}
		}
	}

	private void updateState(Quad q, Map<Register, Set<Quad>> lastAccess, Map<Register, Set<Register>> equivalence) {
		int domEIdx = domE.indexOf(q);
		if (domEIdx != -1) {
			Register v = getRegister(q);
			if (v != null) {
				Map<Register, Set<Quad>> newMappedP = new HashMap<Register, Set<Quad>>(lastAccess);
				Map<Register, Set<Register>> newMappedV = new HashMap<Register, Set<Register>>(equivalence);

				// Handle this register's previous access
				Set<Quad> mappedEs = lastAccess.get(v);
				if (mappedEs != null) {
					for (Quad mappedE : mappedEs) {
						int e1Idx = domE.indexOf(q);
						int e2Idx = domE.indexOf(mappedE);
						add(e1Idx, e2Idx);
					}
				}
				Set<Quad> newMappedE = new HashSet<Quad>();
				newMappedE.add(q);
				newMappedP.put(v, newMappedE);
				lastAccessMap.put(q, newMappedP);

				// Handle equivalent register's previous access
				Set<Register> equiRegs = equivalence.get(v);
				if (equiRegs != null) {
					for (Register equiReg : equiRegs) {
						Set<Quad> equiMappedEs = lastAccess.get(equiReg);
						if (equiMappedEs != null) {
							for (Quad equiMappedE : equiMappedEs) {
								int e1Idx = domE.indexOf(q);
								int e2Idx = domE.indexOf(equiMappedE);
								add(e1Idx, e2Idx);
							}
						}
					}
				}
				newMappedV.remove(v);
				equivalenceMap.put(q, newMappedV);
			} else {
				lastAccessMap.put(q, lastAccess);
				equivalenceMap.put(q, equivalence);
			}
		} else if (isMoveInst(q)) {
			Pair<Register, Set<Register>> srcDstPair = getEquivalentRegisters(q);
			if (srcDstPair.val0 != null) {
				// Set<Register> equivalentRegs =
				// equivalence.get(srcDstPair.left);
				// assert (equivalentRegs == null);

				Map<Register, Set<Register>> newMappedV = new HashMap<Register, Set<Register>>(equivalence);
				newMappedV.put(srcDstPair.val0, srcDstPair.val1);
				equivalenceMap.put(q, newMappedV);
			} else {
				equivalenceMap.put(q, equivalence);
			}
			lastAccessMap.put(q, lastAccess);
		} else {
			lastAccessMap.put(q, lastAccess);
			equivalenceMap.put(q, equivalence);
		}
	}

	private boolean isMoveInst(Quad q) {
		Operator op = q.getOperator();
		return ((op instanceof Move) || (op instanceof CheckCast) || (op instanceof Phi));
	}

	private Pair<Register, Set<Register>> getEquivalentRegisters(Quad q) {
		Operator op = q.getOperator();
		Set<Register> equivalentRegs = new HashSet<Register>();
		Register src = null;
		if (op instanceof Move) {
			Operand rx = Move.getSrc(q);
			if (rx instanceof RegisterOperand) {
				RegisterOperand ro = (RegisterOperand) rx;
				if (ro.getType().isReferenceType()) {
					Register r = ro.getRegister();
					RegisterOperand lo = Move.getDest(q);
					src = lo.getRegister();
					equivalentRegs.add(r);
				}
			}
		}
		if ((op instanceof Phi)) {
			RegisterOperand lo = Phi.getDest(q);
			jq_Type t = lo.getType();
			if (t.isReferenceType()) {
				src = lo.getRegister();
				ParamListOperand ros = Phi.getSrcs(q);
				int n = ros.length();
				for (int i = 0; i < n; i++) {
					RegisterOperand ro = ros.get(i);
					if (ro != null) {
						Register r = ro.getRegister();
						equivalentRegs.add(r);
					}
				}
			}
		}
		if ((op instanceof CheckCast)) {
			Operand rx = CheckCast.getSrc(q);
			if (rx instanceof RegisterOperand) {
				RegisterOperand ro = (RegisterOperand) rx;
				if (ro.getType().isReferenceType()) {
					Register r = ro.getRegister();
					RegisterOperand lo = CheckCast.getDest(q);
					src = lo.getRegister();
					equivalentRegs.add(r);
				}
			}
		}
		return new Pair<Register, Set<Register>>(src, equivalentRegs);
	}

	private Register getRegister(Quad q) {
		Operator op = q.getOperator();
		RegisterOperand bo;
		if (op instanceof ALoad) {
			bo = (RegisterOperand) ALoad.getBase(q);
		} else if (op instanceof Getfield) {
			bo = (RegisterOperand) Getfield.getBase(q);
		} else if (op instanceof AStore) {
			bo = (RegisterOperand) AStore.getBase(q);
		} else if (op instanceof Putfield) {
			bo = (RegisterOperand) Putfield.getBase(q);
		} else
			bo = null;
		if (bo != null) {
			Register b = bo.getRegister();
			return b;
		}
		return null;
	}
}
