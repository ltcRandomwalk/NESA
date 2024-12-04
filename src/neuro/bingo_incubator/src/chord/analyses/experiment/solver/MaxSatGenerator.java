package chord.analyses.experiment.solver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.experiment.classifier.Model;
import chord.analyses.experiment.kobj.Abstraction;
import chord.bddbddb.Dom;
import chord.project.analyses.ProgramRel;
import chord.project.Config;
import chord.util.ProfileUtil;
import chord.util.tuple.object.Pair;
import joeq.Compiler.Quad.Quad;

import static chord.analyses.experiment.kobj.Abstraction.Parameter;
import static chord.analyses.experiment.kobj.Abstraction.ParameterValue;
import static chord.util.ExceptionUtil.fail;
import static chord.util.RelUtil.domH;
import static chord.util.RelUtil.pRel;
import static chord.util.SystemUtil.path;
import static com.google.common.base.Verify.verify;

/** Generate the constraint file within one iteration.

  @author xin (and then modified by rgrig, reusing code from hongseok)
 */
public class MaxSatGenerator {
    // model: It points to a model that will put a bias in our MaxSat encoding.
    // Intuitively, this model object identifies derived tuples that are likely to hold.
    // This information is used to bias a solution that our MaxSat solver will find.
    Model model;

    MaxSatSolver[] solvers;
    String problemName; // for debug, used as suffix for file names
    String configFiles[];
    String queryR;
    Provenance provenance;
    final ParamTupleConsHandler paramHandler;
    int queryWeight;
    ConstraintGenerator constraintGenerator;

    public MaxSatGenerator(
            String configFiles[],
            String queryR,
            ParamTupleConsHandler paramHandler,
            Model model
    ) {
        this.queryR = queryR;
        this.configFiles = configFiles;
        this.paramHandler = paramHandler;
        this.model = model;
        this.constraintGenerator = new ConstraintGenerator(paramHandler);
        this.maxAbstraction = Abstraction.cheapest();
    }

    public TupleIndex getTupleIndex() {
        return constraintGenerator.getTupleIndex();
    }

    // TODO(rg): Perhaps make these default/non-public and use the one above from outside?
    public int getIndex(Tuple t) { return getTupleIndex().getIndex(t); }
    public int getFreeIndex() { return getTupleIndex().getFreeIndex(); }
    public Tuple getTuple(int i) { return getTupleIndex().getTuple(i); }

    void update(Abstraction abstraction) {
        ProfileUtil.start("MAIN", "update");
        List<LookUpRule> rules = ResultLoader.getRules(configFiles);
        provenance = Provenance.of(rules);
        paramHandler.init(rules);
        constraintGenerator.update(provenance);
        maxAbstraction.max(abstraction);
        ProfileUtil.stop("MAIN", "update");
    }

    public static final class Result {
        public Abstraction newAbstraction;
        public final Set<Tuple> difficultQueries = Sets.newHashSet();
    }

    public Result solve(
            Set<Tuple> querySet,
            Abstraction abstraction,
            int maxK,
            boolean likelyPossible,
            boolean boolStrategy,
            String problemName
    ) {
        ProfileUtil.start("MAIN", "MXG.solve");
        this.problemName = problemName;
        update(abstraction);
        List<FormattedConstraint> clauses = buildMaxSatQuestion(
                querySet, maxK, likelyPossible, boolStrategy);
        List<Integer> assignment = askMaxSatSolver(clauses);
        Result result = interpretAssignment(
                assignment, querySet, likelyPossible, boolStrategy, maxK);
        constraintGenerator.getTupleIndex().unfreeze();
        if (assignment != null && debug()) calculateSolverObedience(assignment);
        ProfileUtil.stop("MAIN", "MXG.solve");
        return result;
    }

