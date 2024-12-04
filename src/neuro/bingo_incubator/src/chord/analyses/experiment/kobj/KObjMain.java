package chord.analyses.experiment.kobj;

import static chord.util.ExceptionUtil.fail;
import static chord.util.RelUtil.domH;
import static chord.util.RelUtil.domI;
import static chord.util.RelUtil.domK;
import static chord.util.RelUtil.pRel;
import static chord.util.SystemUtil.path;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.experiment.classifier.ComplicatedModel;
import chord.analyses.experiment.classifier.DefaultModel;
import chord.analyses.experiment.classifier.Estimator;
import chord.analyses.experiment.classifier.FeatureExtractor;
import chord.analyses.experiment.classifier.Model;
import chord.analyses.experiment.classifier.Oracle;
import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.MaxSatGenerator;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.ResultLoader;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.util.ChordTimeoutException;
import chord.util.ProfileUtil;
import chord.util.Timeout;
import chord.util.Utils;
import joeq.Compiler.Quad.Quad;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static com.google.common.base.Verify.verify;

/** For experiments. Do not rely on this.

  The relevant system properties are
    chord.experiment.accumulate conjoins constraints accross iterations (default false)
    chord.experiment.boolDomain=true/false: specify whether we want to use boolean domain based kobj (default false)
    chord.experiment.classifier.estimator is a class name (default chord.analyses.experiment.classifier.DefaultEstimator)
    chord.experiment.classifier.features is a class name (default chord.analyses.experiment.classifier.DefaultFeatureExtractor)
    chord.experiment.classifier.oracle is a class name (default chord.analyses.experiment.classifier.DefaultOracle)
    chord.experiment.classifier.projector is a class name (default chord.analyses.experiment.kobj.DefaultProjector)
    chord.experiment.client is one of polysite, downcast, datarace (default polysite)
    chord.experiment.debug is a boolean (default false)
    chord.experiment.estimatorName specifies the file name of the trained estimator to be saved at or loaded (default NULL)
    chord.experiment.immediateTest is a boolean (default false)
    chord.experiment.khighest is an int (default 10)
    chord.experiment.learn is a boolean (default false)
    chord.experiment.likelyPossible is a boolean (default true)
    chord.experiment.model.class (default chord.analyses.experiment.classifier.DefaultModel)
    chord.experiment.model.loadFile (default null)
    chord.experiment.model.ruleProbability.scale maximum weight bias to put on tuples (default 10)
    chord.experiment.model.saveFile (default null)
    chord.experiment.onlykLimitedQueries is an integer (default zero) that hackily controls what set of queries are used
    chord.experiment.onlyReportQueries (default false)
    chord.experiment.query is a semicolon-separated list of queries
    chord.experiment.runSeparate is a boolean that specifies if each query is resolved separately (default true)
    chord.experiment.saveGlobalProvenance is a boolean (default false)
    chord.experiment.solver.debug turns on some debugging output
    chord.experiment.solver.mcsls is the name of the mcsls executable (default mcsls; try mcsls-mac for mac)
    chord.experiment.solver.mcsls.alg is the name of the algorithm to use (default cld; other values: bls, els, bfd, efd)
    chord.experiment.solver.mcsls.num is an integer, higher means slower but better solutions (default 10)
    chord.experiment.solver.mcslspath is the full path to mcsls
    chord.experiment.solver.mifumax is the name of the mifumax executable (default mifumax)
    chord.experiment.solver.mifupath is the path to the mifumax executable
    chord.experiment.solver.timeout is an integer, the number of seconds
    chord.experiment.solvers is a list of class names of solvers, which should implement chord.analyses.experiment.solver.MaxSatSolver
    chord.experiment.timeout is the number of seconds after which the man loop's iterations stop
  plus those of other classes, in particular MaxSatGenerator.
 */
@Chord(name = "experiment")
public class KObjMain extends JavaAnalysis {
  // These defaults should agree with the comment on the class.
  private String estimatorName = null; // deprecate
  private String loadModelFile = null;
  private String saveModelFile = null;
  private boolean ifBool = false;
  private boolean immediateTest = false;
  private boolean learn = false;
  private boolean likelyPossible = true;
  private boolean onlyReportQueries = false;
  private boolean runSeparate = true;
  private boolean saveGlobalProvenance = false;
  private int maxK = 10;
  private int timeout = -1;

