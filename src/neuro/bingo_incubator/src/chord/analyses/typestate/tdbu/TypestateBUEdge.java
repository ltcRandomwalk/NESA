package chord.analyses.typestate.tdbu;

import java.util.Set;
import java.util.SortedSet;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.typestate.AbstractState;
import chord.analyses.typestate.AccessPath;
import chord.analyses.typestate.Edge;
import chord.analyses.typestate.EdgeKind;
import chord.analyses.typestate.GlobalAccessPath;
import chord.analyses.typestate.RegisterAccessPath;
import chord.program.Loc;
import chord.project.analyses.tdbu.BUEdge;
import chord.project.analyses.tdbu.Constraint;
import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

/**
 * HARD CODED for field depth = 0
 * 
 * The path edge of bottom-up must-alias analysis. Since the initial state of a
 * path edge would always be some place holder, there's no need to implement an
 * independent node class
 * 
 * The BU in the implementation only talks about full edges, because null
 * edges(dump edges we create for object allocation) only survive locally inside
 * a function and will become full edges after existing the function. For this
 * part, BU and TD look the same, so I don't see any need to ship the work from
 * TD to BU. As a result, BU actually only talks about the mustset of the
 * incoming full edge.
 * 
 * To check the current symbolic state, the priority is always: 1. unionSet 2.
 * minusSet 3. constraint
 * 
 * @author xin
 * 
 */
public class TypestateBUEdge implements BUEdge<Edge, Edge>, Cloneable {

	private Set<Variable> killSet;
	private Set<Variable> genSet;

	// The symbolic edge type
	// private boolean edgePlaceHolder;
	// private EdgeKind kind;

	// The constraint
	private AliasConstraint constraint;

	private Register ret;

	private Set<Pair<Loc, BUEdge<Edge, Edge>>> instStates;

	private int hashCode;
	private boolean isHashed = false;

	public TypestateBUEdge() {
		// pathPlaceHolder = true;
		killSet = new ArraySet<Variable>();
		genSet = new ArraySet<Variable>();

		// edgePlaceHolder = true;

		constraint = new AliasConstraint(true);

		instStates = new ArraySet<Pair<Loc, BUEdge<Edge, Edge>>>();
	}

	public Register getRet() {
		return ret;
	}

	public void setRet(Register r) {
		this.ret = r;
	}

	/**
	 * Apply the current BUEdge to a TD edge, It's very simple: 1. keep the
	 * access paths not in minusSet;2. add the access paths that can be
	 * generated through union set
	 */
	@Override
	public Edge applyInvoke(Quad q, Edge clrPE, Loc loc, jq_Method tgtM) {
		if (clrPE.dstNode == null)
			return null;

		// Replace the actual parameters with formal parameters
		ArraySet<AccessPath> clrMS = AliasUtilities.handleParametersTD(
				clrPE.dstNode.ms, q, tgtM);
		AbstractState tranDstNode = new AbstractState(clrPE.dstNode.ts, clrMS,
				clrPE.dstNode.canReturn, clrPE.dstNode.may);
		Edge exitEdge = apply(new Edge(clrPE.srcNode, tranDstNode, clrPE.type,
				clrPE.h), loc);
		if (exitEdge == null)
			return null;
//		Register tgtRetReg = (Invoke.getDest(q) != null) ? Invoke.getDest(q)
//				.getRegister() : null;
		ArraySet<AccessPath> newMS = exitEdge.dstNode.ms;
//		if (this.ret != null && tgtRetReg != null)
//			newMS = AliasUtilities.handleMoveTD(newMS, ret, tgtRetReg);
		AbstractState newDstNode = new AbstractState(exitEdge.dstNode.ts,
				newMS, exitEdge.dstNode.canReturn, exitEdge.dstNode.may);
		return new Edge(clrPE.srcNode, newDstNode, clrPE.type, clrPE.h);
	}

