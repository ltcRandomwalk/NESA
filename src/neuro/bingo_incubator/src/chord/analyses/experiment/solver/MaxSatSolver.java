package chord.analyses.experiment.solver;

import java.util.List;

interface MaxSatSolver {
  /** Returns an assignment, represented as a list of literals; or null if
    the hard clauses are unsatisfiable. The {@code problemName} is used only
    for debug. */
  List<Integer> solve(List<FormattedConstraint> clauses, String problemName);
}
