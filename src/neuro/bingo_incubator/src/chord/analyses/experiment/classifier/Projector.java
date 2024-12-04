package chord.analyses.experiment.classifier;

import chord.analyses.experiment.solver.Tuple;

public abstract class Projector {
	public Object projectTo;

	public Projector(Object projectTo){
		this.projectTo = projectTo;
	}

	public abstract Tuple project(Tuple t);

	//Returns the highest/most precise abstraction mentioned by this tuple.
	//If no abstraction is mentioned, then it returns some special value.
	public abstract Object getHighestAbs(Tuple t);

}
