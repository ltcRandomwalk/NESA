package chord.analyses.experiment.classifier;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import static chord.util.CollectionUtil.*;

public class RelnameFeatureExtractor extends FeatureExtractor {

	private Map<String, Double> indexOfName = newHashMap();

	public RelnameFeatureExtractor(Projector p) {
		super(p);
	}

	@Override public ArrayList<Double> features(Provenance provenance, Tuple t) {
		// t = t.truncateContext(0);
		ArrayList<Double> r = newArrayList();
		Double i = indexOfName.get(t.getRelName());
		if (i == null) indexOfName.put(t.getRelName(), i = (double) indexOfName.size());
		r.add(i);
		return r;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clear() {
		indexOfName.clear();

	}

	@Override
	public String featureToString(ArrayList<Double> features) {
		Double relDouble = features.get(0);
		for(Map.Entry<String, Double> entry : indexOfName.entrySet()){
			if(entry.getValue().equals(relDouble))
				return entry.getKey();
		}
		return "Unknown feature vector: "+features;
	}
}
