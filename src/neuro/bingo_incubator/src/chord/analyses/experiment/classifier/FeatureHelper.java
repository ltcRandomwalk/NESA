package chord.analyses.experiment.classifier;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Tuple;
import chord.util.tuple.object.Pair;

interface Analysis<S> {
	S initS();
	ArrayList<Pair<Tuple,S>> visit(S sourceS, ConstraintItem c);
	S join(S s1, S s2);
	boolean leq(S s1, S s2);
}

/** Functions likely to be used while extracting features. */
public class FeatureHelper {

	private static Map<Tuple, ArrayList<ConstraintItem>> buildGraph(Iterable<ConstraintItem> constraints) {
		HashMap<Tuple, ArrayList<ConstraintItem>> graph = Maps.newHashMap();
    for (ConstraintItem c : constraints) {
			Tuple hd = c.getHeadTuple();
			if (hd != null) {
			        ArrayList<ConstraintItem> xs = graph.get(hd);
			        if (xs == null) xs = Lists.newArrayList();
			        xs.add(c);
			        graph.put(hd, xs);
			}
      		}
		return graph;
   	}

	private static <S> Map<Tuple, S> analyze(Set<Tuple> queries, Iterable<ConstraintItem> constraints, Analysis<S> analyzer) {
		assert(queries != null && !queries.isEmpty());

		Map<Tuple, ArrayList<ConstraintItem>> graph = buildGraph(constraints);

    		HashMap<Tuple, S> res = Maps.newHashMap();
		ArrayList<Tuple> now = Lists.newArrayList();
		ArrayList<Tuple> nxt = Lists.newArrayList();

		for (Tuple q : queries) {
			nxt.add(q);
			res.put(q, analyzer.initS());
		}

		while (!nxt.isEmpty()) {
			ArrayList<Tuple> tmp = now; now = nxt; nxt = tmp;
      			for (Tuple source : now) {
				S sourceS = res.get(source);
			        ArrayList<ConstraintItem> targets = graph.get(source);
				if (targets != null) for (ConstraintItem c : targets) {
					for (Pair<Tuple, S> p : analyzer.visit(sourceS, c)) {
						S targetS = res.get(p.val0);
						if (targetS == null) {
							res.put(p.val0, targetS);
							nxt.add(p.val0);
						} else if (!analyzer.leq(p.val1, targetS)) {
							res.put(p.val0, analyzer.join(targetS, p.val1));
							nxt.add(p.val0);
						}
					}
				}
			}
			now.clear();
		}
		return res;
	}

	public static Set<Tuple> getReachingTuples(Set<Tuple> queries, Iterable<ConstraintItem> constraints) {
		assert(queries != null && !queries.isEmpty());

		Map<Tuple, ArrayList<ConstraintItem>> graph = buildGraph(constraints);
    		Set<Tuple> res = Sets.newHashSet();
		ArrayList<Tuple> now = Lists.newArrayList();
		ArrayList<Tuple> nxt = Lists.newArrayList();

		res.addAll(queries);
		nxt.addAll(queries);
		while (!nxt.isEmpty()) {
			ArrayList<Tuple> tmp = now; now = nxt; nxt = tmp;
      			for (Tuple source : now) {
			        ArrayList<ConstraintItem> targets = graph.get(source);
				if (targets != null) for (ConstraintItem c : targets) {
					List<Tuple> subTuples = c.getSubTuples();
					for (Tuple target : subTuples) if (!res.contains(target)) {
						res.add(target);
						nxt.add(target);
					}
				}
			}
			now.clear();
		}
		return res;
	}

  // A rule like (a <- b, c) counts as two arcs, (a<-b) and (a<-c).
  // Note: Chains are compressed. More precisely, nodes with both
  // in- and out-degree equal to 1 do not count towards distance.
  // Tuples with no path to |query| will not appear in the returned map.
  public static Map<Tuple, Integer> distancesTo(Tuple query, Iterable<ConstraintItem> constraints) {
    // Build graph. NOTE: Parallel arcs are not merged.
    long t1 = System.nanoTime();
    HashMap<Tuple, ArrayList<Tuple>> to = Maps.newHashMap();
    for (ConstraintItem c : constraints) {
      ArrayList<Tuple> xs = to.get(c.getHeadTuple());
      if (xs == null) xs = Lists.newArrayList();
      xs.addAll(c.getSubTuples());
      to.put(c.getHeadTuple(), xs);
    }
    HashMap<Tuple, Integer> outdegree = Maps.newHashMap();
    for (Map.Entry<Tuple,ArrayList<Tuple>> e : to.entrySet()) {
      for (Tuple x : e.getValue()) {
        Integer od = outdegree.get(x);
        if (od == null) od = 0;
        outdegree.put(x, od + 1);
      }
    }
    long t2 = System.nanoTime();
    //System.out.printf("DBG to %s\n",to);

    // Do BFS.
    long t3 = System.nanoTime();
    HashMap<Tuple, Integer> distance = Maps.newHashMap();
    ArrayList<Tuple> now = Lists.newArrayList();
    ArrayList<Tuple> nxt = Lists.newArrayList();
    ArrayList<Tuple> tmp;
    int d = 0;
    nxt.add(query);
    distance.put(query, d);
    while (!nxt.isEmpty()) {
      now.clear();
      tmp = now; now = nxt; nxt = tmp;
      ++d;
      for (Tuple y : now) {
        ArrayList<Tuple> xs = to.get(y);
        if (xs != null) for (Tuple x : xs) {
          while (true) {
            Integer od = distance.get(x);
            if (od != null) break;
            distance.put(x, d);

            // What follows is for handling chains.
            if (outdegree.get(x) != 1) break;
            ArrayList<Tuple> zs = to.get(x);
            if (zs == null || zs.size() != 1) break;
            x = zs.get(0);
          }
          nxt.add(x);
        }
      }
    }
    long t4 = System.nanoTime();

    //System.out.printf(
    //    "TIMES FeatureHelper.distanceTo graph %.02f bfs %.02f print %.02f\n",
    //    (1e-9)*(t2-t1), (1e-9)*(t4-t3), (1e-9)*(t3-t2));
    return distance;
  }
}

