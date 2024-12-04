package chord.analyses.experiment.classifier;


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
import java.util.Random;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;
import chord.util.tuple.object.Pair;
import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.fail;
import static chord.util.SystemUtil.path;

public class ExactEstimator extends Estimator {

	private Set<ArrayList<Double>> featuresSet = Sets.newHashSet();
	private Random random = new Random();
	Set<ArrayList<Double>> reachingFeaturesSet = Sets.newHashSet();

	private int computeWeight(ArrayList<Double> features) {
                double estimation = estimate(features);
                return (estimation > 0.5) ? 1000 : (estimation < -0.5 ? -1000 : 0);
	}

	@Override
        public int weightTuple(ArrayList<Double> features) {
		return computeWeight(features);
        }

	@Override
        public int weightConstraint(ArrayList<Double> features) {
		return computeWeight(features);
	}

	private double corrupt(double v) {
		int n;
		if (v > 0.0) {
			n = random.nextInt(100);
			if (1 <= n && n < 80) {
				return v;
			} else if (n < 98) {
				return 0.0;
			} else {
				return -v;
			}
		} else if (v < 0.0) {
			n = random.nextInt(100);
			if (1 <= n && n < 80) {
				return v;
			} else if (n < 98) {
				return 0.0;
			} else {
				return -v;
			}
		} else {
			return v;
		}
	}

	@Override
	protected double estimate(ArrayList<Double> features) {
		if (!reachingFeaturesSet.contains(features)) {
			return 1.0;
		} else if (featuresSet.contains(features)) {
			return corrupt(1.0);
		} else {
			return corrupt(-1.0);
		}
	}

	@Override
	public void informDerivations(
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector
			) {
		Set<Tuple> reachingTuples = FeatureHelper.getReachingTuples(queries, provenance);
		for (Tuple t : reachingTuples) {
			if (t != null && selector.apply(t)) {
				ArrayList<Double> fs = extractor.features(provenance, t);
				reachingFeaturesSet.add(fs);
			}
		}
	}

	@Override
	public void learnFromTuples(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector
			) {
		featuresSet.clear();
		reachingFeaturesSet.clear();
		Set<Tuple> seen = newHashSet();
		Set<Tuple> tuples = oracle.getAllTuples();
		for (Tuple t : tuples) {
			if (t != null && selector.apply(t) && !seen.contains(t)) {
				seen.add(t);
				ArrayList<Double> fs = extractor.features(provenance, t);
				featuresSet.add(fs);
			}
		}
	}

	@Override
	public void learnFromConstraints(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<ConstraintItem> selector
			) {
		featuresSet.clear();
		reachingFeaturesSet.clear();
		Set<ConstraintItem> seen = newHashSet();
		Set<ConstraintItem> constraints = oracle.getAllConstraintItems();
		for (ConstraintItem c : constraints) {
			if (c != null && selector.apply(c) && !seen.contains(c)) {
				seen.add(c);
				ArrayList<Double> fs = extractor.features(provenance, c);
				featuresSet.add(fs);
			}
		}
	}

	@Override
	public void save(String estimatorName) {
		try {
			PrintWriter pw = new PrintWriter(path(Config.outDirName, estimatorName));
			for(ArrayList<Double> fs : featuresSet){
				for(Double d : fs){
					pw.print(d + " ");
				}
				pw.println();
			}
			pw.flush(); pw.close();
		} catch (IOException e) {
			fail(e);
		}
	}

	@Override
	public void load(String estimatorName) {
		featuresSet.clear();
		reachingFeaturesSet.clear();
		File f = new File(path(Config.outDirName, estimatorName));
		Scanner sc;
		try {
			sc = new Scanner(f);
			while (sc.hasNext()) {
				ArrayList<Double> fs = new ArrayList<Double>();
				String line = sc.nextLine();
				Scanner lineSc = new Scanner(line);
				while (lineSc.hasNext()){
					String c = lineSc.next();
					if (!c.equals("")){
						fs.add(Double.parseDouble(c));
					}
				}
				featuresSet.add(fs);
			}
		} catch (FileNotFoundException e) {
			fail(e);
		}

	}


	@Override
	public void clear() {
		featuresSet.clear();
		reachingFeaturesSet.clear();
	}
}