	@Override
	public Edge apply(Edge tdpe, Loc loc) {
		ArraySet<AccessPath> clrMS = tdpe.dstNode.ms;
		if (!constraint.statisfy(clrMS))
			return null;

		ArraySet<AccessPath> newMS = new ArraySet<AccessPath>();

		// if(pathPlaceHolder){
		for (Variable v : genSet) {
					newMS.add(v.createAccessPath());
		}
		for (AccessPath ap : clrMS) {
			Variable tempV = null;
			if (ap instanceof RegisterAccessPath) {
				RegisterAccessPath rap = (RegisterAccessPath) ap;
				tempV = new Variable(rap.var);
			}
			if (ap instanceof GlobalAccessPath) {
				GlobalAccessPath gap = (GlobalAccessPath) ap;
				tempV = new Variable(gap.global);
			}
			if (!killSet.contains(tempV))
				newMS.add(ap);
		}
		AbstractState newDstNode = new AbstractState(tdpe.dstNode.ts, newMS,
				tdpe.dstNode.canReturn, tdpe.dstNode.may);
		return new Edge(tdpe.srcNode, newDstNode, tdpe.type, tdpe.h);
	}

	@Override
	public Set<BUEdge<Edge, Edge>> applyInvoke(Quad q,
			BUEdge<Edge, Edge> clrEdge, Loc loc, jq_Method tgtM, SortedSet<Constraint> trackedCases, int bulimit) {
		if (!(clrEdge instanceof TypestateBUEdge))
			throw new RuntimeException("Impatiable BU edge type!");
//		SortedSet<Constraint> tcCopy = new SortedArraySet<Constraint>(trackedCases);
		ParamListOperand args = Invoke.getParamList(q);
		RegisterFactory rf = tgtM.getCFG().getRegisterFactory();
		Set<BUEdge<Edge, Edge>> curEdges = new ArraySet<BUEdge<Edge, Edge>>();
		Set<BUEdge<Edge, Edge>> nextEdges = new ArraySet<BUEdge<Edge, Edge>>();
		nextEdges.add(clrEdge);
		for (int i = 0; i < args.length(); i++) {
			curEdges = nextEdges;
			nextEdges = new ArraySet<BUEdge<Edge, Edge>>();
			Register actualReg = args.get(i).getRegister();
			Register formalReg = rf.get(i);
			Set<TypestateBUEdge> paramTrans = constructMove(new Variable(
					actualReg), new Variable(formalReg));
			for (TypestateBUEdge pt : paramTrans) {
				for (BUEdge<Edge, Edge> ce : curEdges) {
					BUEdge<Edge, Edge> temp = pt.apply(ce, loc);
					if(temp!=null)
						temp = AliasUtilities.checkBUPE((TypestateBUEdge)temp, q.getMethod());
					if (temp != null && !temp.getConstraint().isFalse())
						nextEdges.add(temp);
				}
			}
//			nextEdges = fitCases(nextEdges,tcCopy,bulimit);
		}
		curEdges = nextEdges;
		nextEdges = new ArraySet<BUEdge<Edge, Edge>>();
		for (BUEdge<Edge, Edge> ce : curEdges) {
			BUEdge<Edge, Edge> temp = this.apply(ce, loc);
			if (temp != null && !temp.getConstraint().isFalse())
				nextEdges.add(temp);
		}
		return nextEdges;
	}

	private Set<BUEdge<Edge,Edge>> fitCases(Set<BUEdge<Edge,Edge>> edgesToAdd, SortedSet<Constraint> trackedCases,int bulimit){
		Set<BUEdge<Edge,Edge>> ret = new ArraySet<BUEdge<Edge,Edge>>();
		Set<Constraint> newCases = new ArraySet<Constraint>();
		Set<Constraint> casesToSplit = new ArraySet<Constraint>();
		Set<BUEdge<Edge,Edge>> tempSet = new ArraySet<BUEdge<Edge,Edge>>();
		for (Constraint dnf : trackedCases) {
			for (BUEdge<Edge,Edge> bupe : edgesToAdd) {
				if(bupe == null)
					continue;
				Constraint nc = bupe.getConstraint().intersect(dnf);
				if (nc.isFalse())
					continue;
				newCases.add(nc);
				casesToSplit.add(dnf);
				bupe = bupe.changeConstraint(nc);
				tempSet.add(bupe);
			}
		}
		trackedCases.removeAll(casesToSplit);
		trackedCases.addAll(newCases);
		while (trackedCases.size() > bulimit) {
			trackedCases.remove(trackedCases.first());
		}
		for(BUEdge<Edge,Edge> bupe: tempSet)
			if(trackedCases.contains(bupe.getConstraint()))
				ret.add(bupe);
		return ret;
	}
	
