package chord.analyses.experiment.solver;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import chord.project.Config;
import static chord.util.ExceptionUtil.fail;
import static chord.util.SystemUtil.path;

public class Mcsls extends WcnfSolver {
  @Override public List<Integer> solve(
      List<FormattedConstraint> toSolve,
      String problemName
  ) {
    // NOTE: toArray may pad with null
    // Could be seen as an optimization or a leak, depending on your mood.
    clauses = toSolve.toArray(clauses);

    TreeMap<Integer, List<Integer>> byWeight = Maps.newTreeMap();
    for (int i = 0; i < toSolve.size(); ++i) {
      List<Integer> cs = byWeight.get(clauses[i].weight());
      if (cs == null) cs = Lists.newArrayList();
      cs.add(i);
      byWeight.put(clauses[i].weight(), cs);
    }
    long totalWeight = 0;
    for (Map.Entry<Integer, List<Integer>> e : byWeight.entrySet()) {
      if (e.getKey() <= totalWeight)
        fail("Mcsls doesn't know how to solve non-lexicographic weights");
      totalWeight += ((long)e.getKey()) * e.getValue().size();
    }

    List<List<Integer>> groups = Lists.newArrayList(byWeight.values());
    ArrayList<Integer> softClauses = Lists.newArrayList();
    List<Integer> hardClauses = groups.get(groups.size() - 1);
    groups.remove(groups.size() - 1);
    Collections.reverse(groups);
    for (List<Integer> g : groups) {
      softClauses.addAll(g);
      softClauses = solve(softClauses, hardClauses);
      int i, j;
      for (i = j = 0; j < softClauses.size(); ++j) {
        int c = softClauses.get(j);
        if (c < 0) softClauses.set(i++, -c);
        else hardClauses.add(c);
      }
      softClauses.subList(i, softClauses.size()).clear();
    }
    return assignmentOfClauses(softClauses, hardClauses);
  }

  // All integers (in softClauses, hardClauses, and return) are indices in
  // {@code clauses}. If hardClauses can be satisfied, then the result is
  // almost the same as softClauses, except that the sign of those soft clauses
  // that weren't satisfied has been flipped.  Returns null iff hardClauses
  // can't be satisfied.
  private ArrayList<Integer> solve(List<Integer> softClauses, List<Integer> hardClauses) {
    final int m = softClauses.size();
    final int n = hardClauses.size();

    List<FormattedConstraint> toAsk = Lists.newArrayList();
    for (int i : softClauses) toAsk.add(clauses[i].withWeight(1));
    for (int i : hardClauses) toAsk.add(clauses[i].withWeight(Math.max(m + 1, 2)));
      // NOTE: mcsls misbehaves if hard clauses have weight 1

    try {
      ++stepCnt;
      File qf = new File(path(
          Config.outDirName, String.format("mcsls_%05d.wcnf", stepCnt)));
      PrintWriter qw = new PrintWriter(qf);
      saveWcnf(toAsk, qw);
      qw.flush(); qw.close();

      long startTime = System.nanoTime();
      String timeout =
          System.getProperty("chord.experiment.solver.timeout", "60");
      String numlimit =
          System.getProperty("chord.experiment.solver.mcsls.num", "10");
      String alg =
          System.getProperty("chord.experiment.solver.mcsls.alg", "cld");

      ProcessBuilder pb = new ProcessBuilder(
          mcslsPath, "-T", timeout, "-num", numlimit, "-alg", alg,
          qf.getAbsolutePath());
      pb.redirectErrorStream(true); // otherwise it might fill the pipe and block
      final Process p = pb.start();
      final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
      File mcslsOutput = new File(path(
          Config.outDirName, String.format("mcsls_%05d.out", stepCnt)));
      final PrintWriter rpw = new PrintWriter(mcslsOutput);

      String line;
      while ((line = in.readLine()) != null) rpw.println(line);
      in.close();
      rpw.flush(); rpw.close();

      if (p.waitFor() != 0) fail("mcsls returned non-zero.");
      long stopTime = System.nanoTime();
      System.out.printf("MCSLS: mcsls done in %.01f seconds%n",
          (1e-9)*(stopTime-startTime));

      Set<Integer> mcs = Sets.newHashSet(parseMcs(mcslsOutput));
      ArrayList<Integer> result = Lists.newArrayList();
      int i = 0;
      for (int j : softClauses) {
        ++i;
        // here, i is index in mcsls's output; j is index in {@code clauses}
        result.add(mcs.contains(i)? -j : j);
      }
      if (SAFE) {
        int mcsSize = 0;
        if (result.size() != softClauses.size()) fail("Internal error.");
        for (i = 0; i < result.size(); ++i) {
          if (result.get(i) < 0) ++mcsSize;
          if (Math.abs(result.get(i)) != Math.abs(softClauses.get(i)))
            fail("Internal error.");
        }
        if (mcs.size() != mcsSize) fail("Internal error.");
      }
      return result;
    } catch (Exception e) {
      fail("MCSLS: Error running mcsls.", e);
    }
    return null;
  }

