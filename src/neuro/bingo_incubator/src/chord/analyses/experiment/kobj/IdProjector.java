package chord.analyses.experiment.kobj;

import chord.analyses.alias.DomC;
import chord.analyses.experiment.classifier.Projector;
import chord.analyses.experiment.solver.Tuple;

public class IdProjector extends Projector {

	public IdProjector(Object projectTo) {
		super(projectTo);
	}

	@Override	public Tuple project(Tuple t) {
		return t;
	}

	@Override	public Object getHighestAbs(Tuple t) {
    return t.getHighestAbs();
	}
}
