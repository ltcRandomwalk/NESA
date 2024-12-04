package chord.analyses.experiment.classifier;

import com.google.common.collect.Maps;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public final class DefaultModel implements Model {
  public DefaultModel() {}

  @Override
  public void computeWeights(Provenance provenance, Set<Tuple> activeQueries) {
    // do nothing
  }

  @Override
  public void learn(Provenance oracleProvenance, Provenance cheapProvenance) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Tuple, Integer> getTupleWeights() {
    return Maps.newHashMap();
  }

  @Override
  public Map<ConstraintItem, Integer> getConstraintWeights() {
    return Maps.newHashMap();
  }

  @Override
  public void save(PrintWriter out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void load(BufferedReader in) {
    throw new UnsupportedOperationException();
  }
}
