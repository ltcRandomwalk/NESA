package chord.project.analyses.provenance;

import java.util.Arrays;
import java.util.List;

public class FormatedConstraint{
	public int weight;
	public int[] constraint;
	public FormatedConstraint(int weight, int[] constraint){
		this.weight = weight;
		this.constraint = constraint;
	}
	public String toString(){
		StringBuffer sb = new StringBuffer();
		sb.append(weight);
		for(int i : constraint)
			sb.append(" "+i);
		sb.append(" 0");
		return sb.toString();
	}
	
	public String toExplicitString(List<Tuple> tuplePool){
		StringBuffer sb = new StringBuffer();
		for(int i : constraint){
			if(i > 0)
				sb.append(tuplePool.get(i).toString());
			else
				sb.append("!"+tuplePool.get(0-i).toString());
				sb.append(" \\/ ");
		}
		return sb.toString();
	}
	
	public int[] getConstraint(){
		return this.constraint;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(constraint);
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
		FormatedConstraint other = (FormatedConstraint) obj;
		if (!Arrays.equals(constraint, other.constraint))
			return false;
		if (weight != other.weight)
			return false;
		return true;
	}
	
	
}