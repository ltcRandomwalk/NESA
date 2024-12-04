package chord.analyses.mln;

import static chord.analyses.mln.RevConstraintGenerator.IAA;
import static chord.analyses.mln.RevConstraintGenerator.SAFE;
import static com.google.common.base.Verify.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.project.analyses.provenance.Tuple;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AndRevConstraintGenerator extends RevConstraintGenerator {

	public AndRevConstraintGenerator(SimplePTHandler parameterHandler) {
		super(parameterHandler);
		// TODO Auto-generated constructor stub
	}

	@Override
	public int[][] getReversedCnf(Iterable<Tuple> parameters,
			Iterable<Tuple> queries) {
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

        while (!todo.isEmpty()) {
          int y = todo.iterator().next(); todo.remove(y);
          done.add(y);
          List<Clause> cs = withHead.get(y);
          if (cs == null) {
        	  System.out.printf("XXX unjustified %d %s%n", y, tupleIndex.getTuple(Math.abs(y)));
        	  verify(/*parameterHandler.isParam(tupleIndex.getTuple(y)) ||*/ parameterHandler.isConstant(tupleIndex.getTuple(y)));
        	  continue;
          }
          for (Clause c : cs) {
            for (int i = c.ls.length - 2; i >= 0; --i) {
              int x = -c.ls[i];
              if (!done.contains(x)) todo.add(x);
              revCnf.add(new int[] {-y, x});
            }
          }
        }
      }
      System.out.printf("XXX PROF phase 2 %.02f%n", (1e-9)*(System.nanoTime()-start));
      System.out.println("XXX PROF phase 2. Num rev clauses: " + revCnf.size());
    }

    return revCnf.toArray(IAA);	
    }
	
	

}