	/**
	 * Apply current BUEdge to another BUEdge. Pay special attention that
	 * current BUEdge is always talking about the symbolic state of the
	 * parameter, not the placeholder
	 */
	@Override
	public BUEdge<Edge, Edge> apply(BUEdge<Edge, Edge> clrEdge, Loc loc) {
		if (!(clrEdge instanceof TypestateBUEdge))
			throw new RuntimeException("Incompatible BU edge type!");
		TypestateBUEdge that = (TypestateBUEdge) clrEdge;
		AliasConstraint newCons = this.constraint.statisfy(that.killSet,
				that.genSet, that.constraint);
		if (newCons.isFalse())
			return null;
		TypestateBUEdge ret = new TypestateBUEdge();
		ret.constraint = newCons;
		for (Variable v : this.killSet) {
			if (newCons.getNotInVSet().contains(v))
				continue;
			ret.killSet.add(v);
		}
		ret.killSet.addAll(that.killSet);
		ret.genSet.addAll(this.genSet);
		for (Variable v : that.genSet) {// Add the gen set
														// which are not
														// killed
			if (this.killSet.contains(v))
				continue;
			ret.genSet.add(v);
		}
		return ret;
	}

	@Override
	public boolean satisfy(Edge tdse) {
		if (tdse.type == EdgeKind.FULL) {
			return constraint.statisfy(tdse.srcNode.ms);
		} else
			return false;
	}

	@Override
	public int canMerge(BUEdge other) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean mergeWith(BUEdge other) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Constraint getConstraint() {
		return constraint;
	}

	@Override
	public BUEdge<Edge, Edge> changeConstraint(Constraint cons) {
		TypestateBUEdge ret = this.clone();
		ret.constraint = (AliasConstraint) cons;
		return ret;
	}

	@Override
	public TypestateBUEdge clone() {
		TypestateBUEdge ret = new TypestateBUEdge();
		ret.constraint = this.constraint.clone();
		ret.killSet = new ArraySet<Variable>(this.killSet);
		ret.genSet = new ArraySet<Variable>(this.genSet);
		if (this.instStates != null)
			ret.instStates = new ArraySet<Pair<Loc, BUEdge<Edge, Edge>>>(
					this.instStates);
		return ret;
	}

	public Set<Variable> getKillSet() {
		return killSet;
	}

	public void setKillSet(Set<Variable> minusSet) {
		this.killSet = minusSet;
	}

	public Set<Variable> getGenSet() {
		return genSet;
	}

	public void setGenSet(Set<Variable> unionSet) {
		this.genSet = unionSet;
	}

	public void setConstraint(AliasConstraint constraint) {
		this.constraint = constraint;
	}

	/*
	 * Since we already implemented a general form to apply BUEdge for any
	 * BUEdge, why don't we use it for all the transfer functions of each
	 * statement? Below generate all the MustAliasBUEdge representing each
	 * transfer functions
	 */

