package chord.analyses.mustnotnull;

import chord.util.ArraySet;

/**
 * The abstract state tracked by must-not null analysis.
 * 
 * @author Ravi Mangal
 */
public class AbstractState {
	public static final ArraySet<AccessPath> emptyMS;
	static {
		emptyMS = new ArraySet<AccessPath>(0);
		emptyMS.setImmutable();
	}

	public final ArraySet<AccessPath> ms;
	public final boolean canReturn;

	public AbstractState(ArraySet<AccessPath> ms) {
		this(ms, false);
	}

	public AbstractState(ArraySet<AccessPath> ms, boolean ret) {
		assert (ms != null);
		this.ms = ms;
		canReturn = ret;
	}

	@Override
	public int hashCode() {
		return ms.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof AbstractState) {
			AbstractState that = (AbstractState) obj;
			return canReturn == that.canReturn && ms.equals(that.ms);
		}
		return false;
	}

	@Override
	public String toString() {
		String ret = "ret=" + (canReturn ? "true" : "false") + 
				",ms=" + (ms.isEmpty() ? "EMPTY" : "");
		for (AccessPath ap : ms) ret += ap + ",";
		return ret;
	}
}
