package chord.analyses.mln;

import java.util.List;
import java.util.Set;

import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.MaxSatGenerator;
import chord.project.analyses.provenance.Tuple;
import chord.util.tuple.object.Pair;

import com.google.common.collect.Sets;

import static chord.util.CollectionUtil.mkPair;
import static chord.util.RelUtil.pRel;

//public final class SimplePTHandler implements ParamTupleConsHandler {
public class SimplePTHandler {
  protected Set<String> derivedRs;
  protected List<Tuple> constTuples;

  public SimplePTHandler(List<Tuple> constTuples){
	  this.constTuples = constTuples;
  }

  // Add (DenyX(a,b) :- DenyX(a,c)), for X∊{O,H} and b>c.
  public Set<FormatedConstraint>
  getHardCons(int w, Set<Tuple> paramTSet, MaxSatGenerator gen) {
    Set<FormatedConstraint> r = Sets.newHashSet();
    return r;
  }

  public void init(List<LookUpRule> rules) {
    derivedRs = Sets.newHashSet();
    for (LookUpRule r : rules) {
      derivedRs.add(r.getHeadRelName());
    }
  }

  // For the meaning of return, see ParamTupleConsHandler.
  public Pair<Tuple, Boolean> transform(Tuple t, Boolean sign) {
    // if (constTuples.contains(t)) return null;
    // if (derivedRs.contains(t.getRelName())) return mkPair(t, true);
    if (derivedRs.contains(t.getRelName())) return mkPair(t, sign);
    return null;
  }
  
  public boolean isConstant(Tuple t) {
	  return constTuples.contains(t);
  }
  
  public List<Tuple> getConstantTuples() {
	  return constTuples;
  }

  public Set<String> getDerivedRelNames() {
    return derivedRs;
  }
  
  public int getWeight(Tuple t) {
  	return 0;
  }
  


  // some abbreviations
  static Tuple mkTuple(String n, int[] ps) { return new Tuple(pRel(n), ps); }
}