	/**
	 * to = from
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public static Set<TypestateBUEdge> constructMove(Variable from, Variable to) {
		if (from == null || to == null)
			throw new RuntimeException("Neither from nor to can be null!");
		Set<TypestateBUEdge> ret = new ArraySet<TypestateBUEdge>();
		// Case 1: from \notin ms && to \notin ms
		TypestateBUEdge c1 = new TypestateBUEdge();
		c1.constraint = new AliasConstraint();
		Set<Variable> notInV1 = c1.constraint.getNotInVSet();
		notInV1.add(from);
		notInV1.add(to);
		ret.add(c1);
		// Case 2: from \in ms && to \notin ms
		TypestateBUEdge c2 = new TypestateBUEdge();
		c2.constraint = new AliasConstraint();
		Set<Variable> notInV2 = c2.constraint.getNotInVSet();
		notInV2.add(to);
		Set<Variable> inV2 = c2.constraint.getInVSet();
		inV2.add(from);
		c2.genSet.add(to);
		ret.add(c2);
		// Case 3: from \notin ms && to \in ms
		TypestateBUEdge c3 = new TypestateBUEdge();
		c3.constraint = new AliasConstraint();
		Set<Variable> notInV3 = c3.constraint.getNotInVSet();
		notInV3.add(from);
		Set<Variable> inV3 = c3.constraint.getInVSet();
		inV3.add(to);
		c3.killSet.add(to);
		ret.add(c3);
		// Case 4: from \in ms && to \in ms
		TypestateBUEdge c4 = new TypestateBUEdge();
		c4.constraint = new AliasConstraint();
		Set<Variable> inV4 = c4.constraint.getInVSet();
		inV4.add(from);
		inV4.add(to);
//		c4.killSet.add(to);
//		c4.genSet.add(to);
		ret.add(c4);
		return ret;
	}

	public static Set<TypestateBUEdge> constructArgMove(Variable actual,
			Variable formal) {
		if (actual == null || formal == null)
			throw new RuntimeException("Neither from nor to can be null!");
		Set<TypestateBUEdge> ret = new ArraySet<TypestateBUEdge>();
		// Case 1: actual \notin ms
		TypestateBUEdge c1 = new TypestateBUEdge();
		c1.constraint = new AliasConstraint();
		Set<Variable> notInV1 = c1.constraint.getNotInVSet();
		notInV1.add(actual);
		ret.add(c1);
		// Case 2: actual \in ms
		TypestateBUEdge c2 = new TypestateBUEdge();
		c2.constraint = new AliasConstraint();
		Set<Variable> inV2 = c2.constraint.getInVSet();
		inV2.add(actual);
		c2.genSet.add(formal);
		ret.add(c2);
		return ret;
	}

	public static Set<TypestateBUEdge> constructRetMove(Variable retReg,
			Variable recReg) {
		if (retReg == null || recReg == null)
			throw new RuntimeException("Neither from nor to can be null!");
		Set<TypestateBUEdge> ret = new ArraySet<TypestateBUEdge>();
		// Case 1: recReg \notin ms
		TypestateBUEdge c1 = new TypestateBUEdge();
		c1.constraint = new AliasConstraint();
		Set<Variable> notInV1 = c1.constraint.getNotInVSet();
		notInV1.add(recReg);
		ret.add(c1);
		// Case 2: rec \in ms
		TypestateBUEdge c2 = new TypestateBUEdge();
		c2.constraint = new AliasConstraint();
		Set<Variable> inV2 = c2.constraint.getInVSet();
		inV2.add(recReg);
		c2.genSet.add(retReg);
		ret.add(c2);
		return ret;
	}

	/**
	 * to = null
	 * 
	 * @param to
	 * @return
	 */
	public static Set<TypestateBUEdge> constructMoveNull(Variable to) {
		if (to == null)
			throw new RuntimeException("Neither from nor to can be null!");
		Set<TypestateBUEdge> ret = new ArraySet<TypestateBUEdge>();
		// Case 1: to \notin ms
		TypestateBUEdge c1 = new TypestateBUEdge();
		c1.constraint = new AliasConstraint();
		Set<Variable> notInV1 = c1.constraint.getNotInVSet();
		notInV1.add(to);
		ret.add(c1);
		// Case 2: to \in ms
		TypestateBUEdge c2 = new TypestateBUEdge();
		c2.constraint = new AliasConstraint();
		Set<Variable> inV2 = c2.constraint.getInVSet();
		inV2.add(to);
		c2.killSet.add(to);
		ret.add(c2);
		return ret;
	}

	public static Set<TypestateBUEdge> constructPutField(Variable from,
			jq_Field to) {
		if (from == null || to == null)
			throw new RuntimeException("Neither from nor to can be null!");
		return constructNoop();
	}
	
	public static Set<TypestateBUEdge> constructNoop(){
		Set<TypestateBUEdge> ret = new ArraySet<TypestateBUEdge>();
		TypestateBUEdge c1 = new TypestateBUEdge();
		c1.constraint = new AliasConstraint(true);
		ret.add(c1);
		return ret;
	}

	public static Set<TypestateBUEdge> constructPutFieldNull(jq_Field to) {
		if (to == null)
			throw new RuntimeException("To cannot be null!");
		return constructNoop();
	}

	public static Set<TypestateBUEdge> constructGetField(jq_Field from,
			Variable to) {
		if (from == null || to == null)
			throw new RuntimeException("Neither from nor to can be null!");
		return constructMoveNull(to);
	}

	@Override
	public Edge applyInvokeWithoutRet(Quad q, Edge clrPE, Loc loc,
			jq_Method tgtM) {
		return this.applyInvoke(q, clrPE, loc, tgtM);
	}

