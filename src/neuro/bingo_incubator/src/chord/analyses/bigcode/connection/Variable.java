package chord.analyses.bigcode.connection;

public abstract class Variable {	
	public final boolean isStatic;
	public final int index;	
	public Variable(int index, boolean isStatic) {
		this.index = index;
		this.isStatic = isStatic;
	}
	public boolean isStatic() { return isStatic; }
	public boolean isOld() {
		return index >= VariablesPartition.MAX_VARS;
	}
}
