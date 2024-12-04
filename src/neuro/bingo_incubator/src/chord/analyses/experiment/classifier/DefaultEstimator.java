package chord.analyses.experiment.classifier;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;
import chord.util.tuple.object.Pair;
import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.fail;
import static chord.util.SystemUtil.path;

public class DefaultEstimator extends Estimator {

	private Map<ArrayList<Double>, Pair<Integer, Integer>> countsOf = newHashMap();

	@Override
	protected double estimate(ArrayList<Double> features) {
		Pair<Integer, Integer> p = countsOf.get(features);
		if (p == null)
			return 0.0;
		double empProb = 1.0 * p.val1 / p.val0;
		return empProb; // Only reward good tuples

		// Alternative decision rule 1:
		// if (empProb >= 0.5) return 1.0;
		// else if (empProb < 0.5) return -1.0;
		// else return 0.0;

		// Alternative decision rule 2:
		// if(empProb < 0.5) return -1; //Only penalize bad tuples
		// else return 0;
	}

	@Override
	public void learnFromTuples(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor, Predicate<Tuple> selector) {
		Set<Tuple> seen = newHashSet();
		countsOf.clear();
    for (ConstraintItem c : provenance) {
      Tuple t = c.getHeadTuple();
      if (t != null && selector.apply(t) && !seen.contains(t)) {
        seen.add(t);
        ArrayList<Double> fs = extractor.features(provenance, t);
        Pair<Integer, Integer> p = countsOf.get(fs);
        if (p == null)
          countsOf.put(fs, p = mkPair(0, 0));
        ++p.val0;
        if (oracle.weight(t) > 0.0)
          ++p.val1;
      }
    }
	}

	@Override
	public void learnFromConstraints(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor, Predicate<ConstraintItem> selector) {
		Set<ConstraintItem> seen = Sets.newHashSet();
		countsOf.clear();
    for (ConstraintItem c : provenance) {
      if (c != null && selector.apply(c) && !seen.contains(c)) {
        seen.add(c);
        ArrayList<Double> fs = extractor.features(provenance, c);
        Pair<Integer, Integer> p = countsOf.get(fs);
        if (p == null)
          countsOf.put(fs, p = mkPair(0, 0));
        ++p.val0;
        if (oracle.weight(c) > 0.0)
          ++p.val1;
      }
    }
	}

	@Override
	public void save(String estimatorName) {
		try {
			PrintWriter pw = new PrintWriter(path(Config.outDirName, estimatorName));
			for (Entry<ArrayList<Double>, Pair<Integer, Integer>> e : countsOf.entrySet()) {
				for (Double d : e.getKey()) {
					pw.print(d + " ");
				}
				pw.println();
				pw.print(e.getValue().val0 + " ");
				pw.println(e.getValue().val1);
			}

			pw.flush();
			pw.close();
		} catch (IOException e) {
			fail(e);
		}
	}

	@Override
	public void load(String estimatorName) {
		countsOf.clear();
		File f = new File(path(Config.outDirName, estimatorName));
		Scanner sc;
		try {
			sc = new Scanner(f);
			while (sc.hasNext()) {
				ArrayList<Double> k = new ArrayList<Double>();
				String line = sc.nextLine();
				Scanner lineSc = new Scanner(line);
				while (lineSc.hasNext()) {
					String c = lineSc.next();
					if (!c.equals("")) {
						k.add(Double.parseDouble(c));
					}
				}

				line = sc.nextLine();
				lineSc = new Scanner(line);
				int p1 = lineSc.nextInt();
				int p2 = lineSc.nextInt();
				countsOf.put(k, new Pair<Integer, Integer>(p1, p2));
			}
		} catch (FileNotFoundException e) {
			fail(e);
		}

	}

	@Override
	public void clear() {
		countsOf.clear();
	}

	@Override
	public void printLearnedFacts(PrintStream out, FeatureExtractor extractor) {
		out.println("ESTIMATOR: DefaultEstimator learned following facts: ");
		for (Map.Entry<ArrayList<Double>, Pair<Integer, Integer>> entry : countsOf.entrySet()) {
			double value = 1.0 * entry.getValue().val1 / entry.getValue().val0;
			out.println("ESTIMATOR: " + extractor.featureToString(entry.getKey()) + ", " + value);
		}
	}

	@Override
	public void printLearnedFacts(PrintWriter out, FeatureExtractor extractor) {
		out.println("ESTIMATOR: DefaultEstimator learned following facts: ");
		for (Map.Entry<ArrayList<Double>, Pair<Integer, Integer>> entry : countsOf.entrySet()) {
			double value = 1.0 * entry.getValue().val1 / entry.getValue().val0;
			out.println("ESTIMATOR: " + extractor.featureToString(entry.getKey()) + ", " + value);
		}
	}

}
