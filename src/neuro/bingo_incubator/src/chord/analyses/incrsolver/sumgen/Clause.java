package chord.analyses.incrsolver.sumgen;

import java.util.Arrays;

public class Clause {
	public int[] ls;
	private int hash;
	Clause(final int[] ls) {
		this.ls = ls;
		for (int l : this.ls) hash ^= l;
	}
	@Override public boolean equals(Object other) {
		if (!(other instanceof Clause)) return false;
		Clause c = (Clause) other;
		return
			hash == c.hash && Arrays.equals(ls, c.ls);
	}
	@Override public int hashCode() { return hash; }
}
