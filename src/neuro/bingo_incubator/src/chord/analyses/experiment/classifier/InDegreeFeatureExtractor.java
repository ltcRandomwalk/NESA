package chord.analyses.experiment.classifier;

import java.util.ArrayList;
import java.util.List;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public class InDegreeFeatureExtractor extends FeatureExtractor {

	public InDegreeFeatureExtractor(Projector p) {
		super(p);
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, Tuple t) {
		int inDegree = 0;
    List<ConstraintItem> cs = provenance.data.get(t.getRelName());
    if (cs != null) inDegree += cs.size();
		ArrayList<Double> ret = new ArrayList<Double>();
		ret.add((double)inDegree);
		return ret;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	@Override
	public String featureToString(ArrayList<Double> features) {
		return "In degree "+features;
	}

}
