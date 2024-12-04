package chord.analyses.bigcode.connection;

import java.util.HashMap;
import java.util.Map;

public class LocalVariable extends Variable {		
	static Map <Integer, LocalVariable> locVariables = new HashMap<Integer, LocalVariable>() ;
	public static LocalVariable getNewLocalVariable(int index)  {
		if (!((index >= 0) && ((index < (VariablesPartition.MAX_VARS * 2)) || index == VariablesPartitionNullness.NULL_INT))) {
			System.out.println(index);
			assert(false);
		}
		LocalVariable v = locVariables.get(index);
		if (v == null) {
			 v = new LocalVariable(index);
			 locVariables.put(index,v);
		}
		return v;	
	}
	
	private LocalVariable(int index) {	
		super(index, false);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof LocalVariable)) return false;
		LocalVariable v = (LocalVariable) o;
		return v.index == this.index;
	}
	
	@Override
	public int hashCode() { return index; }

	@Override
	public String toString() {
		String str = "";
		int newIndex; 
		if (index != VariablesPartitionNullness.NULL_INT) {
			if (isOld()) {	
				str += "Aux-";
				newIndex = index - VariablesPartition.MAX_VARS;
	    	} else {
				newIndex = index;
			}
			str += VariablesPartition.domV.get(newIndex).toString();
		}
		return str;
	}	
}
