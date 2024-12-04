package chord.analyses.experiment.kobj;

import static chord.util.ExceptionUtil.fail;
import static chord.util.RelUtil.domH;
import static chord.util.RelUtil.pRel;
import static chord.util.SystemUtil.path;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import chord.analyses.experiment.classifier.Estimator;
import chord.analyses.experiment.classifier.FeatureExtractor;
import chord.analyses.experiment.classifier.Model;
import chord.analyses.experiment.classifier.Oracle;
import chord.analyses.experiment.classifier.Projector;
import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Tuple;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.util.HasTimeout;
import chord.util.ProfileUtil;
import chord.util.Timeout;
import joeq.Compiler.Quad.Quad;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class Helper {

  static Predicate<Tuple> selectorTuples = new Predicate<Tuple>() {
    @Override public boolean apply(Tuple t) {
      return (!t.isA("Deny") && !t.isA("Allow") && !t.isA(clientQueryRelName()));
    };
  };

  static class ContainmentPredicate implements Predicate<Tuple>{
    private Set<Tuple> tuples;

    public ContainmentPredicate(Set<Tuple> tuples) {
      super();
      this.tuples = tuples;
    }

    @Override
    public boolean apply(Tuple arg0) {
      return tuples.contains(arg0);
    }

  }
  static Predicate<Tuple> selectorTuples(Set<Tuple> tuples){
    return new ContainmentPredicate(tuples);
  }

  static Predicate<ConstraintItem> selectorConstraints = new Predicate<ConstraintItem>() {
    @Override public boolean apply(ConstraintItem c) {
      return (!c.getHeadTuple().isA(clientQueryRelName()) /*&&
          (c.getHeadTuple().is("CVC") || c.getHeadTuple().is("CFC") || c.getHeadTuple().is("FC"))*/);
    };
  };

  static class ConvertToTuples implements Function<List<LookUpRule>, Iterable<String>> {
    @Override
    public Iterable<String> apply(List<LookUpRule> rules) {
      Projector p = newProjector(0);
      Set<String> tuples = Sets.newHashSet();
      for (LookUpRule r : rules) for (ConstraintItem c : r.viewAll()) {
        Tuple t = c.getHeadTuple();
        if (t != null) {
          String tName = p.project(t).toString();
          if (!tuples.contains(tName)) tuples.add(tName);
        }
      }
      return (new ArrayList<String>(tuples));
    }
  }

  static class ConvertToCountedTuples implements Function<List<LookUpRule>, Iterable<String>> {
    @Override
    public Iterable<String> apply(List<LookUpRule> rules) {
      Projector p = newProjector(0);
      Set<Tuple> seen = Sets.newHashSet();
      Map<String, Integer> countMap = Maps.newHashMap();
      for (LookUpRule r : rules) for (ConstraintItem c : r.viewAll()) {
        Tuple t = c.getHeadTuple();
        if (t != null && !seen.contains(t)) {
          seen.add(t);
          String tName = p.project(t).toString();
          Integer count = countMap.get(tName);
          if (count == null) {
            countMap.put(tName,1);
          } else {
            countMap.put(tName,count+1);
          }
        }
      }
      List<String> result = Lists.newArrayList();
      for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
        result.add(entry.getKey() + ": " + entry.getValue().toString());
      }
      return result;
    }
  }

  static class ConvertToCountedConstraintItems implements Function<List<LookUpRule>, Iterable<String>> {
    //Projector p = newProjector(0);
    Projector p = null;

    public ConvertToCountedConstraintItems(Projector p){
      this.p = p;
    }

    private Tuple project(Tuple t) {
      if (t == null) return t;
      else return p.project(t);
    }
    private ConstraintItem project(ConstraintItem c) {
      Tuple head = c.getHeadTuple();
      List<Tuple> subTuples = c.getSubTuples();
      Tuple newHead = project(head);
      List<Tuple> newSubTuples = Lists.newArrayList();
      for (Tuple t : subTuples) newSubTuples.add(project(t));
      return new ConstraintItem(newHead, newSubTuples, c.getHeadTupleSign(), c.getSubTuplesSign());
    }

    @Override
    public Iterable<String> apply(List<LookUpRule> rules) {
      Map<String, Integer> countMap = new TreeMap<String, Integer>();
      for (LookUpRule r : rules) for (ConstraintItem c : r.viewAll()) {
        String cProjName = project(c).toString();
        Integer count = countMap.get(cProjName);
        if (count == null) {
          countMap.put(cProjName,1);
        } else {
          countMap.put(cProjName,count+1);
        }
      }
      Set<String> result = new TreeSet<String>();
      for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
        result.add(entry.getKey() + ": " + entry.getValue().toString());
      }
      return result;
    }
  }

  static class Client {
    final String name, task, queryRel;
    Client(String name, String task, String queryRel)
    { this.name=name; this.task=task; this.queryRel=queryRel; }
  }

  static final String[] taskNames = {
    "cipa-0cfa-dlog",
    "exp-simple-pro-ctxts-java",
    "pro-argCopy-dlog",
    "exp-kobj-bit-init-dlog_XZ89_",
    "pro-cspa-kobj-dlog_XZ89_",
  };

  static final Client[] clients = {
    new Client("polysite", "polysite-dlog_XZ89_", "polySite"),
    new Client("all-polysite", "all-polysite-dlog_XZ89_", "polySite"),
    new Client("downcast", "pro-downcast-dlog_XZ89_", "unsafeDowncast"),
    new Client("datarace", "datarace-cs-noneg-dlog_XZ89_.dlog", "racePairs_cs"),
  };

  // choose feature extractor, estimator and oracle -- start {{{
  // NOTE: the three objects returned by newExtractor, newEstimator and newOracle
  // *must* be semantically compatible.

  // NOTE: I think the cause for repetition below is type erasure.
  // Is there a better way to write this code?

  @Deprecated
  public static FeatureExtractor newExtractor() {
    String c = System.getProperty(
        "chord.experiment.classifier.features",
        "chord.analyses.experiment.classifier.DefaultFeatureExtractor");
    try {
      return (FeatureExtractor) Class.forName(c).getConstructor(Projector.class).newInstance(newProjector(0));
    } catch (Exception e) {
      fail(String.format("Can't instantiate %s:%n%s", c, e));
      return null;
    }
  }

  @Deprecated
  public static Estimator newEstimator() {
    String c = System.getProperty(
        "chord.experiment.classifier.estimator",
        "chord.analyses.experiment.classifier.DefaultEstimator");
    try {
      return (Estimator) newI(c);
    } catch (Exception e) {
      fail(String.format("Can't instantiate %s:%n%s", c, e));
      return null;
    }
  }

  @Deprecated
  public static Oracle newOracle() {
    String c = System.getProperty(
        "chord.experiment.classifier.oracle",
        "chord.analyses.experiment.classifier.DefaultOracle");
    try {
      return (Oracle) Class.forName(c).getConstructor(Projector.class).newInstance(newProjector(0));
    } catch (Exception e) {
      fail(String.format("Can't instantiate %s:%n%s", c, e));
      return null;
    }
  }

  public static Model newModel() {
    String c = System.getProperty(
        "chord.experiment.model.class",
        "chord.analyses.experiment.classifier.DefaultModel");
    try {
      return (Model) newI(c);
    } catch (Exception e) {
      fail(String.format("Can't instantiate %s:%n%s", c, e));
      return null;
    }
  }

  static Projector newProjector(int k) {
    String c = System.getProperty(
        "chord.experiment.classifier.projector",
        "chord.analyses.experiment.kobj.DefaultProjector");
    try {
      return (Projector) Class.forName(c).getConstructor(Object.class).newInstance((Integer)k);
    } catch (Exception e) {
      fail(String.format("Can't instantiate %s:%n%s", c, e));
      return null;
    }
  }

  // choose feature extractor, estimator and oracle -- stop }}}

  static void saveTuples(List<LookUpRule> rules, String fileName) {
    Projector p = newProjector(0);
    Set<Tuple> seen = Sets.newHashSet();
    try {
      PrintWriter pw = new PrintWriter(path(Config.outDirName, fileName));
      for (LookUpRule r : rules) for (ConstraintItem c : r.viewAll()) {
        Tuple t = c.getHeadTuple();
        if (t != null) {
          Tuple t1 = p.project(t);
          if (!seen.contains(t1)) {
            seen.add(t1);
            pw.println(t1);
          }
        }
      }
      pw.flush(); pw.close();
    } catch (IOException e) {
      fail(e);
    }
  }

  static void saveResult(List<LookUpRule> rules, String fileName, Function<List<LookUpRule>, Iterable<String>> convert) {
    Iterable<String> results = convert.apply(rules);
    try {
      PrintWriter pw = new PrintWriter(path(Config.outDirName, fileName));
      for (String r : results) pw.println(r);
      pw.flush(); pw.close();
    } catch (IOException e) {
      fail(e);
    }
  }

  static void setAllParam(String rel, Abstraction abs) {
    ProgramRel exact = pRel(String.format("%sK", rel));
    exact.zero();
    for (Object o : domH()) {
      Quad q = (Quad) o;
      exact.add(q, abs.valueOf(q, rel));
    }
    exact.save();
  }

  static Set<Tuple> runAllTasks(List<ITask> tasks, Timeout timeout) {
    ProfileUtil.start("MAIN", "Helper.runAllTasks:runTasks");
    for (ITask t : tasks) {
      ProfileUtil.start("MAIN", "Helper.runAllTasks:"+t.getName());
      ClassicProject.g().resetTaskDone(t);
      if (t instanceof HasTimeout) { ((HasTimeout) t).setTimeout(timeout); }
      ClassicProject.g().runTask(t);
      ProfileUtil.stop("MAIN");
    }
    ProfileUtil.stop("MAIN");
    qRel().load();
    return tuples(qRel());
  }

  static Set<Tuple> tuples(final ProgramRel r) {
    Set<Tuple> ts = Sets.newHashSet();
    for (int[] args : r.getAryNIntTuples())
      ts.add(new Tuple(r, args));
    return ts;
  }

  static Client client() {
    String cn = System.getProperty("chord.experiment.client", clients[0].name);
    for (Client c : clients) if (cn.equals(c.name)) return c;
    fail("Unknown client: " + cn);
    return null;
  }

  static String clientTask() { return client().task; }
  static String clientQueryRelName() { return client().queryRel; }

  // DBG
  static void dumpenv(String k) {
    System.out.printf("============================MAIN: env(%s)=<%s>\n", k, System.getenv(k));
  }

  // HACK. Mimics TaskParser.buildDlogAnalysisMap, assuming that .config files
  // are placed in the same directory as their dlog file.  Ideally, the lookup
  // code is not duplicated for each extension.
  static String findConfig(String cn) throws IOException {
    cn = String.format("%s.config", cn);
    String[] fns = Config.dlogAnalysisPathName.split(File.pathSeparator);
    File[] fs = new File[fns.length];
    for (int i = 0; i < fs.length; ++i) fs[i] = new File(fns[i]);
    String r = findConfigRec(cn, fs);
    if (r == null) fail(String.format("Couldn't find %s", cn));
    return r;
  }

  static String findConfigRec(String cn, File[] fs) throws IOException {
    String r = null;
    for (File f : fs) {
      if (!f.exists()) continue;
      if (f.isDirectory()) {
        r = findConfigRec(cn, f.listFiles());
        if (r != null) return r;
      }
      else if(f.getName().endsWith(".jar")){//search in the jar file
                JarFile jarFile = new JarFile(f);
                Enumeration e = jarFile.entries();
                while (e.hasMoreElements()) {
                    JarEntry je = (JarEntry) e.nextElement();
                    String fileName2 = je.getName();
                    String fileNames[] = fileName2.split(File.separator);
                    if (fileNames[fileNames.length-1].equals(cn)) {
                        InputStream is = jarFile.getInputStream(je);
                        String fileName3 = OutDirUtils.copyResourceByPath(fileName2, is, "config");
                        return fileName3;
                    }
                }
      } else
        if (f.getName().equals(cn)) return f.getAbsolutePath();
    }
    return r;
  }

  static ProgramRel qRel() { return pRel(clientQueryRelName()); }
  static Object newI(String name) throws Exception {
    return Class.forName(name).newInstance();
  }

  // Differs from Boolean.getBoolean in how it handles the default.
  static boolean getBoolean(String p, boolean d) {
    String s = System.getProperty(p);
    if (s == null || s.equals("")) return d;
    return s.equalsIgnoreCase("true");
  }

}