	@Override
	public Set<BUEdge<Edge, Edge>> applyInvokeWithoutRet(Quad q,
			BUEdge<Edge, Edge> clrEdge, Loc loc, jq_Method tgtM, SortedSet<Constraint> trackedCases, int bulimit) {
		return this.applyInvoke(q, clrEdge, loc, tgtM, trackedCases, bulimit);
	}

	@Override
	public int hashCode() {
		if (isHashed)
			return hashCode;
		isHashed = true;
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((constraint == null) ? 0 : constraint.hashCode());
		result = prime * result + ((genSet == null) ? 0 : genSet.hashCode());
		result = prime * result
				+ ((instStates == null) ? 0 : instStates.hashCode());
		result = prime * result + ((killSet == null) ? 0 : killSet.hashCode());
		result = prime * result + ((ret == null) ? 0 : ret.hashCode());
		hashCode = result;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TypestateBUEdge other = (TypestateBUEdge) obj;
		if (constraint == null) {
			if (other.constraint != null)
				return false;
		} else if (!constraint.equals(other.constraint))
			return false;
		if (genSet == null) {
			if (other.genSet != null)
				return false;
		} else if (!genSet.equals(other.genSet))
			return false;
		if (instStates == null) {
			if (other.instStates != null)
				return false;
		} else if (!instStates.equals(other.instStates))
			return false;
		if (killSet == null) {
			if (other.killSet != null)
				return false;
		} else if (!killSet.equals(other.killSet))
			return false;
		if (ret == null) {
			if (other.ret != null)
				return false;
		} else if (!ret.equals(other.ret))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MustAliasBUEdge [killSet=" + killSet + ", genSet=" + genSet
				+ ", constraint=" + constraint + ", ret=" + ret
				+ ", instStates=" + instStates + "]";
	}

	@Override
	public Set<Pair<Loc, BUEdge<Edge, Edge>>> getInstStates() {
		return instStates;
	}

	@Override
	public void setInstStates(Set<Pair<Loc, BUEdge<Edge, Edge>>> stateSet) {
		this.instStates = stateSet;
	}

	@Override
	public BUEdge<Edge, Edge> cloneWithoutInstStates() {
		TypestateBUEdge ret = new TypestateBUEdge();
		ret.constraint = this.constraint.clone();
		ret.killSet = new ArraySet<Variable>(this.killSet);
		ret.genSet = new ArraySet<Variable>(this.genSet);
		ret.instStates = new ArraySet<Pair<Loc, BUEdge<Edge, Edge>>>();
		return ret;
	}

	public TypestateBUEdge lift(Set<Register> args) {
		TypestateBUEdge ret = new TypestateBUEdge();
		AliasConstraint newCons = constraint.lift(args);
		if (newCons.isFalse())
			return null;
		ret.constraint = newCons;
		ret.killSet = new ArraySet<Variable>();
		for (Variable v : this.killSet)
			if (v.isGlobal()) {
				ret.killSet.add(v);
			} else if (args.contains(v.getLocal()))
				ret.killSet.add(v);
		for (Variable v : this.genSet)
			if (v.isGlobal())
				ret.genSet.add(v);
		ret.instStates = new ArraySet<Pair<Loc, BUEdge<Edge, Edge>>>();
		for (Pair<Loc, BUEdge<Edge, Edge>> instPair : this.instStates) {
			ret.instStates.add(new Pair<Loc, BUEdge<Edge, Edge>>(instPair.val0,
					instPair.val1.changeConstraint(ret.constraint)));
		}
		return ret;
	}

	public TypestateBUEdge checkValid(Set<Register> args) {
		TypestateBUEdge ret = new TypestateBUEdge();
		AliasConstraint newCons = constraint.lift(args);
		if (newCons.isFalse())
			return null;
		ret.constraint = newCons;
		ret.killSet = this.killSet;
		ret.genSet = this.genSet;
		ret.instStates = new ArraySet<Pair<Loc, BUEdge<Edge, Edge>>>();
		for (Pair<Loc, BUEdge<Edge, Edge>> instPair : this.instStates) {
			ret.instStates.add(new Pair<Loc, BUEdge<Edge, Edge>>(instPair.val0,
					instPair.val1.changeConstraint(ret.constraint)));
		}
		return ret;
	}

	@Override
	public int size() {
		return genSet.size() + killSet.size() + constraint.size();
	}

	@Override
	public BUEdge<Edge, Edge> checkValid(jq_Method m) {
		return AliasUtilities.checkBUPE(this, m);
	}
}
