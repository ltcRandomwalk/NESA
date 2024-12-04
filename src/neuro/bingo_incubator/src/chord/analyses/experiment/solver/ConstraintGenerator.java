package chord.analyses.experiment.solver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.bddbddb.Dom;
import chord.util.ProfileUtil;
import chord.util.tuple.object.Pair;

import static chord.util.ExceptionUtil.fail;
import static com.google.common.base.Verify.verify;

/**
  Generates CNF constraints from Datalog dumps.
  It maintains the tuple index, necessary to interpret the CNF literals.  If
  chord.experiment.accumulate, then it accumulates constraints over multiple
  iterations.

  To use this class, first instantiate it, then call {@code update} whenever
  you have new results from the Datalog solver, and call {@code getCnf}
  whenever you need the current provenance constraints. (See also {@code
  getReversedCnf}) At any point you can also ask for the tuple index, which you
  can use to convert from integers to their tuples. You can also modify the
  tuple index, which will affect the integers that {@code ConstraintGenerator}
  will allocate in the future.
 */
public class ConstraintGenerator {
  public ConstraintGenerator(final ParamTupleConsHandler parameterHandler) {
    this.clauses = Maps.newHashMap();
    this.auxiliaries = null;
    this.literals = Sets.newTreeSet();
    this.parameterHandler = parameterHandler;
    this.tupleIndex = new TupleIndex();
    this.accumulate = Boolean.getBoolean("chord.experiment.accumulate");
  }

  // NOTE: The parameter handler must also be init(), by my client.
  public void update(Provenance provenance) {
    ProfileUtil.start("MAIN", "ConstraintGenerator.update");
    if (!accumulate) resetClauses();
    for (Map.Entry<String, List<ConstraintItem>> e : provenance.data.entrySet()) {
      for (ConstraintItem c : e.getValue()) {
        addClause(c, e.getKey());
      }
    }
    ProfileUtil.stop("MAIN", "ConstraintGenerator.update");
  }

  public int[][] getCnf() {
    auxiliaries = null;
    int[][] result = new int[clauses.size()][];
    int i = 0;
    for (Clause c : clauses.keySet()) {
      result[i++] = Arrays.copyOf(c.ls, c.ls.length);
    }
    return result;
  }