    // This function tries to generate the same question on different runs.
    private List<FormattedConstraint> buildMaxSatQuestion(
            Set<Tuple> querySet,
            int maxK,
            boolean likelyPossible,
            boolean boolStrategy
    ) {
        ProfileUtil.start("MAIN", "buildMaxSatQuestion");
        Preconditions.checkArgument(!querySet.isEmpty());

        // The names in parentheses are those from the paper.
        List<FormattedConstraint> derivation = Lists.newArrayList(); // hard (phi)
        List<FormattedConstraint> absValid = Lists.newArrayList(); // hard (delta)
        List<FormattedConstraint> absCost = Lists.newArrayList(); // soft (eta)
        List<FormattedConstraint> bias = Lists.newArrayList(); // soft
        List<FormattedConstraint> query = Lists.newArrayList(); // hard iff one query and model agrees

        // NOTE: Weights of 0 are adjusted later.

        // Say what it means for an abstraction to be valid.
        ProfileUtil.start("MAIN", "absValid");
        for (ParameterValue pv : maxAbstraction) {
            for (int k = 0; k < pv.value; ++k) {
                setParam(absValid, pv.parameter, k, -1);
            }
            if (pv.value == maxK) {
                setParam(absValid, pv.parameter, pv.value, +1);
            }
        }
        ProfileUtil.stop("MAIN", "absValid");

        // Set provenance constraints.
        ProfileUtil.start("MAIN", "derivation");
        int[][] cnf = null;
        if (likelyPossible) {
            cnf = constraintGenerator.getCnf();
        } else {
            List<Tuple> ps = Lists.newArrayList();
            for (ParameterValue pv : maxAbstraction) {
                ps.add(Abstraction.tuple(pv));
            }
            cnf = constraintGenerator.getReversedCnf(ps, querySet);
        }
        for (int[] c : cnf) derivation.add(FormattedConstraint.make(0, c));
        ProfileUtil.stop("MAIN", "derivation");

        // Prepare soft clauses.
        ProfileUtil.start("MAIN", "bias-prob");
        model.computeWeights(provenance, querySet);
        for (Map.Entry<Tuple, Integer> e : model.getTupleWeights().entrySet()) {
            if (e.getValue() != 0) {
                Tuple t = e.getKey();
                int w = e.getValue();
                bias.add(FormattedConstraint.unit(w, getIndex(t)));
            }
        }
        if (!likelyPossible) {
            Map<ConstraintItem, Integer> constraintWeights =
                model.getConstraintWeights();
            Map<Integer, ConstraintItem> auxiliaries =
                constraintGenerator.getAuxiliaries();
            for (Map.Entry<Integer, ConstraintItem> e : auxiliaries.entrySet()) {
                int u = e.getKey(); // the variable encoding an arc/constraint
                Integer w = constraintWeights.get(e.getValue());
                if (w != null) {
                    System.out.printf("DBG MXG: bias %d on %s\n", w, e.getValue());
                    bias.add(FormattedConstraint.unit(w, u));
                }
            }
        }
        ProfileUtil.stop("MAIN", "bias-prob");

        ProfileUtil.start("MAIN", "bias-cost");
        {
            int w = likelyPossible ? 1 : -1;
            for (ParameterValue pv : maxAbstraction) {
                absCost.add(FormattedConstraint.unit(
                    w, getIndex(Abstraction.tuple(pv))));
            }
        }
        for (Tuple q : querySet)
            query.add(FormattedConstraint.make(0, getIndex(q)));
        ProfileUtil.stop("MAIN", "bias-cost");

        // Adjust weights.
        // TODO(rg): Should I check for overflow?
        ProfileUtil.start("MAIN", "adjust");
        int sw = 1;
        for (FormattedConstraint c : bias) sw += c.weight();
        for (FormattedConstraint c : absCost) sw += c.weight();
        final int oqw = likelyPossible? -(sw + 1) : (sw + 1);
        int qw = 0;
        for (FormattedConstraint c : query) {
            c.unitWeight(oqw);
            verify(c.weight() == Math.abs(oqw));
            qw += c.weight();
        }
        int top = (query.size() == 1 && qw == sw + 1)? sw + 1 : sw + qw + 1;
        for (FormattedConstraint c : derivation) c.weight(top);
        for (FormattedConstraint c : absValid) c.weight(top);

        {   // Ensure that lack of progress manifests as UNSAT.
            int[] qs = new int[querySet.size()];
            int w = likelyPossible? -1 : 1;
            int i = 0;
            for (Tuple q : querySet) qs[i++] = w * getIndex(q);
            query.add(FormattedConstraint.make(top, qs));
        }
        ProfileUtil.stop("MAIN", "adjust");

        if (debug()) {
            ProfileUtil.start("MAIN", "debug_output");
            try {
                PrintWriter ew = new PrintWriter(path(
                    Config.outDirName, String.format("refine_%s.explicit", problemName)));
                saveExplicitClauses(ew, derivation, "Derivation");
                saveExplicitClauses(ew, absValid, "AbsValid");
                saveExplicitClauses(ew, absCost, "AbsCost");
                saveExplicitClauses(ew, query, "Query");
                saveExplicitClauses(ew, bias, "Bias");
                ew.flush(); ew.close();
                if (!likelyPossible) { // TODO update this code
                    // Also save before reversal.
                    ew = new PrintWriter(path(
                        Config.outDirName,
                        String.format("accumulated_%s.explicit", problemName)));
                    Set<Integer> reset = Sets.newHashSet();
                    List<FormattedConstraint> cs = Lists.newArrayList();
foo:
                    for (int[] c : constraintGenerator.getCnf()) {
                        for (int l : c) if (reset.contains(-l)) continue foo;
                        cs.add(FormattedConstraint.make(1, c));
                    }
                    saveExplicitClauses(ew, cs, "Provenance");
                    ew.flush(); ew.close();
                }
            } catch (IOException e) {
                System.out.println("MXG: WARNING: can't save refinement question.");
            }
            ProfileUtil.stop("MAIN", "debug_output");
        }

        ProfileUtil.start("MAIN", "collect");
        List<FormattedConstraint> result = Lists.newArrayList();
        result.addAll(derivation);
        result.addAll(absValid);
        result.addAll(absCost);
        result.addAll(query);
        result.addAll(bias);
        ProfileUtil.stop("MAIN", "collect");

        normalizeClauses(result);
        System.out.println("MXG: MaxSat question generated");
        ProfileUtil.stop("MAIN", "buildMaxSatQuestion");
        return result;
    }

