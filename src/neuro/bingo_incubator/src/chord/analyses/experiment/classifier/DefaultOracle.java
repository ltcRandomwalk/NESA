package chord.analyses.experiment.classifier;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.*;

@Deprecated // see comment on ComplicatedModel
public class DefaultOracle extends Oracle {

	private Set<Tuple> ts;
	private Set<ConstraintItem> cis;

	public DefaultOracle(Projector p) { super(p); }

	@Override
	public int weight(Tuple t) {
		t = p.project(t);
		return ts.contains(t)? 1 : -1;
	}

	@Override
	public int weight(ConstraintItem ci) {
		Tuple head = p.project(ci.getHeadTuple());
		List<Tuple> subT = new ArrayList<Tuple>();
		for(Tuple sub : ci.getSubTuples()) subT.add(p.project(sub));

		return cis.contains(
			new ConstraintItem(head, subT, ci.getHeadTupleSign(), ci.getSubTuplesSign()))? 1 : -1;
	}

  @Override public void init(Iterable<ConstraintItem> cs) {
    ts = Sets.newHashSet();
    cis = Sets.newHashSet();
    for (ConstraintItem c : cs) {
 			Tuple t = c.getHeadTuple();
			Tuple pT = null;
			if (t != null) { pT = p.project(t); ts.add(pT); }
			List<Tuple> subT = new ArrayList<Tuple>();
			for(Tuple sub : c.getSubTuples()){
				if (sub != null) subT.add(p.project(sub));
			}
			cis.add(new ConstraintItem(pT, subT, c.getHeadTupleSign(), c.getSubTuplesSign()));
    }
  }

	@Override
	public void clear() {
		if(ts!=null) ts.clear();
		if(cis!=null) cis.clear();
	}

	@Override
	public Set<Tuple> getDerivedTuples() {
		return ts;
	}

	@Override
	public Set<Tuple> getAllTuples() {
		Set<Tuple> allTuples = newHashSet();
		for(ConstraintItem ci : cis){
			allTuples.add(ci.getHeadTuple());
			allTuples.addAll(ci.getSubTuples());
		}
		return allTuples;
	}

	@Override
	public Set<ConstraintItem> getAllConstraintItems() {
		return cis;
	}
}
