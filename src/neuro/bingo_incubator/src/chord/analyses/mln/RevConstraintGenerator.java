package chord.analyses.mln;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


import java.util.SortedSet;

//import chord.project.analyses.provenance.ParamTupleConsHandler;
import chord.project.analyses.provenance.Tuple;
import chord.util.ProfileUtil;
import chord.util.tuple.object.Pair;
import static chord.util.ExceptionUtil.fail;
import static com.google.common.base.Verify.verify;

/**
  Generates CNF constraints from Datalog dumps.
  It accumulates constraints over multiple iterations. Also, it maintains the
  tuple index, necessary to interpret the CNF literals.

  To use this class, first instantiate it, then call {@code update} whenever
  you have new results from the Datalog solver, and call {@code getCnf}
  whenever you need the current provenance constraints. (See also {@code
  getReversedCnf}) At any point you can also ask for the tuple index, which you
  can use to convert from integers to their tuples. You can also modify the
  tuple index, which will affect the integers that {@code ConstraintGenerator}
  will allocate in the future.
 */
public class RevConstraintGenerator {
  public RevConstraintGenerator(final SimplePTHandler parameterHandler) {
//    this.clauses = Sets.newHashSet();
	this.clauses = Lists.newArrayList();
    this.literals = Sets.newTreeSet();
    this.parameterHandler = parameterHandler;
    this.tupleIndex = new TupleIndex();
  }

  // SIDE-EFFECT: Initializes the parameter handler.
  public void update(List<LookUpRule> rs) {
    ProfileUtil.start("MAIN", "ConstraintGenerator.update");
    parameterHandler.init(rs);
    for (LookUpRule r : rs) {
    	for (Iterator<ConstraintItem> it = r.getAllConstrIterator(); it.hasNext(); ) {
    		addClause(clauseOfConstraint(it.next()));
    	}
    }
    ProfileUtil.stop("MAIN", "ConstraintGenerator.update");
  }

  public int[][] getCnf() {
    int[][] result = new int[clauses.size()][];
    int i = 0;
    for (Clause c : clauses) result[i++] = Arrays.copyOf(c.ls, c.ls.length);
    return result;
  }

  public int[][] getReversedCnf(
	  Iterable<Tuple> parameters, Iterable<Tuple> queries
  ) {
    // Phase 0: Assert formulas are of the right shape.
    if (SAFE) {
      long start = System.nanoTime();
      for (Clause c : clauses) {
        int posCount = 0;
        for (int l : c.ls) if (l > 0) ++posCount;
        verify(posCount == 1, "I can only deal with definite Horn formulas. (8fdwad)");
        for (int i = 1; i < c.ls.length; ++i) 
          verify(c.ls[i-1] < c.ls[i], "Clauses should be sorted. (d98wqhed)");
        for (int l : c.ls)
          verify(tupleIndex.getTuple(Math.abs(l)) != null, "That's surprising! (8sdb8ad)");
      }
      System.out.printf("XXX PROF phase 0 %.02f%n", (1e-9)*(System.nanoTime()-start));
      System.out.println("XXX PROF phase 0. Num clauses: " + clauses.size());
    }

    // Phase 1: Simulate a Datalog run, and keep only forward-going clauses.
    List<Clause> forwardClauses = Lists.newArrayList();
    {
      long start = System.nanoTime();
      Map<Integer, List<Clause>> watch = Maps.newHashMap();
      Set<Integer> justified = Sets.newHashSet();
      Set<Integer> now = Sets.newHashSet();
      Set<Integer> nxt = Sets.newHashSet();

      for (Tuple p : parameters) nxt.add(tupleIndex.getOldIndex(p));
  //    for (Tuple p : parameters) nxt.add(tupleIndex.getIndex(p));
      for (Clause c : clauses) {
        if (c.ls.length == 1) {
          nxt.add(c.ls[0]);
          forwardClauses.add(c);
        } else {
          List<Clause> cs = watch.get(-c.ls[0]);
          if (cs == null) {
            cs = Lists.newArrayList();
            watch.put(-c.ls[0], cs);
          }
          cs.add(c);
        }
      }

      while (!nxt.isEmpty()) {
        { Set<Integer> tmpSI = now; now = nxt; nxt = tmpSI; nxt.clear(); }
        justified.addAll(now);
        for (Integer x : now) {
          List<Clause> ws = watch.get(x);
          if (ws == null) continue;
          for (Clause w : ws) {
            int y = w.ls[w.ls.length - 1];
            if (justified.contains(y)) continue; // w isn't a forward clause
            int i = w.ls.length - 2;
            while (i >= 0 && justified.contains(-w.ls[i])) --i;
            if (i >= 0) {
              List<Clause> zs = watch.get(-w.ls[i]);
              if (zs == null) {
                zs = Lists.newArrayList();
                watch.put(-w.ls[i], zs);
              }
              zs.add(w);
            } else {
              nxt.add(y);
              forwardClauses.add(w);
            }
          }
        }
      }
      System.out.printf("XXX PROF phase 1 %.02f%n", (1e-9)*(System.nanoTime()-start));
      System.out.println("XXX PROF phase 1. Num forward clauses: " + forwardClauses.size());
    }

    // Phase 2: Reverse implications.
    List<int[]> revCnf = Lists.newArrayList();
    {
      long start = System.nanoTime();

      // Phase 2.1. Build Graph.
      Map<Integer, List<Clause>> withHead = Maps.newHashMap();
      {
        for (Clause c : forwardClauses) {
          int y = c.ls[c.ls.length - 1];
          List<Clause> cs = withHead.get(y);
          if (cs == null) {
            cs = Lists.newArrayList();
            withHead.put(y, cs);
          }
          cs.add(c);
        }
      }

      // Phase 2.2. Backward reachability from queries.
      {
        Set<Integer> done = Sets.newHashSet();
        Set<Integer> todo = Sets.newHashSet();

        for (Tuple q : queries) todo.add(tupleIndex.getOldIndex(q));

        int auxVar = tupleIndex.size();  // last used index
        while (!todo.isEmpty()) {
          int y = todo.iterator().next(); todo.remove(y);
          done.add(y);
          List<Clause> cs = withHead.get(y);
          if (cs == null) {
        	  System.out.printf("XXX unjustified %d %s%n", y, tupleIndex.getTuple(Math.abs(y)));
        	  verify(/*parameterHandler.isParam(tupleIndex.getTuple(y)) ||*/ parameterHandler.isConstant(tupleIndex.getTuple(y)));
        	  continue;
          }
          if (cs.size() > 1) {
            int[] orClause = new int[cs.size() + 1];
            int i = 1;
            for (Clause c : cs) { orClause[i] = auxVar + i; ++i; }
            orClause[0] = -y;
            revCnf.add(orClause);
          }
          for (Clause c : cs) {
            int ny = -(cs.size() == 1? y : ++auxVar);
            for (int i = c.ls.length - 2; i >= 0; --i) {
              int x = -c.ls[i];
              if (!done.contains(x)) todo.add(x);
              revCnf.add(new int[] {ny, x});
            }
          }
        }
      }
      System.out.printf("XXX PROF phase 2 %.02f%n", (1e-9)*(System.nanoTime()-start));
      System.out.println("XXX PROF phase 2. Num rev clauses: " + revCnf.size());
    }

    return revCnf.toArray(IAA);
  }

