package chord.analyses.prunerefine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractionSet<E> {
	
	protected HashSet<E> S = new HashSet<E>(); // The set of abs
	
	@Override public int hashCode() { return S.hashCode(); }
	@Override public boolean equals(Object _that) {
		AbstractionSet<E> that = (AbstractionSet<E>)_that;
		return S.equals(that.S);
	}
	
	public Set<E> getAbstractions() { return S; }
	public void add(E c) { S.add(c); }
	public void add(AbstractionSet<E> that) { S.addAll(that.S); }
	public boolean contains(E c) { return S.contains(c); }
	public int size() { return S.size(); }
	
	@Override public abstract  String toString();
	
	public abstract List<E> getSortedAbstractions();
	public abstract String printStatus();
	public abstract String printAbstraction(E abs);

}
