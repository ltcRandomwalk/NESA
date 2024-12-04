package chord.analyses.experiment.classifier;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public abstract class Estimator {
	public static class TestResult {
		public int numPos = 0;
		public int falsePos = 0;
		public int numNeg = 0;
		public int falseNeg = 0;
	}

	protected abstract double estimate(ArrayList<Double> features);

	public abstract void learnFromTuples(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector
			);

	public abstract void learnFromConstraints(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<ConstraintItem> selector
			);

	public abstract void save(String filePath);

	public abstract void load(String filePath);


	public abstract void clear();

	public void learnFromTuples(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor) {
		learnFromTuples(oracle, queries, provenance, extractor, Predicates.<Tuple>alwaysTrue());
	}

	public void learnFromConstraints(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor) {
		learnFromConstraints(oracle, queries, provenance, extractor, Predicates.<ConstraintItem>alwaysTrue());
	}

	public void informDerivations(
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector) {
		return;
	}

	public int weightTuple(ArrayList<Double> features) {
		double estimation = estimate(features);
		return (estimation > 0.5) ? 1 : (estimation < -0.5 ? -1 : 0);
	}

	public int weightConstraint(ArrayList<Double> features) {
		//Returning 0 for a constraint is pointless. So the safe
		//default behavior is to say that the constraint should be present.
		//TODO:Can possibly return two different kinds of positive weights based on the confidence.
		double estimation = estimate(features);
		//return (estimation > -0.5) ? 1 : -1;
		estimation *= 100;
		if(((int)estimation) == 0) estimation = (estimation > 0) ? 1 : -1;
		return (int) (estimation);
	}

	public TestResult testFromTuples(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector
			) {
		TestResult result = new TestResult();
		Set<Tuple> seen = new HashSet<Tuple>();
    for (ConstraintItem c : provenance) {
			Tuple t = c.getHeadTuple();
			if (t != null && selector.apply(t) && !seen.contains(t)) {
				seen.add(t);
				ArrayList<Double> features = extractor.features(provenance, t);
				// Hongseok: At the moment, the use of weights is not entirely consistent.
				if (oracle.weight(t) > 0) {
					result.numPos++;
					int weight = weightTuple(features);
					if (!(weight > 0)) {
						result.falsePos++;
						System.out.print("(DBG) EST: Tuple " + t + " incorrectly excluded. ");
						System.out.println("Features: " + features + " Est. Weight: " + weight);
					}
				}
				else {
					result.numNeg++;
					int weight = weightTuple(features);
					if (!(weight < 0)) {
						result.falseNeg++;
						System.out.print("(DBG) EST: Tuple " + t + " incorrectly included. ");
						System.out.println("Features: " + features + " Est. Weight: " + weight);
					}
				}
			}
		}
		return result;
	}

	public TestResult testFromConstraints(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<ConstraintItem> selector
			) {
		TestResult result = new TestResult();
		Set<ConstraintItem> seen = new HashSet<ConstraintItem>();
    for (ConstraintItem c : provenance) {
			if (selector.apply(c) && !seen.contains(c)) {
				seen.add(c);
				ArrayList<Double> features = extractor.features(provenance, c);
				if (oracle.weight(c) > 0) {
					result.numPos++;
					if (!(weightConstraint(features) > 0)) {
						result.falsePos++;
						System.out.println(
							"(DBG) EST: Tuple incorrectly included. Constraint:"
							+ c + " Features:" + features);
					}
				}
				else {
					result.numNeg++;
					if (!(weightConstraint(features) < 0)) {
						result.falseNeg++;
						System.out.println(
							"(DBG) EST: Tuple incorrectly excluded. Constraint:"
							+ c + " Features:" + features);
					}
				}
			}
		}
		return result;
	}

	public TestResult testFromTuples(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor) {
		return testFromTuples(oracle, queries, provenance, extractor, Predicates.<Tuple>alwaysTrue());
	}

	public TestResult testFromConstraints(Oracle oracle, Set<Tuple> queries, Provenance provenance, FeatureExtractor extractor) {
		return testFromConstraints(oracle, queries, provenance, extractor, Predicates.<ConstraintItem>alwaysTrue());
	}

	public void printLearnedFacts(PrintStream out, FeatureExtractor extractor){
		out.println("TODO: implement method printLearnedFacts in "+this.getClass());
	}

	public void printLearnedFacts(PrintWriter out, FeatureExtractor featureExtractor) {
		out.println("TODO: implement method printLearnedFacts in "+this.getClass());
	}
}