    // This function is tightly connected to buildMaxSatQuestion
    private Result interpretAssignment(
        List<Integer> assignment,
        Set<Tuple> querySet,
        boolean likelyPossible,
        boolean boolStrategy,
        int maxK
    ) {
        Result result = new Result();
        if (assignment == null) { // UNSAT
            verify(likelyPossible);
            return result; // impossible, so no abstraction to try next
        }

        ProfileUtil.start("MAIN", "interpretAssignment");

        // Compute next abstraction to try.
        result.newAbstraction = new Abstraction();
        if (likelyPossible) {
            // If in maxAbstraction but not selected, then bump up.
            Set<Parameter> kept = Sets.newHashSet();
            for (int l : assignment) if (l > 0) {
                ParameterValue pv = ParameterValue.of(getTuple(l));
                if (pv == null) continue;
                if (pv.value == maxAbstraction.valueOf(pv.parameter)) {
                    kept.add(pv.parameter);
                }
            }

            if (false) { // XXX
                List<Parameter> toBump = Lists.newArrayList();
                for (ParameterValue pv : maxAbstraction) if (!kept.contains(pv.parameter)) {
                    toBump.add(pv.parameter);
                }
                System.out.printf("XXX toBump %s%n", toBump);
            }

            for (ParameterValue pv : maxAbstraction) {
                Parameter p = pv.parameter;
                int oldK = pv.value;
                int newK =
                    kept.contains(p)? oldK : (boolStrategy? maxK : oldK + 1);
                verify(newK <= maxK);
                result.newAbstraction.set(p, newK);
            }
        } else { // !likelyPossible
            // If all positive params are maxed out, then impossible.
            // Otherwise bump up to maxK those that aren't maxed out.
            result.newAbstraction.putAll(maxAbstraction);
            boolean impossible = true;
            boolean reallyImpossible = true;
            for (int l : assignment) if (l > 0) {
                Tuple t = getTuple(l);
                if (t == null) continue; // reversed constraints have auxiliaries
                ParameterValue pv = ParameterValue.of(getTuple(l));
                if (pv == null) continue;
                reallyImpossible = false;
                impossible &= pv.value == maxK;
                result.newAbstraction.set(pv.parameter, maxK);
            }
            if (reallyImpossible) {
                System.out.printf("MXG: REALLY_IMPOSSIBLE%n");
            }
            if (impossible) {
                result.newAbstraction = null;
            }
        }

        // Compute difficult queries. When likelyPossible, queries should
        // to not be selected if possible; if selected, they are difficult.
        // And vice-versa for !likelyPossible.
        for (int l : assignment) {
            Tuple t = getTuple(Math.abs(l));
            if (!querySet.contains(t)) continue;
            if (likelyPossible == (l > 0)) {
                result.difficultQueries.add(t);
            }
        }

        ProfileUtil.stop("MAIN", "interpretAssignment");
        return result;
    }


