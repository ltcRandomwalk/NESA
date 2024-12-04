package chord.analyses.absmin;

/**
 * Object factory for creating Abstraction objects of appropriate types
 *
 */
public interface AbstractionFactory {
	Abstraction create(String enc);
}
