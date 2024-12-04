package chord.project.analyses.qmaxsat;

import java.util.List;

/**
 * A clause in the WCNF format of MaxSAT.
 * @author xin
 *
 */
public class Clause {
	public int weight;
	public List<Integer> posLiterals;
	public List<Integer> negLiterals;
	
	public String toDimacsString(){
		StringBuffer sb = new StringBuffer();
		if(this.isHardClause())
			sb.append(Problem.hardWeight);
		else
			sb.append(weight);
		for(Integer at : posLiterals)
			sb.append(" "+at);
		for(Integer at : negLiterals)
			sb.append(" "+(-at));
		sb.append(" 0");
		return sb.toString();
	}
	
	public boolean isHardClause(){
		return weight < 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((negLiterals == null) ? 0 : negLiterals.hashCode());
		result = prime * result
				+ ((posLiterals == null) ? 0 : posLiterals.hashCode());
		result = prime * result + weight;
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
		Clause other = (Clause) obj;
		if (negLiterals == null) {
			if (other.negLiterals != null)
				return false;
		} else if (!negLiterals.equals(other.negLiterals))
			return false;
		if (posLiterals == null) {
			if (other.posLiterals != null)
				return false;
		} else if (!posLiterals.equals(other.posLiterals))
			return false;
		if (weight != other.weight)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Clause [weight=" + weight + ", posLiterals=" + posLiterals
				+ ", negLiterals=" + negLiterals + "]";
	}
	
	
}