    private void calculateSolverObedience(List<Integer> assignment) {
        ProfileUtil.start("MAIN", "calculateSolverObedience");
        int posViolationCnt = 0;
        int posViolationWt = 0;
        int negViolationCnt = 0;
        int negViolationWt = 0;

        Set<Tuple> trueTuples = Sets.newHashSet();
        for (int i : assignment) {
            Tuple t = getTuple(Math.abs(i));
            if (i > 0)
                trueTuples.add(t);
        }
        for (Map.Entry<Tuple, Integer> e : model.getTupleWeights().entrySet()) {
            int w = e.getValue();
            if (w < 0) {
                if (trueTuples.contains(e.getKey())) {
                    negViolationCnt++;
                    negViolationWt += -w;
                }
            } else {
                if(!trueTuples.contains(e.getKey())){
                    posViolationCnt++;
                    posViolationWt += w;
                }
            }
        }

        long totalTupleWeight = 0;
        for (Integer w : model.getTupleWeights().values())
            totalTupleWeight += Math.abs(w);

        System.out.println("(DBG) MXG: # of positive violations by solver: " + posViolationCnt + ", Total weight of positive violations: " + posViolationWt);
        System.out.println("(DBG) MXG: # of negative violations by solver: " + negViolationCnt + ", Total weight of negative violations: " + negViolationWt);
        System.out.println("(DBG) MXG: # of violations by solver: " + (posViolationCnt + negViolationCnt) + ", Total weight of violations: " + (posViolationWt + negViolationCnt));
        System.out.println("(DBG) MXG: Number of suggestions by model: " + model.getTupleWeights().size()
                + ", Total weight of suggestions by model: " + totalTupleWeight);
        ProfileUtil.stop("MAIN", "calculateSolverObedience");
    }

    // NOTE(rg): At least on my computer, the clauses aren't always in the same
    // order, probably because the jre uses some randomization in hashing. This
    // makes mifumax return different answers (of same cost), which makes it
    // difficult to reproduce runs for debugging. The function
    // |normalizeClauses| tries to alleviate the problem just described.
    // (Google keyword, in case you are curious: "jdk.map.althashing.threshold")
    void normalizeClauses(List<FormattedConstraint> clauses) {
        ProfileUtil.start("MAIN", "MXG.normalizeClauses");
        for (FormattedConstraint c : clauses) c.normalize();
        Collections.sort(clauses);
        ProfileUtil.stop("MAIN", "MXG.normalizeClauses");
    }

    List<Integer> askMaxSatSolver(List<FormattedConstraint> clauses) {
        ProfileUtil.start("MAIN", "askMaxSatSolver");
        if (debug()) saveTupleIndex();

        if (solvers == null) setupSolvers();
        for (MaxSatSolver s : solvers) {
            System.out.printf("MXG: will now try solver %s%n", s.getClass().getName());
            try {
                List<Integer> assignment = s.solve(clauses, problemName);
                ProfileUtil.stop("MAIN", "askMaxSatSolver");
                return assignment;
            } catch (Exception e) {
                System.out.printf("MXG: WARNING: solver %s has thrown %s%n",
                        s.getClass().getName(), e);
                e.printStackTrace(System.out);
            }
        }
        fail("MXG: All solvers failed: ERROR or TIMEOUT");
        ProfileUtil.stop("MAIN", "askMaxSatSolver");
        return null;
    }

