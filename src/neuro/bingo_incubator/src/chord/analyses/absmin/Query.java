package chord.analyses.absmin;

import java.util.Set;

import chord.util.Utils;

/**
 * Generic query object used to represent the queries.
 */
public abstract class Query implements Comparable<Query> {
    ////////////////////////////////////////////////////////////////////////////////////
    // Instance methods that classes extending this class must implement.
    ////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Encode query as a string such that it can be exchanged
	 * between independent processes and uniquely interpreted.
	 */
	public abstract String encode();

	/**
	 * Encode query in a manner suitable for XML printing.
	 */
	public abstract String encodeForXML();

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object q);

	@Override
	public abstract String toString();

	@Override
	public abstract int compareTo(Query q);

    ////////////////////////////////////////////////////////////////////////////////////
    // Utility static methods.
    ////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Concatenate queries using sep as a delimiter and return the resulting concatenated queries.
	 */
	public static String concatQueries(final String[] queries, final String sep) {
		if (queries == null) {
			return "";
		}
		final StringBuilder buf = new StringBuilder();
		for (final String query : queries) {
			if (buf.length() > 0) buf.append(sep);
			buf.append(query);
		}
		return buf.toString();
	}

	public static String concatQueries(final Set<Query> queries, final String sep) {
		final String[] queriesAsArray = new String[queries.size()];
		int i = 0;
		for (final Query query : queries) {
			queriesAsArray[i++] = query.encode();
		}
		return concatQueries(queriesAsArray, sep);
	}

	/**
	 * Split multiple queries into an array of queries using the supplied delimiter.
	 */
	public static String[] splitQueries(final String queries, final String sep) {
		if (queries == null) return null;
		return Utils.split(queries, sep, true, true, 0);
	}
}
