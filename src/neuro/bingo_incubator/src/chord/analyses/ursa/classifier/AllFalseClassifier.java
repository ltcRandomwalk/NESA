package chord.analyses.ursa.classifier;

import java.util.Map;
import java.util.Set;

import chord.analyses.mln.ConstraintItem;
import chord.project.analyses.provenance.Tuple;

public class AllFalseClassifier implements Classifier {

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(String path) {
		// TODO Auto-generated method stub

	}

	@Override
	public void load(String path) {
		// TODO Auto-generated method stub

	}

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		return 1.0;
	}

	@Override
	public void init() {
		// TODO Auto-generated method stub

	}

	@Override
	public void done() {
		// TODO Auto-generated method stub

	}

}
