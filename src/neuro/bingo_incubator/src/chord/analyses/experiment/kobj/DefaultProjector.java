package chord.analyses.experiment.kobj;

import chord.analyses.alias.DomC;
import chord.analyses.experiment.classifier.Projector;
import chord.analyses.experiment.solver.Tuple;

public class DefaultProjector extends Projector {

	public DefaultProjector(Object projectTo) {
		super(projectTo);
	}

	@Override	public Tuple project(Tuple t) {
    return t.project((Integer) projectTo);
	}

	@Override	public Object getHighestAbs(Tuple t) {
    return t.getHighestAbs();
	}
}
