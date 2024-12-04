package chord.analyses.experiment.classifier;

import com.google.common.base.Predicate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import chord.analyses.experiment.kobj.DefaultProjector;
import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;
import chord.util.tuple.object.Pair;
import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.fail;

public class FrequencyEstimator extends Estimator {

	private Map<ArrayList<Double>,Pair<Integer, Integer>> countsOf = newHashMap();


	@Override protected double estimate(ArrayList<Double> features) {
		Pair<Integer, Integer> p = countsOf.get(features);
		if (p == null) return 0.0;
		return ((1.0 * p.val1) / p.val0); //Only reward good tuples
	//	if(((1.0 * p.val1) / p.val0) < 0.5) return -1; //Only penalize bad tuples
	//	else return 0;
	}

	@Override public void learnFromTuples(
		Oracle oracle,
		Set<Tuple> queries,
		Provenance provenance,
		FeatureExtractor extractor,
		Predicate<Tuple> selector
	) {
		fail("Not implemented");
	}

	@Override
	public void learnFromConstraints(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<ConstraintItem> selector
			) {
		Projector proj = new DefaultProjector(0);
		Set<ConstraintItem> seen = newHashSet();
		countsOf.clear();
    for (ConstraintItem c : provenance) {
      Tuple head = proj.project(c.getHeadTuple());
      List<Tuple> subT = new ArrayList<Tuple>();
      for(Tuple sub : c.getSubTuples()) subT.add(proj.project(sub));
      ConstraintItem ci = new ConstraintItem(head, subT, c.getHeadTupleSign(), c.getSubTuplesSign());

      if (selector.apply(c) && !seen.contains(ci)) {
        seen.add(ci);
        ArrayList<Double> fs = extractor.features(provenance, ci);
        Pair<Integer, Integer> p = countsOf.get(fs);
        if (p == null) countsOf.put(fs, p = mkPair(0, 0));
        ++p.val0;
      }
		}
		seen.clear();
		for(ConstraintItem c : oracle.getAllConstraintItems()){
			if (c != null && selector.apply(c) && !seen.contains(c)) {
				seen.add(c);
				ArrayList<Double> fs = extractor.features(provenance, c);
			//	System.out.println("(DBG) " + fs + ":" + c);
				Pair<Integer, Integer> p = countsOf.get(fs);
				if (p == null) {
					System.out.println("Oracle contains instatiations of a rule that is not used for imprecise analysis: " + c);
					continue; //fail("Oracle contains instatiations of a rule that is not used for imprecise analysis: " + c);
				}
				++p.val1;
			}
		}
		for(ArrayList<Double> key : countsOf.keySet()){
			System.out.println("(DBG) " + key + ":" + (countsOf.get(key).val1 * 1.0)/countsOf.get(key).val0 );
		}
	}

	@Override
	public void save(String estimatorName) {
		fail("Not implemented");
	}

	@Override
	public void load(String estimatorName) {
		fail("Not implemented");
	}

	@Override
	public void clear() {
		countsOf.clear();
	}
}
