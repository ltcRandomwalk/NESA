package chord.analyses.bigcode.connection;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;

public class StaticVariable extends Variable  {		
	static Map <Integer, StaticVariable> stVariables = new HashMap<Integer, StaticVariable>() ;
	public static StaticVariable getNewStaticVariable(int index) {
		if (!((index >= 0) && ((index < (VariablesPartition.MAX_VARS * 2)) || index == VariablesPartitionNullness.NULL_INT))) {
			System.out.println(index);
			assert(false);
		}
		StaticVariable v = stVariables.get(index);
		if (v == null) {
			 v = new StaticVariable(index);
			 stVariables.put(index,v);
		}
		return v;		
	}

	private StaticVariable(int index) {
		super(index, true);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof StaticVariable)) return false;
		StaticVariable v = (StaticVariable) o;
		return (v.index == this.index);
	}
	
	@Override
	public int hashCode() { 
		return index + 1000000;  //to get a different hash code than local vars}
	}

	@Override
	public String toString()
	{
		
		String str = "";
		int newIndex; 
		if (isOld())
    	{	
			str += "Aux-";
			newIndex = index - VariablesPartition.MAX_VARS;
    	}
		else
		{
			newIndex = index;
		}
		jq_Field f = VariablesPartition.domF.get(newIndex);
		str += f.getName().toString();
		return str;
	}	
}

