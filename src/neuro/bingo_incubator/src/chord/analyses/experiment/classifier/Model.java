package chord.analyses.experiment.classifier;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;


// Extra requirement: Has default constructor.
public interface Model {
  void computeWeights(Provenance provenance, Set<Tuple> activeQueries);

  Map<Tuple, Integer> getTupleWeights();
  Map<ConstraintItem, Integer> getConstraintWeights();

  // Optional operations
  void learn(Provenance oracleProvenance, Provenance cheapProvenance);
  void save(PrintWriter out);
  void load(BufferedReader in);
}
