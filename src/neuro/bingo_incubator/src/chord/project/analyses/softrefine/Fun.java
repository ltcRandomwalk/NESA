package chord.project.analyses.softrefine;

// Interface for closures in the style of Scala

public interface Fun<A,B> {
  B apply(A x);
}