    public void saveGlobalProvenance(PrintWriter pw) {
        constraintGenerator.save(pw);
    }

    void saveDerivation(Set<Tuple> qs, List<Integer> assignment) {
        try {
            PrintWriter w = new PrintWriter(path(
                Config.outDirName, String.format("derivation_%s.explicit", problemName)));
            Set<Integer> vs = Sets.newHashSet(assignment);
            for (ConstraintItem c : provenance) {
                Tuple h = c.getHeadTuple();
                List<Tuple> bs = c.getSubTuples();
                boolean ok = true;
                ok &= getTupleIndex().hasIndex(h) && vs.contains(getIndex(h));
                for (Tuple b : bs) ok &= !getTupleIndex().hasIndex(b) || vs.contains(getIndex(b));
                if (!ok) continue;
                w.print(h);
                w.print(" <- ");
                List<String> ss = Lists.newArrayList();
                for (Tuple b : bs) if (getTupleIndex().hasIndex(b))
                    ss.add(b.toString());
                Collections.sort(ss);
                for (Tuple b : bs) if (!getTupleIndex().hasIndex(b))
                    ss.add("ZAPED_" + b.toString());
                boolean first = true;
                for (String s : ss) {
                    if (!first) w.print("&");
                    first = false;
                    w.print(s);
                }
                w.println();
            }
            w.flush(); w.close();
        } catch (IOException e) {
            System.out.printf("MXG: WARNING: can't save debug info%n%s%n", e);
        }
    }

    void saveExplicitClauses(
        PrintWriter w, List<FormattedConstraint> cs, String name
    ) {
        boolean b;
        for (FormattedConstraint c : cs) {
            w.printf("%s weight %d : ", name, c.weight());
            b = true;
            for (int x : c.constraint()) if (x > 0) {
                if (!b) w.print("|");
                b = false;
                Tuple t = getTuple(x);
                if (t == null) w.print("Aux" + x); else w.print(t);
            }
            w.print(" <- ");
            List<String> ss = Lists.newArrayList();
            for (int x : c.constraint()) if (x < 0) {
                Tuple t = getTuple(-x);
                if (t == null) ss.add("Aux" + (-x)); else ss.add(t.toString());
            }
            Collections.sort(ss);
            b = true;
            for (String s : ss) {
                if (!b) w.print("&");
                b = false;
                w.print(s);
            }
            w.println();
        }
    }

    void saveTupleIndex() {
        try {
            PrintWriter pwTemp = writer(String.format("tuple_%s.map",problemName));
            pwTemp.print(constraintGenerator.getTupleIndex().toString());
            pwTemp.flush();
            pwTemp.close();
        } catch (FileNotFoundException e) {
            System.out.printf("MXG: WARNING: can't save tuple map\n");
        }
    }

    PrintWriter writer(String fileName) throws FileNotFoundException {
        return new PrintWriter(new File(path(Config.outDirName, fileName)));
    }

    void setupSolvers() {
        String names = System.getProperty("chord.experiment.solvers");
        if (names == null) {
            solvers = new MaxSatSolver[] {
              new Mifumax(), new Mcsls(), new ApproxMaxSatSolver()
            };
        } else {
            String[] ns = names.split("[:;]");
            try {
              solvers = new MaxSatSolver[ns.length];
              for (int i = 0; i < ns.length; ++i)
                solvers[i] = (MaxSatSolver) Class.forName(ns[i]).newInstance();
            } catch (Exception e) {
                fail("Cannot setup solvers", e);
            }
        }
    }

    void setParam(
        List<FormattedConstraint> absValid,
        Parameter parameter,
        int value,
        int weight
    ) {
        absValid.add(FormattedConstraint.make(
            0, weight * getIndex(Abstraction.tuple(parameter, value))));
    }

    private Abstraction maxAbstraction;


    static boolean debug() {
        return Boolean.getBoolean("chord.experiment.solver.debug");
    }

    static private final boolean SAFE = true;
}

// vim:sw=4:ts=4:sts=4:
