package chord.analyses.derivsz;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.incrsolver.sumgen.Clause;
import chord.bddbddb.Dom;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.LookUpRule;
import chord.project.analyses.provenance.Tuple;
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
public class ConstraintGenerator {
	public TIntHashSet unusedTuples;

	private HashSet<Clause> clauses;
	private HashSet<Clause> fwdClauses;
	private HashMap<Integer, HashSet<Clause>> headToDisjunctsMap;
	private HashMap<Integer, TupleStats> tupleToTupleStatsMap;
	private HashMap<Integer, TupleStats> tupleToTupleStatsMapCompl;
	private final List<Integer> literals; // a sort of buffer
	private final TupleIndex tupleIndex;
	private boolean sliceGraph;
	private boolean sliceComplement;
	private HashMap<Integer, HashSet<Clause>> slicedHdToDisjunctsMap;
	private HashMap<Integer, HashSet<Clause>> sliceComplHdToDisjunctsMap;
	private HashMap<String, int[]> mark;
	private int disjunctProbPercent;
	private TIntHashSet appDerivedTuples;
	// DBG
	private static final boolean SAFE = true;
	
  public ConstraintGenerator() {
	    this.clauses = Sets.newHashSet();
	    this.fwdClauses = Sets.newHashSet();
	    this.literals = Lists.newArrayList();
	    this.tupleIndex = new TupleIndex();
	    this.headToDisjunctsMap = new HashMap<Integer, HashSet<Clause>>();
	    this.sliceGraph = false;
	    this.sliceComplement = false;
	    this.tupleToTupleStatsMap = new HashMap<Integer, TupleStats>();
	    this.unusedTuples = new TIntHashSet();
	    this.appDerivedTuples = new TIntHashSet();
 }
  
  public ConstraintGenerator(boolean sg, HashMap<String, int[]> m, int prob, boolean sCompl) {
	    this.clauses = Sets.newHashSet();
	    this.fwdClauses = Sets.newHashSet();
	    this.literals = Lists.newArrayList();
	    this.tupleIndex = new TupleIndex();
	    this.headToDisjunctsMap = new HashMap<Integer, HashSet<Clause>>();
	    this.sliceGraph = sg;
	    this.sliceComplement = sCompl;
	    if (sg) {
	    	this.slicedHdToDisjunctsMap = new HashMap<Integer, HashSet<Clause>>();
	    	if (sCompl) this.sliceComplHdToDisjunctsMap = new HashMap<Integer, HashSet<Clause>>();
	    }
	    this.mark = m;
	    this.disjunctProbPercent = prob;
	    this.tupleToTupleStatsMap = new HashMap<Integer, TupleStats>();
	    this.tupleToTupleStatsMapCompl = new HashMap<Integer, TupleStats>();
	    this.unusedTuples = new TIntHashSet();
	    this.appDerivedTuples = new TIntHashSet();
  }
  
  public HashMap<Integer, TupleStats> getTupleToTupleStatsMap() {
	  return tupleToTupleStatsMap;
  }
  
  
  public HashMap<Integer, TupleStats> getTupleToTupleStatsMapCompl() {
	  return tupleToTupleStatsMapCompl;
  }
  
  
  public void update(LookUpRule r) {
	  Iterator<ConstraintItem> iter = r.getAllConstrIterator();
	  while (iter.hasNext()) {
		ConstraintItem it = iter.next();
	    addClause(clauseOfConstraint(it));
	  }
  }
  
  public int[][] getCnf() {
    int[][] result = new int[clauses.size()][];
    int i = 0;
    for (Clause c : clauses) result[i++] = Arrays.copyOf(c.ls, c.ls.length);
    return result;
  }

