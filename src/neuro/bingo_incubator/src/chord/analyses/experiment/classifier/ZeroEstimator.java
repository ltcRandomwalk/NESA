package chord.analyses.experiment.classifier;

import com.google.common.base.Predicate;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public class ZeroEstimator extends Estimator {
  @Override public void learnFromTuples(
    Oracle oracle,
    Set<Tuple> queries,
    Provenance provenance,
    FeatureExtractor extractor,
    Predicate<Tuple> selector
  ) { return; }

  @Override
  public void learnFromConstraints(
		Oracle oracle,
		Set<Tuple> queries,
    Provenance provenance,
    FeatureExtractor extractor,
    Predicate<ConstraintItem> selector
  ) { return; }

  @Override protected double estimate(ArrayList<Double> features) { return 0.0; }

  @Override
  public void save(String filePath) {}

  @Override
  public void load(String filePath) {}

  @Override
  public void clear() {}

}