  public int[][] getReversedCnf(
      Iterable<Tuple> parameters, Iterable<Tuple> queries
  ) {
    if (accumulate) {
      System.out.printf(
      "WARNING: no proof that accumulate is compatible with !likelyPossible%n");
    }

    // Phase 0: Assert formulas are of the right shape.
    if (SAFE) {
      for (Clause c : clauses.keySet()) {
        int posCount = 0;
        for (int l : c.ls) if (l > 0) ++posCount;
        verify(posCount == 1, "I can only deal with definite Horn formulas. (8fdwad)");
        for (int i = 1; i < c.ls.length; ++i)
          verify(c.ls[i-1] < c.ls[i], "Clauses should be sorted. (d98wqhed)");
        for (int l : c.ls)
          verify(tupleIndex.getTuple(Math.abs(l)) != null, "That's surprising! (8sdb8ad)");
      }
    }

    // Phase 1: Simulate a Datalog run, and keep only forward-going clauses.
    List<Clause> forwardClauses = Lists.newArrayList();
    {
      Map<Integer, List<Clause>> watch = Maps.newHashMap();
      Set<Integer> justified = Sets.newHashSet();
      Set<Integer> now = Sets.newHashSet();
      Set<Integer> nxt = Sets.newHashSet();

      for (Tuple p : parameters) nxt.add(tupleIndex.getIndex(p));
      for (Clause c : clauses.keySet()) {
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
              if (false) {
                System.out.printf("DBG use %s <-", tupleIndex.getTuple(y));
                for (int l : w.ls) if (l < 0)
                  System.out.printf(" %s", tupleIndex.getTuple(-l));
                System.out.printf("\n");
              }
            }
          }
        }
      }
    }

    // Phase 2: Reverse implications.
    List<int[]> revCnf = Lists.newArrayList();
    auxiliaries = Maps.newHashMap();
    {
      // Phase 2.1. Build Graph.
      Map<Integer, List<Clause>> withHead = Maps.newHashMap();
      for (Clause c : forwardClauses) {
        int y = c.ls[c.ls.length - 1];
        List<Clause> cs = withHead.get(y);
        if (cs == null) {
          cs = Lists.newArrayList();
          withHead.put(y, cs);
        }
        cs.add(c);
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
            verify(parameterHandler.isParam(tupleIndex.getTuple(y)));
            continue;
          }
          if (cs.size() > 1) {
            int[] orClause = new int[cs.size() + 1];
            int i = 1;
            for (Clause c : cs) {
              auxiliaries.put(auxVar + i, clauses.get(c));
              orClause[i] = auxVar + i; ++i;
            }
            orClause[0] = -y;
            revCnf.add(orClause);
          } else {
            verify(cs.size() == 1);
            auxiliaries.put(y, clauses.get(cs.get(0)));
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
    }

    return revCnf.toArray(IAA);
  }

  // Returns a map from variable id to the constraint item it represents.
  // It makes sense to call this after getRevCnf, but not after getCnf.
  public Map<Integer, ConstraintItem> getAuxiliaries() {
    Preconditions.checkState(auxiliaries != null,
        "getAuxiliaries called after getCnf");
    return Collections.unmodifiableMap(auxiliaries);
  }

  // Format: json
  public void save(PrintWriter out) {
    out.printf("{ \"arcs\" :\n"); saveArcs(out);
    out.printf(", \"vertices\" :\n"); saveVertices(out);
    out.printf(", \"rules\" :\n"); saveRules(out);
    out.printf(", \"types\" :\n"); saveVertexTypes(out);
    out.printf(", \"contexts\" :\n"); saveContexts(out);
    out.printf("}\n");
  }

  public TupleIndex getTupleIndex() { return tupleIndex; }

  private void saveArcs(PrintWriter out) {
    out.printf("  [\n");
    boolean first1, first2;
    first1 = true;
    for (Clause c : clauses.keySet()) {
      out.printf(first1? "    " : "  , ");
      out.printf("[\"%s\", ", c.ruleId);
      for (int l : c.ls) if (l > 0) saveTuple(out, l);
      out.printf(", [");
      first2 = true;
      for (int l : c.ls) if (l < 0) {
        if (!first2) out.printf(", ");
        saveTuple(out, l);
        first2 = false;
      }
      out.printf("]]\n");
      first1 = false;
    }
    out.printf("  ]\n");
  }

  private void saveVertices(PrintWriter out) {
    boolean first = true;
    out.printf("  [\n");
    for (Tuple t : tupleIndex) {
      out.printf(first? "    " : "  , ");
      saveTuple(out, t);
      out.printf("\n");
      first = false;
    }
    out.printf("  ]\n");
  }

  private void saveVertexTypes(PrintWriter out) {
    Map<String,List<String>> argumentType = Maps.newTreeMap();
    for (Tuple t : tupleIndex) {
      String n = t.getRelName();
      if (argumentType.containsKey(n)) continue;
      List<String> cs = Lists.newArrayList();
      for (Dom d : t.getDomains())
        cs.add(d.getClass().getSimpleName());
      argumentType.put(n, cs);
    }
    boolean first1, first2;
    first1 = true;
    out.printf("  [\n");
    for (Map.Entry<String,List<String>> e : argumentType.entrySet()) {
      out.printf(first1? "    " : "  , ");
      out.printf("[\"%s\", [", e.getKey());
      first2 = true;
      for (String c : e.getValue()) {
        if (!first2) out.printf(", ");
        out.printf("\"%s\"", c);
        first2 = false;
      }
      out.printf("]]\n");
      first1 = false;
    }
    out.printf("  ]\n");
  }

  private void saveContexts(PrintWriter out) {
    Map<Integer, Integer> contexts = Maps.newTreeMap();
    for (Tuple t : tupleIndex) {
      final Dom[] ds = t.getDomains();
      final int[] vs = t.getIndices();
      final int n = ds.length;
      for (int i = 0; i < n; ++i) {
        if (!(ds[i] instanceof DomC)) continue;
        Ctxt d, c = (Ctxt) ds[i].get(vs[i]);
        while (c.length() > 0 && !contexts.containsKey(ds[i].indexOf(c))) {
          d = c; c = c.prefix(d.length() - 1);
          contexts.put(ds[i].indexOf(d), ds[i].indexOf(c));
        }
        if (c.length() == 0) contexts.put(ds[i].indexOf(c), null);
      }
    }
    boolean first = true;
    out.printf("  [\n");
    for (Map.Entry<Integer, Integer> e : contexts.entrySet()) {
      out.printf(first? "    " : "  , ");
      out.printf("[%d", e.getKey());
      if (e.getValue() != null)
        out.printf(", %d", e.getValue());
      out.printf("]\n");
      first = false;
    }
    out.printf("  ]\n");
  }

  private void saveRules(PrintWriter out) {
    SortedSet<String> ruleIds = Sets.newTreeSet();
    for (Clause c : clauses.keySet()) ruleIds.add(c.ruleId);
    boolean first = true;
    out.printf("  [\n");
    for (String i : ruleIds) {
      out.printf(first? "    " : "  , ");
      out.printf("\"%s\"\n", i);
      first = false;
    }
    out.printf("  ]\n");
  }

  private void saveTuple(PrintWriter out, int l) {
    saveTuple(out, tupleIndex.getTuple(Math.abs(-l)));
  }

  private void saveTuple(PrintWriter out, Tuple t) {
    out.printf("[\"%s\",[", t.getRelName());
    boolean first = true;
    for (int i : t.getIndices()) {
      if (!first) out.printf(",");
      out.printf("%d", i);
      first = false;
    }
    out.printf("]]");
  }

  private void resetClauses() {
    clauses.clear();
  }

  private void addClause(ConstraintItem constraint, String ruleId) {
    // TODO: profile to see if subsumption is important
    clauses.put(new Clause(clauseOfConstraint(constraint), ruleId), constraint);
  }

  // NOTE: a null from paramHandler.transform is interpreted as "reset tuple"
  private int[] clauseOfConstraint(ConstraintItem c) {
    int x;
    Tuple t;
    literals.clear();
    t = parameterHandler.transform(c.getHeadTuple(), c.getHeadTupleSign());
    if (t == null) fail("Only parameters should be reset by PTHandler");
    literals.add(tupleIndex.getIndex(t));

    Iterator<Tuple> ti = c.getSubTuples().iterator();
    Iterator<Boolean> si = c.getSubTuplesSign().iterator();
    while (ti.hasNext()) {
      t = parameterHandler.transform(ti.next(), si.next());
      if (t == null) continue;
      literals.add(-tupleIndex.getIndex(t));
    }

    return Ints.toArray(literals);
  }

  private HashMap<Clause, ConstraintItem> clauses;
  private HashMap<Integer, ConstraintItem> auxiliaries = Maps.newHashMap();
    // In the reversed CNF, if variable x is true, then constraint
    // auxiliaries.get(x) was used in the derivation. Typically, x will be an
    // auxiliary variable.

  private final SortedSet<Integer> literals; // a sort of buffer

  private final ParamTupleConsHandler parameterHandler;
  private final TupleIndex tupleIndex;

  private final boolean accumulate;

  private static final int[][] IAA = new int[0][];

  // DBG
  private static final boolean SAFE = true;

  private static final class Clause {
    public int[] ls;
    public String ruleId;
    private int hash;
    Clause(final int[] ls, final String ruleId) {
      this.ls = ls;
      this.ruleId = ruleId;
      hash = ruleId.hashCode();
      for (int l : this.ls) hash ^= l;
    }
    @Override public boolean equals(Object other) {
      if (!(other instanceof Clause)) return false;
      Clause c = (Clause) other;
      return
        hash == c.hash && ruleId.equals(c.ruleId) && Arrays.equals(ls, c.ls);
    }
    @Override public int hashCode() { return hash; }
  }

  // TODO(rg): Should we merge ResultLoader into this class?
  // TODO(rg): Prune irrelevant constraints in getCnf?
}
