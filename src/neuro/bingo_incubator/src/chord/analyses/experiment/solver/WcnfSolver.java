package chord.analyses.experiment.solver;

import java.io.PrintWriter;
import java.util.List;

/** Handles saving the question in DIMACS wcnf format. Subclasses should run
the solver and interpret the answer. */
public abstract class WcnfSolver implements MaxSatSolver {
  void saveWcnf(List<FormattedConstraint> clauses, PrintWriter writer) {
    int top = top(clauses);
    int maxvar = maxvar(clauses);
    writer.printf("p wcnf %d %d %d%n", maxvar, clauses.size(), top);
    for (FormattedConstraint c : clauses) {
        writer.printf("%d", c.weight());
        for (int l : c.constraint()) writer.printf(" %d", l);
        writer.println(" 0");
    }
  }

  int top(List<FormattedConstraint> clauses) {
    int top = 0;
    for (FormattedConstraint c : clauses) top = Math.max(top, c.weight());
    return top;
  }

  int maxvar(List<FormattedConstraint> clauses) {
    int maxvar = 0;
    for (FormattedConstraint c : clauses) for (int l : c.constraint())
      maxvar = Math.max(maxvar, Math.abs(l));
    return maxvar;
  }
}
