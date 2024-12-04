package chord.analyses.absmin;

import java.util.Set;

import chord.util.Utils;

/**
 * Generic abstraction object used to represent the abstractions to be minimized
 *  as per the abstraction minimizing coarsening algorithm (POPL 11')
 */
public abstract class Abstraction implements Comparable<Abstraction> {
	private int level; // Level of abstraction
	private int minLevel; // Minimum possible level of refinement
	private int maxLevel; // Maximum possible level of refinement

	protected int mod = 0; // Flag to indicate if the object has been modified

    ////////////////////////////////////////////////////////////////////////////////////
    // Instance methods that classes extending this class must implement.
    ////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Encode abstraction as a string such that it can be exchanged
	 * between independent processes and uniquely interpreted.
	 */
	public abstract String encode();

	/**
	 * Encode abstraction in a manner suitable for XML printing.
	 */
	public abstract String encodeForXML();
	
	/**
	 * Copy given abstraction to this abstraction.
	 */
	public abstract void copy(Abstraction a);
	
	/**
	 * Make and return a copy of this abstraction.
	 */
	public abstract Abstraction copy();

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object q);

	@Override
	public abstract String toString();

	@Override
	public abstract int compareTo(Abstraction a);

    ////////////////////////////////////////////////////////////////////////////////////
    // Instance methods that classes extending this class need not override.
    ////////////////////////////////////////////////////////////////////////////////////
	
	public void setLevel(final int level) {
		this.level = level;
		mod = 1;
	}

	public void setMinLevel(final int minLevel) {
		this.minLevel = minLevel;
		mod = 1;
	}

	public void setMaxLevel(final int maxLevel) {
		this.maxLevel = maxLevel;
		mod = 1;
	}

	public int getLevel() {
		return level;
	}

	public int getMinLevel() {
		return minLevel;
	}

	public int getMaxLevel() {
		return maxLevel;
	}

	/**
	 * Increase level of refinement.  Client must ensure this does not go beyond maxLevel.
	 */
	public void refine() {
		level++;
		mod = 1;
	}

	public void maxRefine() {
		this.setLevel(this.getMaxLevel());
	}

	public void minRefine() {
		this.setLevel(this.getMinLevel());
	}

    ////////////////////////////////////////////////////////////////////////////////////
    // Utility static methods.
    ////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Concatenate abstractions using sep as a delimiter and return the resulting concatenated abstractions.
	 */
	public static String concatAbstractions(final String[] abstractions, final String sep) {
		if (abstractions == null)
			return "";
		final StringBuilder buf = new StringBuilder();
		for (final String abstraction : abstractions) {
			if (buf.length() > 0) buf.append(sep);
			buf.append(abstraction);
		}
		return buf.toString();
	}

	/**
	 * Split multiple abstractions into an array of abstractions using the supplied delimiter.
	 */
	public static String[] splitAbstractions(final String abstractions, final String sep) {
		if (abstractions == null)
			return null;
		return Utils.split(abstractions, sep, true, true, 0);
	}
}
