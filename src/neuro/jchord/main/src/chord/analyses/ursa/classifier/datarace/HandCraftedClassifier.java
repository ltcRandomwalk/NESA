package chord.analyses.ursa.classifier.datarace;

import java.util.Map;
import java.util.Set;

import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.classifier.Classifier;
import chord.project.analyses.provenance.Tuple;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;

public class HandCraftedClassifier implements Classifier {
	private boolean isAggressive;

	public HandCraftedClassifier(boolean isAggressive) {
		this.isAggressive = isAggressive;
	}

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {

	}

	@Override
	public void save(String path) {

	}

	@Override
	public void load(String path) {

	}

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		if (t.getRelName().equals("escE"))
			if (this.isAggressive)
				return 1;
			else{
				Quad q = (Quad)t.getValue(0);
				if(q.getMethod().getName().toString().contains("<init>"))
					return 0.9;
			}
		if (t.getRelName().equals("PathEdge_cs")) {
			Inst p = (Inst) t.getValue(1);
			if (p.getMethod().toString().contains("run")) {
				if (p instanceof BasicBlock && ((BasicBlock) p).isEntry()) {
					return 1;
				}
			}
		}
		return 0;
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
