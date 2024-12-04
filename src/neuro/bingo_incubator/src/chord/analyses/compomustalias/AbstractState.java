package chord.analyses.compomustalias;

import chord.util.ArraySet;
import chord.analyses.typestate.TypeState;

/**
 * The abstract state tracked by type-state analysis for an object.
 * 
 * @author machiry
 */
public class AbstractState {
	public static final ArraySet<AccessPath> emptyMS;
	static {
		emptyMS = new ArraySet<AccessPath>(0);
		emptyMS.setImmutable();
	}

	public final TypeState ts;
	public final ArraySet<AccessPath> ms;
	public final boolean canReturn;
	public boolean may;

	public AbstractState(boolean may, TypeState ts, ArraySet<AccessPath> ms) {
		this(ts, ms, false, may);
	}

	public AbstractState(TypeState ts, ArraySet<AccessPath> ms, boolean ret, boolean may) {
		this.ts = ts;
		assert (ms != null);
		this.ms = ms;
		canReturn = ret;
		this.may = may;
	}

	@Override
	public int hashCode() {
		return ms.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		boolean retval = false;
		if (obj instanceof AbstractState) {
			AbstractState that = (AbstractState) obj;
			if (ts != null)
			    retval = (ts.equals(that.ts) && canReturn == that.canReturn && ms.equals(that.ms) && may == that.may);
			else
				retval = (that.ts == null && canReturn == that.canReturn && ms.equals(that.ms) && may == that.may);
		}
		return retval;
	}

	@Override
	public String toString() {
		String ret = "ts=" + ts + ",ret=" + (canReturn ? "true" : "false") + ",may=" + (may ? "true" : "false") + 
				",ms=" + (ms.isEmpty() ? "EMPTY" : "");
		for (AccessPath ap : ms) ret += ap + ",";
		return ret;
	}
	
	public boolean isAppAccessed() {
		boolean retval = false;
		for (AccessPath ap : ms) {
			if (ap instanceof RegisterAccessPath) {
				if ((((RegisterAccessPath)ap).isAppAccessed())) retval = true;
			} 
			else if (ap instanceof GlobalAccessPath) {
				if ((((GlobalAccessPath)ap).isAppAccessed())) retval = true;
			}
			if (retval == true) break;
		}
		return retval;
	}
}