  public void getForwardClauses(List<Tuple> parameters) {
	    // Phase 0: Assert formulas are of the right shape.
	    if (SAFE) {
	      long start = System.nanoTime();
	      for (Clause c : clauses) {
	        int posCount = 0;
	        for (int l : c.ls) if (l > 0) ++posCount;
	        verify(posCount == 1, "I can only deal with definite Horn formulas. (8fdwad)");
	        for (int i = 1; i < c.ls.length; ++i)
	          verify(c.ls[i-1] <= c.ls[i], "Clauses should be sorted. (d98wqhed)");
	        for (int l : c.ls)
	          verify(tupleIndex.getTuple(Math.abs(l)) != null, "That's surprising! (8sdb8ad)");
	      }
	      System.out.printf("XXX PROF phase 0 %.02f%n", (1e-9)*(System.nanoTime()-start));
	    }

	    // Phase 1: Simulate a Datalog run, and keep only forward-going clauses.
	    {
	      long start = System.nanoTime();
	      Map<Integer, List<Clause>> watch = Maps.newHashMap();
	      TIntHashSet justified = new TIntHashSet();
	      TIntHashSet now = new TIntHashSet();
	      TIntHashSet nxt = new TIntHashSet();

	      for (Tuple p : parameters) nxt.add(tupleIndex.getOldIndex(p));
	      for (Clause c : clauses) {
	        if (c.ls.length == 1) {
	          nxt.add(c.ls[0]);
	          fwdClauses.add(c);
	          storeClause(c.ls[0], c);
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
	        { TIntHashSet tmpSI = now; now = nxt; nxt = tmpSI; nxt.clear(); }
	        justified.addAll(now);
	        for (TIntIterator iter = now.iterator(); iter.hasNext(); ) {
			  int x = iter.next();
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
	              if (sliceGraph) {
		              fwdClauses.add(w);
		              storeClause(y, w);
	              } else {
	            	  collectFrontier(y, w, tupleToTupleStatsMap);
	              }
	            }
	          }
	        }
	      }
	      System.out.printf("XXX PROF phase 1 %.02f%n", (1e-9)*(System.nanoTime()-start));
	    }
	    clauses.clear();
	    fwdClauses.clear();
  }
  
  public void getSlicedClauses(List<Tuple> outsets, Set<Integer> edbTuples) {
	TIntHashSet todo = new TIntHashSet();
	TIntHashSet done = new TIntHashSet();
	  
	for (Tuple p : outsets) {
		todo.add(tupleIndex.getOldIndex(p));
	}
	while (!todo.isEmpty()) {
		int y = todo.iterator().next(); todo.remove(y);
		done.add(y);
		HashSet<Clause> cs = headToDisjunctsMap.get(y);
		if (cs == null) {
			continue;
		}
		slicedHdToDisjunctsMap.put(y,  cs);
		for (Clause c : cs) {
			for (int j = c.ls.length - 2; j >= 0; --j) {
				int x = -c.ls[j];
				if (!done.contains(x)) {
					todo.add(x);
				}
			}
		}
	}
	
	// "unusedTuples" initially contains all tuples - both EDB and IDB.
	unusedTuples.removeAll(edbTuples);
	System.out.println ("DERIVSZ: total derived tuples: " + unusedTuples.size());
	System.out.println ("DERIVSZ: total derived tuples (alt): " + headToDisjunctsMap.keySet().size());
	System.out.println ("DERIVSZ: total application derived tuples: " + appDerivedTuples.size());
	TIntHashSet escaping = new TIntHashSet();
	// "done" is the closure of all derived tuples staring from the outset
	escaping.addAll(done);
	// remove all derived tuples that belong to the application
	escaping.removeAll(appDerivedTuples);
	// remove all EDB tuples because all tuples in the body of an applied rule get added to "done" and rule bodies contain EDB tuples also.
	escaping.removeAll(edbTuples);
	System.out.println("DERIVSZ: number of escaping lib tuples: " + escaping.size());
	
	// remove all escaping tuples
	unusedTuples.removeAll(done);
	// remove all application derived tuples
	unusedTuples.removeAll(appDerivedTuples);
	System.out.println  ("DERIVSZ: number of non-escaping lib tuples: " + unusedTuples.size());
	
	if (sliceComplement) {
		// Get the slice of the graph corresponding to the unused tuples. Though used tuples and unused tuples are non-intersecting, 
		// the graph slices corresponding to them may be intersecting.
		todo.clear();
		done.clear();
		for (TIntIterator iter = unusedTuples.iterator(); iter.hasNext(); ) {
			int x = iter.next();
			todo.add(x);
		}
		while (!todo.isEmpty()) {
			int y = todo.iterator().next(); todo.remove(y);
			done.add(y);
			HashSet<Clause> cs = headToDisjunctsMap.get(y);
			if (cs == null) {
				continue;
			}
			sliceComplHdToDisjunctsMap.put(y,  cs);
			for (Clause c : cs) {
				for (int j = c.ls.length - 2; j >= 0; --j) {
					int x = -c.ls[j];
					if (!done.contains(x)) {
						todo.add(x);
					}
				}
			}
		}
		System.out.println ("DERIVSZ: Num keys in sliceComplHdToDisjunctsMap: " + sliceComplHdToDisjunctsMap.keySet().size());
	}
	
	// Get the intersection of escaping and non-escaping - this modifies the set unusedTuples
	unusedTuples.retainAll(escaping);
	System.out.println ("DERIVSZ: intersection of escaping and non-escaping: " + unusedTuples.size());
	
	headToDisjunctsMap.clear();
  }
  
  public void storeClause(int head, Clause c) {
	  HashSet<Clause> currDisjuncts;
	  if (headToDisjunctsMap.containsKey(head))
		  currDisjuncts = headToDisjunctsMap.get(head);
	  else {
		  currDisjuncts = new HashSet<Clause>();
		  headToDisjunctsMap.put(head, currDisjuncts);
	  }
	  currDisjuncts.add(c);
  }

  
  public void computeFrontier(List<Tuple> parameters, List<Tuple> outsets) {
	  HashMap<Integer, HashSet<Clause>> mapToUse;
	  HashMap<Integer, TupleStats> statMap;
	  
	  getForwardClauses(parameters);
	  
	  if (sliceGraph) {
		  Set<Integer> nxt = Sets.newHashSet();
		  for (Tuple p : parameters) nxt.add(tupleIndex.getOldIndex(p));
    	  getSlicedClauses(outsets, nxt);
    	  mapToUse = this.slicedHdToDisjunctsMap;
    	  statMap = tupleToTupleStatsMap;
    	  computeFrontierInternal(parameters, statMap, mapToUse);
    	  System.out.println ("DERIVSZ: Num keys in tupleToTupleStatsMap: " + tupleToTupleStatsMap.keySet().size());
    	  if (sliceComplement) {
	    	  mapToUse = this.sliceComplHdToDisjunctsMap;
	    	  statMap = tupleToTupleStatsMapCompl;
	    	  computeFrontierInternal(parameters, statMap, mapToUse);
	    	  System.out.println ("DERIVSZ: Num keys in tupleToTupleStatsMapCompl: " + tupleToTupleStatsMapCompl.keySet().size());
    	  }
      } else {
    	  statMap = tupleToTupleStatsMap;
    	  mapToUse = this.headToDisjunctsMap;
    	  computeFrontierInternal(parameters, statMap, mapToUse);
      }
  }
  
  
  private void computeFrontierInternal(List<Tuple> parameters, HashMap<Integer, TupleStats> statMap, 
		                               HashMap<Integer, HashSet<Clause>> mapToUse) {
      long start = System.nanoTime();
      Map<Integer, List<Clause>> watch = Maps.newHashMap();
      Set<Integer> justified = Sets.newHashSet();
      Set<Integer> now = Sets.newHashSet();
      Set<Integer> nxt = Sets.newHashSet();
      
      for (Tuple p : parameters) nxt.add(tupleIndex.getOldIndex(p));
      for (HashSet<Clause> disjuncts : mapToUse.values()) {
	      for (Clause c : disjuncts) {
	        if (c.ls.length == 1) {
	          nxt.add(c.ls[0]);
	        } else {
	          List<Clause> cs = watch.get(-c.ls[0]);
	          if (cs == null) {
	            cs = Lists.newArrayList();
	            watch.put(-c.ls[0], cs);
	          }
	          cs.add(c);
	        }
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
              collectFrontier(y, w, statMap);
            }
          }
        }
      }
      System.out.printf("XXX PROF phase 1 %.02f%n", (1e-9)*(System.nanoTime()-start));
  }
  
  
  public void collectFrontier(int head, Clause c, HashMap<Integer, TupleStats> statMap) {
	  if (belongsToLib(tupleIndex.getTuple(head))) {
		  TupleStats ts;
		  if (statMap.containsKey(head))
			  ts = statMap.get(head);
		  else {
			  ts = new TupleStats();
			  statMap.put(head, ts);
		  }
		  boolean isFrontier = false;
		  for (int i = 0; i < c.ls.length - 1; i++) {
			  if (!belongsToLib(tupleIndex.getTuple(-c.ls[i]))) {
				  isFrontier = true;
			  }
		  }
		  if (isFrontier) {
			  TIntHashSet fset = new TIntHashSet();
			  fset.add(head);
			  ts.frntSetList.clear();
			  ts.frntSetList.add(fset);
		  } else {
			  int currSz = ts.frntSetList.size();
			  boolean more = getOneMore(currSz);
			  if (more) {
				  boolean changed = false;
				  for (int i = 0; i < c.ls.length - 1; i++) {
					  if (statMap.containsKey(-c.ls[i])) {
						  TupleStats sub = statMap.get(-c.ls[i]);
						  ts.updateCountersCurrDisjunct(sub);
						  changed = true;
					  }
				  }
				  if (changed) {
					  ts.frntSetList.addAll(ts.currDisjunctFrntSetList);
					  ts.currDisjunctFrntSetList.clear();
				  }
			  }
		  }
	  }
  }
  
  
  public TupleIndex getTupleIndex() { return tupleIndex; }

  
  private boolean getOneMore(int sz) {
	  if (sz == 0)
		  return true;
	  else if (sz == 1) {
		  int rand = TupleStats.randInt(0, 100000);
		  if (rand < disjunctProbPercent * 1000)
			  return true;
		  else
			  return false;
	  } 
	  else 
		  return false;
  }
  
  private boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		for (int i = 0; i < dArr.length; i++) {
			if (mark.containsKey(dArr[i].getName()))
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
		}
		String ret = " ";
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
	 	else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}

  private void addClause(int[] ls) {
    // TODO: profile to see if subsumption is important
    clauses.add(new Clause(ls));
  }

  // NOTE: a null from paramHandler.transform is interpreted as "reset tuple"
  private int[] clauseOfConstraint(ConstraintItem c) {
    int x;
    literals.clear();

    x = tupleIndex.getIndex(c.headTuple);
    literals.add(x);
    if (sliceGraph) unusedTuples.add(x);
    if (sliceGraph && !belongsToLib(c.headTuple)) appDerivedTuples.add(x);
    
    for (Tuple st: c.subTuples) {
      x = tupleIndex.getIndex(st);
      if (sliceGraph) unusedTuples.add(x);
      literals.add(-x);
    }
    return Ints.toArray(literals);
  }

  

  private static final class Clause {
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

