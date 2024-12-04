package chord.analyses.experiment.classifier;

import static chord.util.CollectionUtil.newArrayList;
import static chord.util.CollectionUtil.newHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public class ExactFeatureExtractor extends FeatureExtractor{

	public ExactFeatureExtractor(Projector p) {
		super(p);
	}

	private static Map<String, Integer> indexOfName = newHashMap();
	private static int maxArgs = 0;
	private static int maxRels = 0;
	private static int maxTotalArgs = 0;
	private static boolean isInit = false;

  private void recordRelName(String n) {
    if (indexOfName.containsKey(n)) return;
    indexOfName.put(n, indexOfName.size());
  }

	private void init(Provenance provenance){
		if (!isInit) {
      for (String ruleid : provenance.data.keySet()) {
        LookUpRule r = LookUpRule.byId(ruleid);

        recordRelName(r.getHeadRelName());
        for (String srn : r.getSubRelNames())
          recordRelName(srn);

				int totalDoms = r.getHeadDomCnt();

				if(maxArgs < r.getHeadDomCnt())
					maxArgs = r.getHeadDomCnt();
				for(Integer domCnt : r.getSubDomCnts()){
					totalDoms += domCnt;
					if(maxArgs < domCnt)
						maxArgs = domCnt;
				}

				if(maxTotalArgs < totalDoms)
					maxTotalArgs = totalDoms;

				if(maxRels < (r.getSubRelNames().size() + 1))
					maxRels = r.getSubRelNames().size() + 1;
			}
			isInit = true;
		}
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, Tuple t) {
		init(provenance);
		t = p.project(t);
		ArrayList<Double> r = newArrayList();
		Integer idx = indexOfName.get(t.getRelName());
		if (idx == null) indexOfName.put(t.getRelName(), idx = indexOfName.size());
		r.add((double) idx);

		int[] indices = t.getIndices();
		for (int j = 0; j < indices.length; j++) r.add((double) indices[j]);
		for (int j = indices.length; j < maxArgs; j++) r.add(0.0);

		return r;
	}

	@Override
	public ArrayList<Double> features(Provenance provenance, ConstraintItem c) {
		init(provenance);
		ArrayList<Double> r = newArrayList();
		Tuple head = p.project(c.getHeadTuple());
		Integer idx = indexOfName.get(head.getRelName());
		if (idx == null) indexOfName.put(head.getRelName(), idx = indexOfName.size());
		r.add((double) idx);

		for(Tuple sub : c.getSubTuples()){
			idx = indexOfName.get(sub.getRelName());
			if (idx == null) indexOfName.put(sub.getRelName(), idx = indexOfName.size());
			r.add((double) idx);
		}

		for (int j = r.size(); j < maxRels; j++) r.add(0.0);

		int[] indices = head.getIndices();
		int totalArgs = indices.length;
		for (int j = 0; j < indices.length; j++) r.add((double) indices[j]);

		for(Tuple sub : c.getSubTuples()){
			Tuple t = p.project(sub);
			indices = t.getIndices();
			totalArgs += indices.length;
			for (int j = 0; j < indices.length; j++) r.add((double) indices[j]);
		}

		for (int j = totalArgs; j < maxTotalArgs; j++) r.add(0.0);

		return r;
	}

	@Override
	public void clear() {
	}

}
