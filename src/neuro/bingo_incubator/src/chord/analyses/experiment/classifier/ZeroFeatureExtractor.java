package chord.analyses.experiment.classifier;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.ArrayList;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import static chord.util.CollectionUtil.*;

public class ZeroFeatureExtractor extends FeatureExtractor {

  public ZeroFeatureExtractor(Projector p) {
		super(p);
	}

  @Override public ArrayList<Double> features(Provenance provenance, Tuple t) {
	  return Lists.newArrayList();
  }

  @Override
  public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
	  // TODO Auto-generated method stub
	  return null;
  }

  @Override
  public void clear() {}

}