  private long startTime;

  private List<ITask> tasks;
  private String[] configFiles;
  private int baCnt = 0; // for debug, mostly


  @Override public void run() {
    ProfileUtil.start("MAIN", "run");
    startTime = System.nanoTime();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override public void run() { ProfileUtil.printStats(System.err, 2); }
    });

    try {
      Set<Tuple> qs = null;
      try { qs = initializeAnalysis(); }
      catch (IOException e) { fail("can't initialize analysis", e); }
      StringBuilder def = new StringBuilder();
      for (Tuple q : qs) def.append(String.format("%s;", q));
      if (onlyReportQueries) {
        System.out.printf("MAIN: QUERIES");
        for (Tuple q : qs) System.out.printf(" %s", q);
        System.out.printf("\n");
        return;
      }

      Set<Tuple> rs = Sets.newHashSet();
      Integer onlykLimitedQueries = Integer.getInteger("chord.experiment.onlykLimitedQueries", 0);
      if (onlykLimitedQueries != 0) {
        String t = System.getProperty("chord.experiment.query");
        if (t != null && !t.equals(""))
          System.out.println("MAIN: WARNING: onlykLimitedQueries overrides query");
        Set<Tuple> filteredQs = runAnalysis(Abstraction.constant(onlykLimitedQueries));
        rs.addAll(qs);
        rs.removeAll(filteredQs);
      } else {
        String t = System.getProperty("chord.experiment.query", def.toString());
        for (String s : t.split(";")) if (!"".equals(s)) {
          Tuple q = new Tuple(s);
          if (qs.contains(q)) rs.add(q);
          else System.out.printf("MAIN: Query %s not derived by least precise analysis.%n", q);
        }
      }
      System.out.printf("MAIN: target queries: %s%n", rs.toString());

      if (runSeparate)
        for (Tuple q : rs) solve(Sets.newHashSet(q));
      else
        solve(rs);
    } catch (ChordTimeoutException e) {
      System.out.printf("MAIN: TIMEOUT timestamp %d%n", System.nanoTime());
    }
    ProfileUtil.stop("MAIN");
    ProfileUtil.printStats(System.err, 10);
    // TODO(rg): Why does Java not run the hook on normal shutdown?
  }

  private Set<Tuple> initializeAnalysis() throws IOException {
    ProfileUtil.start("MAIN", "initializeAnalysis");
    ClassicProject p = ClassicProject.g();

    tasks = Lists.newArrayList();
    for (String n : Helper.taskNames) tasks.add(p.getTask(n));
    tasks.add(p.getTask(Helper.clientTask()));

    configFiles = new String[] {
        "exp-kobj-bit-init-dlog_XZ89_",
        "pro-cspa-kobj-dlog_XZ89_",
        Helper.clientTask()
    };
    for (int i = 0; i < configFiles.length; ++i)
      configFiles[i] = Helper.findConfig(configFiles[i]);

    ifBool = Helper.getBoolean("chord.experiment.boolDomain", ifBool);
    immediateTest = Helper.getBoolean("chord.experiment.immediateTest", immediateTest);
    learn = Helper.getBoolean("chord.experiment.learn", learn);
    likelyPossible = Helper.getBoolean("chord.experiment.likelyPossible", likelyPossible);
    onlyReportQueries = Helper.getBoolean("chord.experiment.onlyReportQueries", onlyReportQueries);
    runSeparate = Helper.getBoolean("chord.experiment.runSeparate", runSeparate);
    saveGlobalProvenance = Helper.getBoolean("chord.experiment.saveGlobalProvenance", saveGlobalProvenance);

    maxK = Integer.getInteger("chord.experiment.khighest", maxK);
    timeout = Integer.getInteger("chord.experiment.timeout", timeout);

    if (timeout > 0) {
      timeoutChecker = new KTimeout(System.nanoTime() + timeout * 1000000000l);
    } else {
      timeoutChecker = null;
    }

    estimatorName = System.getProperty("chord.experiment.estimatorName", estimatorName);
    loadModelFile = System.getProperty("chord.experiment.model.loadFile", loadModelFile);
    saveModelFile = System.getProperty("chord.experiment.model.saveFile", saveModelFile);

    if (!likelyPossible) {
      if (!ifBool) {
        System.out.printf("MAIN: boolean strategy is implied by !likelyPossible");
      }
      ifBool = true;
    }

    System.setProperty("chord.ctxt.kind", "co");
    System.setProperty("chord.kobj.khighest", "" + maxK);
    System.setProperty("chord.kcfa.khighest", "" + maxK);

    p.runTask(domI());
    p.runTask(domK());
    p.runTask(domH());

    pRel("IK").zero();
    for (int i = 0; i < domI().size(); ++i)
      pRel("IK").add((Quad) domI().get(i), 0);
    pRel("IK").save();

    Set<Tuple> qs = runAnalysis(Abstraction.cheapest());
    ProfileUtil.stop("MAIN", "initializeAnalysis");
    return qs;
  }

  private void trainModelOn(Model model, Set<Tuple> queries) {
    System.out.printf("MAIN: trainModelOn (start");
    solveUsing(queries, model);
    Provenance oracleProvenance =
      Provenance.of(ResultLoader.getRules(configFiles));
    runAnalysis(Abstraction.cheapest());
    Provenance cheapProvenance =
      Provenance.of(ResultLoader.getRules(configFiles));
    model.learn(oracleProvenance, cheapProvenance);
    System.out.printf("Main: trainModelOn stop)");
  }

  private void solve(Set<Tuple> qs) {
    ProfileUtil.start("MAIN", "solve");
    System.out.printf("MAIN: solve %s (start%n", describeQs(qs));
    Model model = Helper.newModel();
    loadModelFile(model);
    if (learn) {
      trainModelOn(model, qs);
      if (immediateTest) {
        solveUsing(qs, model);
      }
    } else {
      solveUsing(qs, model);
    }
    saveModelFile(model);
    System.out.printf("MAIN: solve stop)%n");
    ProfileUtil.stop("MAIN");
  }

  private void loadModelFile(Model m) {
    if (loadModelFile == null) return;
    try {
      BufferedReader br = new BufferedReader(new FileReader(loadModelFile));
      m.load(br);
      br.close();
    } catch (IOException e) {
      fail("Cannot load model file", e);
    }
  }

  private void saveModelFile(Model m) {
    if (saveModelFile == null) return;
    try {
      PrintWriter pw = new PrintWriter(new FileWriter(saveModelFile));
      m.save(pw);
      pw.flush(); pw.close();
    } catch (IOException e) {
      fail("Cannot save model file", e);
    }
  }

  static class QueriesByKind {
    public Set<Tuple> unresolved;
    public Set<Tuple> impossible;
    public Set<Tuple> difficult;
    public Set<Tuple> ruledOut;
    QueriesByKind() {
      this.unresolved = Sets.newHashSet();
      this.impossible = Sets.newHashSet();
      this.difficult = Sets.newHashSet();
      this.ruledOut = Sets.newHashSet();
    }
    void print(PrintStream out) {
      print(out, "unresolved", unresolved);
      print(out, "impossible", impossible);
      print(out, "difficult", difficult);
      print(out, "ruled out", ruledOut);
    }
    private void print(PrintStream out, String name, Set<Tuple> set) {
      out.printf("MAIN: %s queries: %d %s%n", name, set.size(), set);
    }

    @Override
    public String toString() {
      return "QueriesByKind [unresolved=" + unresolved + ", impossible=" + impossible + ", difficult=" + difficult + ", ruledOut=" + ruledOut + "]";
    }
  }

  private QueriesByKind solveUsing(Set<Tuple> qs, Model model) {
    final String profileLabel = String.format("solveUsing(%s)", qs);
    ProfileUtil.start("MAIN", profileLabel);
    MaxSatGenerator maxsat = newMaxSatGenerator(model);
    Abstraction abstraction = null;
    int count = 0;

    QueriesByKind r = new QueriesByKind();
    r.difficult = qs;

    while (!r.unresolved.isEmpty() || !r.difficult.isEmpty()) {
      ++count;
      logVisualMarker();
      if (r.unresolved.isEmpty()) {
        abstraction = Abstraction.cheapest();
        Set<Tuple> t = r.difficult; r.difficult = r.unresolved; r.unresolved = t;
      }
      System.out.printf(
          "MAIN: solveUsing iteration %d absCost %d timestamp %d%n",
          count, abstraction.cost(), System.nanoTime());
      r.print(System.out);
      if (timeoutChecker != null) timeoutChecker.check();
      Set<Tuple> retQs = runAnalysis(abstraction);
      for (Tuple t : r.unresolved) if (!retQs.contains(t)) r.ruledOut.add(t);
      r.unresolved.retainAll(retQs);
      if (r.unresolved.isEmpty()) break;
      abstraction = bestAbstraction(r, maxsat, abstraction);
    }
    System.out.printf(
        "MAIN: solveUsing loop-end timestamp %d%n", System.nanoTime());
    r.print(System.out);

    saveGlobalProvenance(maxsat);
    ProfileUtil.stop("MAIN", profileLabel);
    return r;
  }


  private void saveGlobalProvenance(MaxSatGenerator maxsat) {
    if (!saveGlobalProvenance) return;
    ProfileUtil.start("MAIN", "saveGlobalProvenance");
    try {
      String fn = String.format(
          "provenance_%08x", (int) System.currentTimeMillis());
      System.out.printf("MAIN: save global provenance to %s%n", fn);
      PrintWriter pw = new PrintWriter(path(Config.outDirName, fn));
      maxsat.saveGlobalProvenance(pw);
      pw.flush(); pw.close();
    } catch (IOException e) {
      fail("Can't save global provenance.", e);
    }
    ProfileUtil.stop("MAIN", "saveGlobalProvenance");
  }

  private Abstraction bestAbstraction(
      QueriesByKind qs,
      MaxSatGenerator maxsat,
      Abstraction oldAbs
  ) {
    ProfileUtil.start("MAIN", "bestAbstraction");
    logVisualMarker();
    final String problemName =
      String.format("%05d_%s", ++baCnt, describeQs(qs.unresolved));
    MaxSatGenerator.Result maxsatAnswer =
      maxsat.solve(
          qs.unresolved,
          oldAbs,
          maxK,
          likelyPossible,
          ifBool,
          problemName);
    qs.difficult.addAll(maxsatAnswer.difficultQueries);
    qs.unresolved.removeAll(maxsatAnswer.difficultQueries);
    if (maxsatAnswer.newAbstraction == null) { // impossible
      qs.impossible.addAll(qs.unresolved);
      qs.unresolved.clear();
    }
    ProfileUtil.stop("MAIN", "bestAbstraction");
    return maxsatAnswer.newAbstraction;
  }


  private MaxSatGenerator newMaxSatGenerator(Model model) {
    Preconditions.checkNotNull(model);
    MaxSatGenerator maxsat = new MaxSatGenerator(
        configFiles,
        Helper.clientQueryRelName(),
        new PTHandler(true, ifBool, maxK, maxK),
        model);
    return maxsat;
  }

  private Set<Tuple> runAnalysis(Abstraction abstraction) {
    ProfileUtil.start("MAIN", "runAnalysis");
    logVisualMarker();
    if (debug()) {
      System.out.printf("MAIN: runAnalysis abstraction %d %s%n",
          abstraction.cost(), abstraction);
    }
    Helper.setAllParam("H", abstraction);
    Helper.setAllParam("O", abstraction);
    Set<Tuple> r = Helper.runAllTasks(tasks, timeoutChecker);
    ProfileUtil.stop("MAIN");
    return r;
  }

  private Timeout timeoutChecker;

  static private void logVisualMarker() {
    System.out.printf("MAIN: %s\n", Strings.repeat("=", 74));
  }

  static private boolean debug() {
    return Boolean.getBoolean("chord.experiment.debug");
  }

  static private <T> String describeQs(Iterable<T> qs) {
    Iterator<T> i = qs.iterator();
    if (!i.hasNext()) return "none";
    String r = i.next().toString();
    if (i.hasNext()) return "all";
    return r;
  }

  static final class KTimeout implements Timeout {
    public KTimeout(long stopTime) { this.stopTime = stopTime; }
    @Override public void check() {
      if (System.nanoTime() >= stopTime)
        throw new ChordTimeoutException();
    }
    private long stopTime;
  }
}

// vim:fdm=marker:fmr={{{,}}}:sw=2:ts=2:
