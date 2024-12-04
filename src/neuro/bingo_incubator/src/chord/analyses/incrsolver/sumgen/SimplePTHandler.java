package chord.analyses.incrsolver.sumgen;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import chord.bddbddb.Dom;
import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.LookUpRule;
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
  HashMap<String, int[]> mark = new HashMap<String, int[]>();

  public SimplePTHandler(List<Tuple> constTuples, HashMap<String, int[]> mark){
	  this.constTuples = constTuples;
	  this.mark = mark;
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
  public Tuple transform(Tuple t) {
    if (constTuples.contains(t)) return null;
    // if (!belongsToLib(t)) return null;
    // if (derivedRs.contains(t.getRelName())) return mkPair(t, true);
    if (derivedRs.contains(t.getRelName())) return t;
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
  
  public boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		for (int i = 0; i < dArr.length; i++) {
			if (mark.containsKey(dArr[i].getName()))
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
		}
		String ret = " ";
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}


  // some abbreviations
  static Tuple mkTuple(String n, int[] ps) { return new Tuple(pRel(n), ps); }
}
