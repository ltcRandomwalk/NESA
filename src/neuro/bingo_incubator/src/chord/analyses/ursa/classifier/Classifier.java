package chord.analyses.ursa.classifier;

import java.util.Map;
import java.util.Set;

import chord.analyses.mln.ConstraintItem;
import chord.project.analyses.provenance.Tuple;

public interface Classifier {

	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance, Set<Tuple> relevantTuples);
	
	public void save(String path);
	
	public void load(String path);
	
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance);
	
	public void init();
	public void done();
	
}
