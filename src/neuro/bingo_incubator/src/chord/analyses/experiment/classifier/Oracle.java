package chord.analyses.experiment.classifier;

import java.util.List;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Tuple;

@Deprecated
public abstract class Oracle {
	
	public Projector p;
	
	public Oracle(Projector p){
		this.p = p;
	}
	
	public abstract int weight(ConstraintItem t);
	
	public abstract int weight(Tuple t);

	// Radu's initial IDEA: EModel will trace some of the things that happen while solving. For
	// example, it could keep the last derivation seen, and offer a view of it
	// through public methods. Oracles could query this information, in order to
	// decide their weights.
	// 
	// Hongseok's change: I made Oracle directly access the analysis result, such as instantiated rules,
	// via configFiles and the static methods of ResultLoader. In this way, we don't have to overload 
	// the model class with multiple functionalities.
  public abstract void init(Iterable<ConstraintItem> cs); // for backward compatibility
	
	public abstract void clear();
	
	//Projected versions of the derived tuples in the oracle
	public abstract Set<Tuple> getDerivedTuples();
	
	//Projected versions of all tuples in the oracle
	public abstract Set<Tuple> getAllTuples();
	
	//Projected versions of all constraints in the oracle
	public abstract Set<ConstraintItem> getAllConstraintItems();
}