  public TupleIndex getTupleIndex() { return tupleIndex; }


  private void addClause(int[] ls) {
    // TODO: profile to see if subsumption is important
    clauses.add(new Clause(ls));
  }

  // NOTE: a null from paramHandler.transform is interpreted as "reset tuple"
  // Handling negation in this manner is incorrect, since this code always assumes
  // that the head tuple is the last element of the clause.
  private int[] clauseOfConstraint(ConstraintItem c) {
    int x;
    Pair<Tuple, Boolean> p;
    literals.clear();
    p = parameterHandler.transform(c.getHeadTuple(), c.getHeadTupleSign());
    if (p == null) fail("Only parameters should be reset by PTHandler");
    if (!p.val1) fail("Cannot handle negation");
    x = tupleIndex.getIndex(p.val0);
    literals.add(p.val1? x : -x);

    Iterator<Tuple> ti = c.getSubTuples().iterator();
    Iterator<Boolean> tiSign = c.getSubTuplesSign().iterator();
    while (ti.hasNext()) {
      p = parameterHandler.transform(ti.next(), tiSign.next());
      if (p == null) continue;
      if (!p.val1) fail("Cannot handle negation");
      x = tupleIndex.getIndex(p.val0);
      literals.add(p.val1? -x : x);
    }

    return Ints.toArray(literals);
  }

//  private HashSet<Clause> clauses;
  protected List<Clause> clauses;

  private final SortedSet<Integer> literals; // a sort of buffer

  protected final SimplePTHandler parameterHandler;
  protected final TupleIndex tupleIndex;

  protected static final int[][] IAA = new int[0][];

  // DBG
  public static final boolean SAFE = true;

  static final class Clause {
    public int[] ls;
    private int hash;
    Clause(final int[] ls) {
      // TODO: profile to see if ls has repetitions often
      this.ls = ls;
      Arrays.sort(ls);
      for (int l : this.ls) hash ^= l;
    }
    @Override public boolean equals(Object other) {
      if (!(other instanceof Clause)) return false;
      Clause c = (Clause) other;
      return hash == c.hash && Arrays.equals(ls, c.ls);
    }
    @Override public int hashCode() {
      return hash;
    }
  }

  // TODO(rg): Should we merge ResultLoader into this class?
  // TODO(rg): Prune irrelevant constraints in getCnf?
}
