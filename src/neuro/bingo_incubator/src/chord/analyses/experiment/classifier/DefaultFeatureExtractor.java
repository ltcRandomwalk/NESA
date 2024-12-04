package chord.analyses.experiment.classifier;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import static chord.util.CollectionUtil.*;

// Features are tuples with contexts truncated to 0  (ignores derivation).
// NOTE: Learning based on these features does not transfer to other programs.
public class DefaultFeatureExtractor extends FeatureExtractor {

	public DefaultFeatureExtractor(Projector p) {
		super(p);
	}

	private Map<String, Double> indexOfName = newHashMap();

    @Override public ArrayList<Double> features(Provenance provenance, Tuple t) {
      t = p.project(t);
      ArrayList<Double> r = newArrayList();
      Double i = indexOfName.get(t.getRelName());
      if (i == null) indexOfName.put(t.getRelName(), i = (double) indexOfName.size());
      r.add(i);

      int sum = 0;
      int[] indices = t.getIndices();
      for (int j = 0; j < indices.length; j++) sum += indices[j];
      r.add((double) sum);
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
}
