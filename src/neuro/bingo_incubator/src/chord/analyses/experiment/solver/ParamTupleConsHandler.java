package chord.analyses.experiment.solver;

import java.util.List;
import java.util.Set;

import chord.util.tuple.object.Pair;

// TODO(rg): I don't understand this interface.
// If somebody can explain in words what is the purpose of this interface, then
// please add a comment.
public interface ParamTupleConsHandler {
	public void init(List<LookUpRule> rules);

  /* Returns null iff the tuple (with the given sign) can be omitted from
  constraints; that is, if the tuple (with the given sign) can be assumed
  to be false. */
	public Tuple transform(Tuple t, Boolean sign);

	public boolean isParam(Tuple t);

	public boolean isDerived(Tuple t);

	public Set<Tuple> getConstTuples();

	public Set<String> getDerivedRelNames();

	public Set<String> getAllRelNames();
}
