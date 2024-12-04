package chord.project.analyses.softrefine;

public abstract class Model {
	public abstract boolean isTrue(Tuple t);
	public boolean isFalse(Tuple t) { return (!isTrue(t)); }
}
