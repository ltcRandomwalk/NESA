package chord.project.analyses.composat;

import java.io.PrintWriter;
import java.util.Set;

import chord.util.ArraySet;
import chord.util.StringUtil;

/**
 * A single constraint in the MaxSAT problem, i.e., a conjunct.
 * @author xin
 *
 */
public class Constraint {
	// We distinguish two constraints by ids. So even two constraints have the exact
	// same content, we will treat them as two separate constraints. This matters as if two soft constraints
	// are identical to each other, the weight will be doubled.
	private int id; 
	
	// We use negative weight for hard clauses.
	private double weight;
	private Set<Integer> literals;
	
	public Constraint(String line){
		String tokens[] = line.split("\\s+");
		double w = Double.parseDouble(tokens[0]);
		Set<Integer> ls = new ArraySet<Integer>();
		for(int i = 1; i < tokens.length; i++)
			ls.add(Integer.parseInt(tokens[i]));
		this.init(w, ls);
	}
	
	public Constraint(Integer i){
		Set<Integer> ls = new ArraySet<Integer>();
		ls.add(i);
		this.init(-1, ls);
	}
	
	public Constraint(double weight, Set<Integer> literals){
		this.init(weight, literals);
	}

	/**
	 * The actual constructor.
	 * @param weight
	 * @param literals
	 */
	private void init(double weight, Set<Integer> literals){
		this.id = SatConfig.constrId;
		SatConfig.constrId++;
		this.literals = literals;
		this.weight = weight;
	}
	
	public double getWeight() {
		return weight;
	}

	public Set<Integer> getLiterals() {
		return literals;
	}
	
	public boolean isHardConstraint(){
		return weight < 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
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
		Constraint other = (Constraint) obj;
		if (id != other.id)
			return false;
		return true;
	}
	
	/**
	 * Check whether current constraint can be satisfied under given solution.
	 */
	public boolean check(Set<Integer> posLiters, Set<Integer> negLiters){
		for(Integer l : this.getLiterals()){
			if(l > 0 && !negLiters.contains(l)){
				return true;
			}
			if( l < 0 && !posLiters.contains(-l))
				return true;
		}
		return false;
	}

	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(weight+"[");
		sb.append(StringUtil.join(literals, " v "));
		sb.append("]");
		return sb.toString();
	}
	
	public void write(PrintWriter pw){
		pw.print(weight);
		for(Integer var : literals)
			pw.print(" "+var);
		pw.println();
	}
}
