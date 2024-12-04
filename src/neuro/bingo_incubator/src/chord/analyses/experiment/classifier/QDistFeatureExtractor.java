package chord.analyses.experiment.classifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public class QDistFeatureExtractor extends FeatureExtractor {
	/**
	 * A map from query tuple to the map [t -> distance between t and q]
	 */
	private Map<Tuple, Map<Tuple,Integer>> distMap;

	public QDistFeatureExtractor(Projector p) {
		super(p);
	}

	@Override
	public void prebuild(Provenance provenance, Set<Tuple> queries) {
		distMap = new HashMap<Tuple, Map<Tuple,Integer>>();
		for(Tuple q : queries){
			List<Tuple> workList = new ArrayList<Tuple>();
			Map<Tuple,Integer> qMap = new HashMap<Tuple,Integer>();
			distMap.put(q, qMap);
			qMap.put(q, 0);
			workList.add(q);
			while(!workList.isEmpty()){
				Tuple ct = workList.remove(0);
				int ctdist = qMap.get(ct);
        List<ConstraintItem> cs = provenance.data.get(ct.getRelName());
        if (cs == null) continue;
        for (ConstraintItem ci : cs) {
          for(Tuple st : ci.getSubTuples()){
            if(this.updateDistance(qMap, ctdist+1, st)){
              workList.add(st);
            }
          }
				}
			}
		}
	}
	
	private boolean updateDistance(Map<Tuple, Integer> qMap, int nDist, Tuple t){
		Integer oDist = qMap.get(t);
		if(oDist == null){
			oDist = Integer.MAX_VALUE;
		}
		if(nDist < oDist){
			qMap.put(t, nDist);
			return true;
		}
		return false;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, Tuple t) {
		ArrayList<Double> ret = new ArrayList<Double>();
		double retV = 0.0;
		for(Map<Tuple,Integer> qMap : distMap.values()){
			Integer dist = qMap.get(t);
			if(dist == null)
				dist = Integer.MAX_VALUE;
			retV += dist;
		}
		ret.add(retV/distMap.size());
		return ret;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clear() {
		distMap = null;
	}

	@Override
	public String featureToString(ArrayList<Double> features) {
		return "Average distance to each query "+features;
	}

	
}
