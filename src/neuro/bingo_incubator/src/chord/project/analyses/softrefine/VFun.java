package chord.project.analyses.softrefine;

// Interface for closures in the style of Scala

public interface VFun<A> {
  void apply(A x);
}
