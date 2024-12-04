package chord.analyses.experiment.classifier;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import chord.analyses.experiment.kobj.Helper;
import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.util.tuple.object.Pair;

import static chord.util.CollectionUtil.mkPair;
import static chord.util.ExceptionUtil.fail;
import static com.google.common.base.Verify.verify;

// DBG
import java.util.Map;

// RG: The old design is a Frankenstein monster. My original design was too
// restrictive. Instead of changing the design, it was patched here and there.
// The result is horrendous, in my opinion. This class is here for backward
// compatibility: I don't have time to update all old code to something more
// sensible.
public class ComplicatedModel implements Model {

  private final Oracle oracle;
  private final Estimator estimator;
  private final FeatureExtractor extractor;
  private final Set<Pair<Tuple,Integer>> weightedTuples;
  private final Set<Pair<ConstraintItem,Integer>> weightedConstraints;
  private final Predicate<Tuple> selTuples;
  private final Predicate<ConstraintItem> selCons;

  public ComplicatedModel() {
    oracle = Helper.newOracle();
    estimator = Helper.newEstimator();
    extractor = Helper.newExtractor();
    weightedTuples = Sets.newHashSet();
    weightedConstraints = Sets.newHashSet();

    // TODO: The following two should be configurable from options,
    // just like Oracle, estimator, extractor from above
    selTuples = Predicates.<Tuple>alwaysTrue();
    selCons = Predicates.<ConstraintItem>alwaysTrue();
  }

  // Compute weights for all the derived tuples, which are given in the form of
  // instantiated rules. This computation uses the estimator and the extractor.
  public void buildTuples(Set<Tuple> queries, Provenance provenance) {
    weightedTuples.clear();
    extractor.prebuild(provenance, queries);
    estimator.informDerivations(queries, provenance, extractor, selTuples);
    for (ConstraintItem c : provenance) {
      Tuple t = c.getHeadTuple();
      if (t != null && selTuples.apply(t)) {
        ArrayList<Double> fs = extractor.features(provenance, t);
        Integer w = estimator.weightTuple(fs);
        if (w != 0) weightedTuples.add(mkPair(t, w));
      }
    }
  }

  // The returned set should be used in the read-only mode.
  public Set<Pair<Tuple,Integer>> getWeightedTuples() {
    return weightedTuples;
  }

  public void buildConstraints(Set<Tuple> queries, Provenance provenance) {
    weightedConstraints.clear();
    for (ConstraintItem c : provenance) if (selCons.apply(c)) {
      ArrayList<Double> fs = extractor.features(provenance, c);
      Integer w = estimator.weightConstraint(fs);
      if (w == 0) fail("Weight for a soft constraint should never be zero: " + fs + ":" + c);
      weightedConstraints.add(mkPair(c, w));
    }
  }

  // The returned set should be used in the read-only mode.
  public Set<Pair<ConstraintItem,Integer>> getWeightedConstraints() {
    return weightedConstraints;
  }

  // Adapter code, to new interface. {{{
  public void computeWeights(Provenance provenance, Set<Tuple> activeQueries) {
    Preconditions.checkNotNull(activeQueries);
    this.activeQueries = activeQueries;
    buildTuples(activeQueries, provenance);
    buildConstraints(activeQueries, provenance);
  }

  public void learn(Provenance oracleProvenance, Provenance cheapProvenance) {
    Preconditions.checkState(activeQueries != null);
    oracle.init(oracleProvenance);
    extractor.prebuild(cheapProvenance, activeQueries);
    estimator.learnFromTuples(oracle, activeQueries, cheapProvenance, extractor);
    estimator.learnFromConstraints(oracle, activeQueries, cheapProvenance, extractor);
  }

  public Map<Tuple, Integer> getTupleWeights() {
    Map<Tuple, Integer> r = Maps.newHashMap();
    for (Pair<Tuple, Integer> p : weightedTuples) {
      verify(!r.containsKey(p.val0));
      r.put(p.val0, p.val1);
    }
    return r;
  }

  public Map<ConstraintItem, Integer> getConstraintWeights() {
    Map<ConstraintItem, Integer> r = Maps.newHashMap();
    for (Pair<ConstraintItem, Integer> p : weightedConstraints) {
      verify(!r.containsKey(p.val0));
      r.put(p.val0, p.val1);
    }
    return r;
  }

  public void save(PrintWriter out) {
    try {
      File f = File.createTempFile("estimator", "save");
      estimator.save(f.toString());
      BufferedReader br = new BufferedReader(new FileReader(f));
      String line;
      while ((line = br.readLine()) != null) out.println(line);
      br.close();
    } catch (IOException e) {
      fail(e);
    }
  }

  public void load(BufferedReader in) {
    try {
      File f = File.createTempFile("estimator", "load");
      PrintWriter pw = new PrintWriter(new FileWriter(f));
      String line;
      while ((line = in.readLine()) != null) pw.println(line);
      pw.flush(); pw.close();
      estimator.load(f.toString());
    } catch (IOException e) {
      fail(e);
    }
  }

  private Set<Tuple> activeQueries;
  // }}}

  static private final double EPSILON = 1e-9;
}
