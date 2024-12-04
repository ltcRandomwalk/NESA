package chord.analyses.experiment.classifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

import static com.google.common.base.Verify.verify;
import static chord.util.ExceptionUtil.fail;

// Assigns weights to rule instantiations according to a model learned offline,
// from multiple programs and queries.
public class RuleProbabilityModel implements Model {

  public RuleProbabilityModel() {
    probabilityWeightScale = Integer.getInteger(
      "chord.experiment.model.ruleProbability.scale", 10);
    Preconditions.checkArgument(
      probabilityWeightScale >= 0,
      "Scale for weights encoding probabilities must be nonnegative.");
  }

  @Override
  public void computeWeights(Provenance provenance, Set<Tuple> activeQueries) {
    activeQueries = null; // ignore
    Map<String, Integer> ruleWeights = Maps.newHashMap();
    for (String ruleId : provenance.data.keySet()) {
      Double probability = probabilityOfRule.get(ruleId);
      if (probability == null) {
        System.out.printf("RPM: Unknown probability for %s. Using %f.\n",
            ruleId, defaultProbability);
        probability = defaultProbability;
      }
      ruleWeights.put(ruleId, snap(Math.log(probability)));
    }
    if (debug()) {
      System.out.printf("DBG RPM: Probabilities above %f cause no bias.\n",
          Math.pow(epsilon, 1.0 / (1 + probabilityWeightScale)));
      for (Map.Entry<String, Integer> e : ruleWeights.entrySet()) {
        if (e.getValue() != 0) {
          System.out.printf("DBG RPM: weight[%s] = %d\n",
              e.getKey(), e.getValue());
        }
      }
    }

    constraintWeights.clear();
    for (Map.Entry<String, List<ConstraintItem>> e : provenance.data.entrySet()) {
      String ruleId = e.getKey();
      int weight = ruleWeights.get(ruleId);
      if (weight != 0) for (ConstraintItem c : e.getValue()) {
        constraintWeights.put(c, weight);
      }
    }
  }

  @Override
  public Map<Tuple, Integer> getTupleWeights() {
    return Maps.newHashMap();
  }

  @Override
  public Map<ConstraintItem, Integer> getConstraintWeights() {
    return Collections.unmodifiableMap(constraintWeights);
  }

  @Override
  public void learn(Provenance oracleProvenance, Provenance cheapProvenance) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(PrintWriter out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void load(BufferedReader in) {
    probabilityOfRule.clear();
    Scanner scan = new Scanner(in);
    while (scan.hasNextDouble()) {
      double p = scan.nextDouble();
      String id = scan.next();
      verify(0 <= p, "negative probability");
      verify(p <= 1, "supra-unitary probability");
      p = Math.max(epsilon, Math.min(1.0 - epsilon, p)); // soften
      probabilityOfRule.put(id, p);
    }
  }

  // Scales {@code w} and snaps it to an integer.
  private int snap(double w) {
    int wi = (int) Math.ceil(-w / Math.log(epsilon) * (probabilityWeightScale + 1));
    verify(-probabilityWeightScale-1 <= wi);
    verify(wi <= 0);
    if (wi == -probabilityWeightScale - 1) wi = -probabilityWeightScale;
    return wi;
  }

  static boolean debug() {
    return Boolean.getBoolean("chord.experiment.solver.debug");
  }

  private final Map<String, Double> probabilityOfRule = Maps.newHashMap();
  private final Map<ConstraintItem, Integer> constraintWeights =
    Maps.newHashMap();
  private int probabilityWeightScale;
  private static final double epsilon = 0.0001;
  private static final double defaultProbability = 0.5;
}

