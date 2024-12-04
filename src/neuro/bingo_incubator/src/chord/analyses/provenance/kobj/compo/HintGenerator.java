package chord.analyses.provenance.kobj.compo;

import java.util.List;

import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.Tuple;

public interface HintGenerator {
	public String hintForQuery(Tuple t);
	public String hintForDlogGRule(ConstraintItem ci);
	public String hintForInput(FormatedConstraint con, List<Tuple> tuplePool);
	public String hintForModelCons(Tuple t, int w);
	public String hintForAbsCost(Tuple t);
}
