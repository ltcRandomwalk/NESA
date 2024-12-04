package chord.analyses.experiment.solver;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.*;

/** The method {@code solve} should be all you need to see. */
// NOTE: Implements the algorithm from http://arxiv.org/abs/1308.3405
// The idea of the algorithm is in {@code assign}. The main loop is in {@code
// solve}, the others are all kinds of helpers.
public class ApproxMaxSatSolver implements MaxSatSolver {
  private boolean DEBUG = false;

  // PRE: No repetitions in constraints!
  // PRE: No trivial clauses!
  // The format is DIMACS, also known as wcnf.
  // Returns null if it didn't satisfy all clauses that have highest weight
  // (think of them as the hard clauses).
  @Override public List<Integer> solve(
      List<FormattedConstraint> cs,
      String problemName
  ) {
	System.out.println("AMX: Approx started on " + problemName);
    int top = 0;
    for (FormattedConstraint c : cs) top = Math.max(top, c.weight());
    int topSum = 0;
    for (FormattedConstraint c : cs) if (c.weight() == top) topSum += top;

    int[] bestAssignment = null;
    long bestWeight = -1;
    initialize(cs);
    for (int seed = 1; seed <= 10; ++seed) {
      random = new Random(seed);
      generatePermutation();
      initializeLoop();
      for (int k = 0; k < n; ++k) assign(k);
      if (weight2 % 2 != 0) fail("Fractional weight? What did I do wrong?");
      if (weight2 >= 2 * bestWeight) {
        bestWeight = weight2 / 2;
        bestAssignment = assignment;
      }
      if (DEBUG) {
        // These steps are wasted, because we can avoid doing them by using
        // a smarter data structure. But, if these numbers aren't big then it's
        // probably not worth it.
        int wasted = 0;
        for (int sz : clauseSize) if (sz < 0) wasted += -sz - 1;
        System.out.printf("AXN: DBG: wasted steps %d%n", wasted);
      }
    }
    System.out.printf(": Found assignment of weight %d.%n", bestWeight);
    if (bestWeight < 0) fail("I should have at least one seed.");
    if (bestWeight < topSum) {
      System.out.printf("AMX: ... but the hard clauses have weight %d. I'll say UNSAT.%n", topSum);
      return null;
    }
    List<Integer> result = newArrayList();
    for (int l : bestAssignment) if (l != 0) result.add(l);
    return result;
  }

  // Sets up m, n, clause, clauseWeight, occurrence.
  private void initialize(List<FormattedConstraint> cs) {
    long t1 = System.nanoTime();
    m = cs.size();

    clause = new int[m][];
    clauseWeight = new int[m];
    int i = 0; // clause index
    for (FormattedConstraint c : cs) {
      clauseWeight[i] = c.weight();
      clause[i] = c.constraint();
      ++i;
    }

    n = 0;
    for (int[] c : clause) for (int l : c)
      n = Math.max(n, Math.abs(l));

    int[] occSize = new int[2 * n + 1];
    for (int[] c : clause) for (int l : c)
      ++occSize[l+n];
    occurrence = new int[2 * n + 1][];
    for (int l = -n; l <= n; ++l) occurrence[l+n] = new int[occSize[l+n]];
    Arrays.fill(occSize, 0);
    for (i = 0; i < m; ++i) for (int l : clause[i])
      occurrence[l+n][occSize[l+n]++] = i;

    long t2 = System.nanoTime();
    if (DEBUG) System.out.printf("AXN: DBG: Initialization took %.01f seconds%n",(1e-9)*(t2-t1));
  }

  // Sets up perm and invPerm.
  private void generatePermutation() {
    int j;
    perm = new int[n + 1]; // perm[0] is a dummy, and is 0
    for (j = 1; j <= n; ++j) perm[j] = j;
    for (j = 1; j <= n; ++j) {
      int k = random.nextInt(n - j + 1) + j;
      int t = perm[k]; perm[k] = perm[j]; perm[j] = t;
    }

    invPerm = new int[n + 1];
    for (j = 1; j <= n; ++j) invPerm[perm[j]] = j;
  }

  // Sets up gainWeight, loseWeight, clauseSize, weight2, assignment
  // Checks overflow of weights.
  private void initializeLoop() {
    gainWeight = new long[2 * n + 1];
    loseWeight = new long[2 * n + 1];
    clauseSize = new int[m];
    assignment = new int[n];

    for (int i = 0; i < m; ++i) {
      clauseSize[i] = clause[i].length;
      for (int l : clause[i]) gainWeight[l+n] += clauseWeight[i];
      if (clauseSize[i] == 1) loseWeight[-clause[i][0]+n] += clauseWeight[i];
    }

    weight2 = 0;
    for (int w : clauseWeight) {
      weight2 += w;
      if (weight2 < 0) fail("Weight overflow?");
    }
    if (weight2 + weight2 < 0) fail("Weight overflow?");
  }

  // POST: Math.abs(assignment[k])==perm[k+1], or assignment[k]==0 (means don't care)
  private void assign(int k) {
    int j = perm[k+1];
    long t = gainWeight[j+n] - loseWeight[j+n];
    long f = gainWeight[-j+n] - loseWeight[-j+n];

    if (t < 0 && f < 0) fail("gains/loses are incorrectly tracked.");
    if (t == 0 && f == 0) return; // don't care
    if (t < 0) assignment[k] = -j;
    else if (f < 0) assignment[k] = j;
    else if (random.nextDouble() < 1.0 * t / (t + f)) assignment[k] = j;
    else assignment[k] = -j;

    int l = assignment[k]; // just a shorthand
    //System.out.printf("DBG set %d%n", l);
    weight2 += gainWeight[l+n] - loseWeight[l+n];
    for (int i : occurrence[l+n]) if (clauseSize[i] > 0) for (int ll : clause[i]) {
      clauseSize[i] = -1;
      gainWeight[ll+n] -= clauseWeight[i]; // assumes no duplicates in clause[i]
    }
    for (int i : occurrence[-l+n]) {
      --clauseSize[i];
      if (clauseSize[i] == 1) {
        for (int ll : clause[i]) { // I hope this searching isn't too slow
          if (invPerm[Math.abs(ll)] - 1 > k) { // that is, ll is unassigned
            // NOTE: -1 above because invPerm is 1-indexed, but k is 0-indexed
            loseWeight[-ll+n] += clauseWeight[i];
            break;
          }
        }
      }
    }
  }


  // notations/conventions:
  //  m is the number of clauses, which are indexed 0..m-1
  //  n is the number of variables; literals l are -n..-1 and 1..n
  // usually,
  //  0 <= i < m ranges over clauses
  //  1 <= j <= n ranges over variables
  //  1 <= abs(l) <= n ranges over literals
  private int m, n;
  private int[][] occurrence; // occurrence[l+n] lists the clauses where l occurs
  private long[] gainWeight; // gainWeight[l+n] is how much the lower bound improves if l is set
  private long[] loseWeight; // loseWeight[l+n] is how much the upper bound worsen if l is set
  private int[] clauseSize; // clauseSize[c] counts how many unset literals are in c
  private long weight2; // the current guess for solution weight, times 2 (to keep it int)
  private int[] assignment; // 0 means unassigned
  private int[] perm; // permutation on variables
  private int[] invPerm;

  // These simply unpack the List<FormattedConstraint>, just in case that List
  // is actually a LinkedList, which would be quite bad.
  private int[] clauseWeight;
  private int[][] clause;

  private Random random;
}
