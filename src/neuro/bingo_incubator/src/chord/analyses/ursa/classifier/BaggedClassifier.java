package chord.analyses.ursa.classifier;

import java.util.Map;
import java.util.Set;

import chord.analyses.mln.ConstraintItem;
import chord.analyses.ursa.classifier.datarace.DynamicAnalysisClassifier;
import chord.analyses.ursa.classifier.datarace.HandCraftedClassifier;
import chord.project.analyses.provenance.Tuple;

public class BaggedClassifier implements Classifier {
	DynamicAnalysisClassifier dynamic = new DynamicAnalysisClassifier();
	HandCraftedClassifier aggr = new HandCraftedClassifier(true);
	HandCraftedClassifier cons = new HandCraftedClassifier(false);

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {
		dynamic.train(edbLabelMap, idbLabelMap, provenance, relevantTuples);
		aggr.train(edbLabelMap, idbLabelMap, provenance, relevantTuples);
		cons.train(edbLabelMap, idbLabelMap, provenance, relevantTuples);
	}

	@Override
	public void save(String path) {
		dynamic.save(path);
		aggr.save(path);
		cons.save(path);
	}

	@Override
	public void load(String path) {
		dynamic.load(path);
		aggr.load(path);
		cons.load(path);
	}
	

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		if(dynamic.predictFalse(t, provenance) > 0.51 && cons.predictFalse(t, provenance) > 0.51)
			return 1;
		return 0;
	}

	@Override
	public void init() {
	}

	@Override
	public void done() {
	}

}