  private List<Integer> parseMcs(File f) throws IOException {
    Scanner file = new Scanner(f);
    List<Integer> result = null;
    boolean timedout = false;
    while (file.hasNextLine()) {
      String line = file.nextLine();
      timedout |= line.startsWith("c mcsls timed out");
      if (!line.startsWith("c MCS: ")) continue;
      Scanner scanLine = new Scanner(line);
      scanLine.next(); // c
      scanLine.next(); // MCS:
      List<Integer> mcs = Lists.newArrayList();
      while (scanLine.hasNextInt()) mcs.add(scanLine.nextInt());
      if (result == null || mcs.size() < result.size()) result = mcs;
    }

    if (result == null) {
      if (timedout) fail("mcsls timed out");
      return null; // it's unsat
    }
    if (false)
      System.out.printf("DBG MCSLS: result for step %d is %s%n", stepCnt, result);
    // TODO: Indicate to the MaxSatGenerator whether there was a timeout.
    // MaxSatGenerator should use the information on whether the result is
    // approximate or exact to decide whether it considers a query impossible.
    return result;
  }

  // Uses unit clauses to guess the assignment. This doesn't work in general,
  // of course, but we'll usually have unit clauses for the literals we're
  // interested in.
  private List<Integer> assignmentOfClauses(
      List<Integer> unsatisfied,
      List<Integer> satisfied
  ) {
    Set<Integer> assignment = Sets.newHashSet();
    // NOTE: If l is part of an MCS, then -l is true in all models.
    assignmentOfClauses(unsatisfied, false, assignment);
    assignmentOfClauses(satisfied, true, assignment);
    if (SAFE) {
      for (int l : assignment) {
        if (assignment.contains(-l))
          fail("mcsls says that contradictory clauses are satisfied?");
      }
    }
    if (false) {
      System.out.printf("DBG MCSLS: unsatisfied clauses (0-based) %s%n", unsatisfied);
      System.out.printf("DBG MCSLS: satisfied clauses (0-based) %s%n", satisfied);
      System.out.printf("DBG MCSLS: assignment %s%n", assignment);
    }
    return Lists.newArrayList(assignment);
  }

  private void assignmentOfClauses(
      List<Integer> cs,
      boolean sat,
      Set<Integer> assignment
  ) {
    for (int i : cs) if (clauses[i].constraint().length == 1)
      for (int l : clauses[i].constraint()) assignment.add(sat? l : -l);
  }

  public Mcsls() {
    String mcsls = System.getProperty("chord.experiment.solver.mcsls", "mcsls");
    mcslsPath = path(System.getenv("CHORD_INCUBATOR"), "src","chord","analyses","experiment","solver",mcsls);
    mcslsPath = System.getProperty("chord.experiment.solver.mcslspath", mcslsPath);
  }

  private FormattedConstraint[] clauses = new FormattedConstraint[0];
  private String mcslsPath;
  private static boolean SAFE = true;
  private static int stepCnt; // mainly for debug; very concurrency unsafe
}
