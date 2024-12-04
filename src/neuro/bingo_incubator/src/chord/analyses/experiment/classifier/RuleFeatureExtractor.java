package chord.analyses.experiment.classifier;

import static com.google.common.base.Verify.verify;
import static chord.util.CollectionUtil.newArrayList;
import static chord.util.CollectionUtil.newHashMap;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public class RuleFeatureExtractor extends FeatureExtractor {

	private Map<String, Double> indexOfRule = newHashMap();

	public RuleFeatureExtractor(Projector p) {
		super(p);
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, Tuple t) {
		return null;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
		ArrayList<Double> ret = Lists.newArrayList();
    String matchRule = null;

    for (String ruleid : provenance.data.keySet()) {
      if (LookUpRule.byId(ruleid).match(c)) {
        verify(matchRule == null, "ConstraintItem matches two rules");
        matchRule = ruleid;
      }
    }
    verify(matchRule != null, "No matching rule found");
		Double i = indexOfRule.get(matchRule.toString());
		if (i == null) indexOfRule.put(matchRule.toString(), i = (double) indexOfRule.size());
		ret.add(i);
		return ret;
	}

	@Override
	public void clear() {
	}

}
