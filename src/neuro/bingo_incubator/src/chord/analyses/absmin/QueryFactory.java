package chord.analyses.absmin;

/**
 * Object factory for creating Query objects of appropriate types
 *
 */
public interface QueryFactory {
	Query create(String enc);
}
