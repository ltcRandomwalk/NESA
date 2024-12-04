 package chord.project.analyses.softrefine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import chord.util.tuple.object.Pair;

/**
 * Represents a constraint in provenance, which has the following form:
 * headTuple = subTuple1 * subTuple2...subTupleN
 * @author Xin. Modified by Hongseok.
 *
 */
public class ConstraintItem {
	public Tuple headTuple;
	public List<Tuple> subTuples = new ArrayList<Tuple>();
	private int weight;
	
	public ConstraintItem(int weight) { 
		this.weight = weight;
	}	
	public void setWeight(int weight) { this.weight = weight; }
	public int getWeight() { return weight; }
	
	public Boolean evaluate(Model model) {
		if (headTuple != null && model.isTrue(headTuple)) return true;
		else {
			for (Tuple tuple : subTuples) {
				if (model.isFalse(tuple)) return true; 
			}
			return false;
		}
	}
	
	public Pair<Tuple, Set<Tuple>> split(Fun<Tuple,Tuple> trans) {
		Tuple head = trans.apply(headTuple);	
		Set<Tuple> conds = new HashSet<Tuple>(); 
		for (Tuple cond : subTuples) {
			Tuple cond1 = trans.apply(cond);
			conds.add(cond1);
		}
		return (new Pair<Tuple, Set<Tuple>>(head,conds));
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		if (headTuple != null) {
			sb.append(headTuple.toString());
		}
		else {
			sb.append(false);
		}
		sb.append(":=");
		for (int i = 0; i < subTuples.size(); i++) {
			if (i!=0) sb.append("*");
			sb.append(subTuples.get(i));
		}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((headTuple == null) ? 0 : headTuple.hashCode());
		result = prime * result
				+ ((subTuples == null) ? 0 : subTuples.hashCode());
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
		ConstraintItem other = (ConstraintItem) obj;
		if (headTuple == null) {
			if (other.headTuple != null)
				return false;
		} else if (!headTuple.equals(other.headTuple))
			return false;
		if (subTuples == null) {
			if (other.subTuples != null)
				return false;
		} else if (!subTuples.equals(other.subTuples))
			return false;
		return true;
	}
}
