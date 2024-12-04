package chord.project.analyses.softrefine;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.project.Config;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

/**
 * Generate the constraint file within one iteration.
 *
 * @author Xin. And modified slightly by Hongseok.
 *
 */
public class MaxSatGenerator {
	public static boolean DEBUG = false;

	private class TupleMap {
		private TObjectIntMap<Tuple> tupleDic;
		private ArrayList<Tuple> tuplePool;
		public TupleMap() {
			tupleDic = new TObjectIntHashMap<Tuple>();
			tuplePool = new ArrayList<Tuple>();
			tuplePool.add(null);// add null to make the index aligned with the Dic
		}
		public int getIndex(Tuple t) {
			int ret = tupleDic.get(t);
			if (ret <= 0) {
				ret = tuplePool.size();
				tupleDic.put(t, ret);
				tuplePool.add(t);
			}
			return ret;
		}
		public Tuple getTuple(int i) { return tuplePool.get(i); }
		public int size() { return tupleDic.size(); }
	}

	private String configFiles[];
	private ParamTupleConsHandler paramHandler;
	private TupleMap tupleVarMap;
	private List<LookUpRule> rules;
	private Fun<Tuple,Tuple> transformer;

	private List<LookUpRule> initRules() {
		List<LookUpRule> rules = new ArrayList<LookUpRule>();
		for (String conFile : configFiles) {
			try {
				Scanner sc = new Scanner(new File(conFile));
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (!line.equals("")) {
						LookUpRule rule = new LookUpRule(line);
						rules.add(rule);
					}
				}
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		paramHandler.init(rules);
		return rules;
	}

	public MaxSatGenerator(final String configFiles[], final ParamTupleConsHandler paramHandler) {
		this.configFiles = configFiles;
		this.paramHandler = paramHandler;
		this.tupleVarMap = new TupleMap();
		this.rules = initRules();
		this.transformer = new Fun<Tuple,Tuple>() {
			public Tuple apply(Tuple t) {
				if (t == null) return null;
				Pair<Tuple, Boolean> pair = paramHandler.transform(t);
				if (pair != null) return pair.val0;
				else return null;
			}
		};
	}

	public int getIndex(Tuple t) {
		return tupleVarMap.getIndex(t);
	}
	public void update() {
		for (LookUpRule r : rules) { r.update(); }
	}

	private class MaxSatInput {
		private Set<Tuple> allTuples;
		private Set<ConstraintItem> hardCons;
		private Set<ConstraintItem> softCons;

		public MaxSatInput() {
			allTuples = new HashSet<Tuple>();
			hardCons = new HashSet<ConstraintItem>();
			softCons = new HashSet<ConstraintItem>();
		}

		private void addTuples(ConstraintItem ci) {
			Pair<Tuple, Set<Tuple>> pair = ci.split(transformer);
			if (pair.val0 != null) allTuples.add(pair.val0);
			for (Tuple t : pair.val1) { if (t != null) allTuples.add(t); }
		}
		public void addHard(ConstraintItem ci) {
			addTuples(ci);
			hardCons.add(ci);
		}
		public void addSoft(ConstraintItem ci) {
			addTuples(ci);
			softCons.add(ci);
		}
		public void addHardTuple(Tuple t, boolean notNegated) {
			ConstraintItem ci = new ConstraintItem(1);
			if (notNegated) {
				ci.headTuple = t;
				addHard(ci);
			}
			else {
				ci.headTuple = null;
				ci.subTuples.add(t);
				addHard(ci);
			}
		}
		public void addSoftTuple(Tuple t, boolean notNegated, int weight) {
			ConstraintItem ci = new ConstraintItem(weight);
			if (notNegated) {
				ci.headTuple = t;
				addSoft(ci);
			}
			else {
				ci.headTuple = null;
				ci.subTuples.add(t);
				addSoft(ci);
			}
		}

		public int numTuples() { return allTuples.size(); }
		public int numHard() { return hardCons.size(); }
		public int numSoft() { return softCons.size(); }
		public int sumSoft() {
			int sum = 0;
			for(ConstraintItem ci : softCons) sum += ci.getWeight();
			return sum;
		}

		public void foreachTuples(VFun<Tuple> action) {
			for (Tuple t : allTuples) action.apply(t);
		}
		public void foreachHard(VFun<ConstraintItem> action) {
			for (ConstraintItem ci : hardCons) action.apply(ci);
		}
		public void foreachSoft(VFun<ConstraintItem> action) {
			for (ConstraintItem ci : softCons) action.apply(ci);
		}
		public Set<Tuple> getParamTuples() {
			Set<Tuple> params = new HashSet<Tuple>();
			for (Tuple t : allTuples)
				if (paramHandler.isParam(t)) params.add(t);
			return params;
		}
	}

	private class DerivationManager {
		private Map<Tuple, Set<ConstraintItem>> edges;
		private Map<Tuple, Set<Tuple>> reachSet;

		public DerivationManager() {
			edges = new HashMap<Tuple, Set<ConstraintItem>>();
			reachSet = new HashMap<Tuple, Set<Tuple>>();
		}
		private Set<ConstraintItem> lookupOrAdd(Tuple t) {
			if (edges.containsKey(t)) return edges.get(t);
			else {
				Set<ConstraintItem> targetSets = new HashSet<ConstraintItem>();
				edges.put(t,targetSets);
				return targetSets;
			}
		}
		private Set<ConstraintItem> lookup(Tuple t) {
			if (edges.containsKey(t)) return edges.get(t);
			else return null;
		}
		public void addEdge(ConstraintItem ci) {
			if (ci.headTuple == null) return;
			else {
				Set<ConstraintItem> targets = lookupOrAdd(ci.headTuple);
				targets.add(ci);
			}
		}
		private void reach(Set<Tuple> result, Set<Tuple> todo) {
			if (!todo.isEmpty()) {
				result.addAll(todo);
				Set<Tuple> next = new HashSet<Tuple>();
				for (Tuple t : todo) {
					Set<ConstraintItem> nextOfT = lookup(t);
					if (nextOfT != null)
						for (ConstraintItem ci : nextOfT)
							next.addAll(ci.subTuples);
				}
				todo.addAll(next);
				todo.removeAll(result);
				reach(result, todo);
			}
		}
		public void computeReach(Tuple t) {
			Set<Tuple> result = new HashSet<Tuple>();
			Set<Tuple> todo = new HashSet<Tuple>();
			todo.add(t);
			reach(result, todo);
			reachSet.put(t, result);
		}
		public Set<Tuple> computeReachOneStep(Tuple t) {
			Set<Tuple> result = new HashSet<Tuple>();
			Set<ConstraintItem> nextOfT = lookup(t);
			if (nextOfT != null)
				for (ConstraintItem ci : nextOfT)
					result.addAll(ci.subTuples);
			return result;
		}
		public Set<Tuple> getReach(Tuple t) {
			if (reachSet.containsKey(t)) {
				Set<Tuple> resultSet = new HashSet<Tuple>();
				resultSet.addAll(reachSet.get(t));
				return resultSet;
			}
			else return null;
		}
		public int getEdgesKeySize() {
			return edges.size();
		}
		public int getEdgesValueSize() {
			int size = 0;
			for (Set<ConstraintItem> value : edges.values())
				size += value.size();
			return size;
		}
		private void printDerivationTodo(Set<Tuple> todo, int count, int depth) {
			if (count >= depth || todo.isEmpty()) return;

			System.out.println("********** [" + count + "] **********");
			Set<Tuple> next = new HashSet<Tuple>();
			for (Tuple t : todo) {
				Set<ConstraintItem> ciSet = lookup(t);
				if (ciSet != null) {
					ConstraintItem maxCi = null;
					int max = -1;
					for (ConstraintItem ci : ciSet) {
						if (max < ci.subTuples.size()) {
							maxCi = ci;
							max = maxCi.subTuples.size();
						}
					}
					System.out.println(maxCi);
					System.out.println("\t" + t.toVerboseString());
					next.addAll(maxCi.subTuples);
				}
			}
			todo.clear();
			todo.addAll(next);
			printDerivationTodo(todo, count+1, depth);
		}
		public void printDerivation(Tuple t, int depth) {
			Set<Tuple> todo = new HashSet<Tuple>();
			todo.add(t);
			printDerivationTodo(todo, 0, depth);
		}
	}

	// very hack routine used to see what goes on
	private void printDerivationQuery(Set<Tuple> queries, DerivationManager derMan) {
		Tuple preTarget = null;
		for (Tuple t : queries) {
			if (t.getIndices()[0] == 1092) preTarget = t;
		}
		if (preTarget == null) {
			System.out.println("No derivation");
			return;
		}
		Tuple target = null;
		Set<Tuple> candidates = derMan.computeReachOneStep(preTarget);
		for (Tuple t : candidates) {
			if (t.getRelName().equals("insvIM") && t.getIndices()[1] == 251) target = t;
		}

		Set<Tuple> current = new HashSet<Tuple>();
		Set<Tuple> temp = new HashSet<Tuple>();
		current.add(target);
		System.out.println(target.toVerboseString());
		for (int i = 0; i < 10; i++) {
			System.out.println("********** [" + i + "] **********");
			for (Tuple t : current) {
				Set<Tuple> next = derMan.computeReachOneStep(t);
				System.out.println(t.toVerboseString());
				System.out.println("\t" + next);
				temp.addAll(next);
			}
			current.clear();
			current.addAll(temp);
			temp.clear();
		}
	}

	private class Stat {
		Set<joeq.Class.jq_Class> classes = new HashSet<joeq.Class.jq_Class>();
	}

	private void computeStatFromTuple(Tuple t, Stat result) {
		int size = t.getArity();
	 	for (int i = 0; i < size; i++) {
			Object value = t.getValue(i);
			if (value instanceof joeq.Class.jq_Method) {
				joeq.Class.jq_Method method = (joeq.Class.jq_Method)value;
				result.classes.add(method.getDeclaringClass());
			}
		}
	}

	private Stat computeStat(Set<Tuple> set) {
		Stat s = new Stat();
		for (Tuple t : set) computeStatFromTuple(t, s);
		return s;
	}

	private Set<joeq.Class.jq_Class> computeThenPrintStat(DerivationManager derMan, Set<Tuple> set, Set<Tuple> exception) {
		Set<joeq.Class.jq_Class> accSet = new HashSet<joeq.Class.jq_Class>();
		for (Tuple t : set) {
			Set<Tuple> derTuples = derMan.getReach(t);
			derTuples.removeAll(exception);
			Set<joeq.Class.jq_Class> classes = computeStat(derTuples).classes;
			System.out.println(t.toVerboseString() + " : " + classes);
			for (Tuple t1 : derTuples)
				System.out.println("\t" + t1.toVerboseString());
			accSet.addAll(classes);
		}
		System.out.println("Total number: " + accSet.size());
		return accSet;
	}

	public void solve(Set<Tuple> tSet, Set<Tuple> fSet, final Fun<ConstraintItem,Boolean> isHard) {
		File consFile = new File(Config.outDirName + File.separator + "all.maxsat");
		File expConsFile = new File(Config.outDirName + File.separator + "all.explicit");
		try {
			final MaxSatInput maxSat = new MaxSatInput();
			VFun<ConstraintItem> infoCollector = new VFun<ConstraintItem>() {
				private boolean isAxiom(Tuple hd, Set<Tuple> conds) {
					if (hd == null) {
						if (conds == null) { return false; } else { return (conds.size() <= 1); }
					}
					else {
						return (conds == null);
					}
				}
				public void apply(ConstraintItem ci) {
					Pair<Tuple, Set<Tuple>> pair = ci.split(transformer);
					Tuple hd = pair.val0;
					Set<Tuple> conds = pair.val1;
					if (isAxiom(hd,conds) || isHard.apply(ci)) { maxSat.addHard(ci); } else { maxSat.addSoft(ci); }
					if (hd != null) maxSat.addSoftTuple(hd, true, 1);
				}
			};
			for (LookUpRule r : rules) r.foreachConstr(infoCollector);
			for (Tuple t : tSet) maxSat.addHardTuple(t, false);
			for (Tuple t : fSet) maxSat.addHardTuple(t, true);
			Set<Tuple> params = maxSat.getParamTuples();
			for (Tuple t : params) maxSat.addHardTuple(t, true);
			final int top = maxSat.sumSoft()+1;

			// Start file generation
			final PrintWriter pw = new PrintWriter(consFile);
			final PrintWriter pw1 = new PrintWriter(expConsFile);
			pw.println("c " + tSet.toString());
			if(DEBUG) pw1.println("Queries: " + tSet.toString());
			pw.print("p wcnf");
			if(DEBUG) pw1.println("=================================");
			pw.print(" " + maxSat.numTuples());
			pw.print(" " + (maxSat.numHard() + maxSat.numSoft()));
			pw.println(" " + top);

			final VFun<ConstraintItem> printer = new VFun<ConstraintItem>() {
				public void apply(ConstraintItem ci) {
					if (ci.headTuple != null) {
						Pair<Tuple, Boolean> tht = paramHandler.transform(ci.headTuple);
						int hidx = tupleVarMap.getIndex(tht.val0);
						if (!tht.val1) hidx = 0 - hidx;
						pw.print(hidx);
					}
					for (Tuple st : ci.subTuples) {
						Pair<Tuple, Boolean> tst = paramHandler.transform(st);
						if (tst != null) {
							int sidx = tupleVarMap.getIndex(tst.val0);
							if (!tst.val1) sidx = 0 - sidx;
							pw.print(" " + (0 - sidx));
						}
					}
					pw.println(" 0");
					if (DEBUG) pw1.println(ci);
				}
			};
			VFun<ConstraintItem> softPrinter = new VFun<ConstraintItem>() {
				public void apply(ConstraintItem ci) {
					pw.print(ci.getWeight() + " ");
					printer.apply(ci);
				}
			};
			VFun<ConstraintItem> hardPrinter = new VFun<ConstraintItem>() {
				public void apply(ConstraintItem ci) {
					pw.print(top + " ");
					printer.apply(ci);
				}
			};
			maxSat.foreachSoft(softPrinter);
			maxSat.foreachHard(hardPrinter);

			pw.flush();
			pw.close();
			pw1.flush();
			pw1.close();

			// Prining some useful statistics
			System.out.println("Generated an input file to the MaxSat solver.");
			System.out.println("Number of soft clauses: " + maxSat.numSoft());
			System.out.println("Number of hard clauses: " + maxSat.numHard());
			System.out.println("Number of variables: " + maxSat.numTuples());
			System.out.println("Value of top: " + top);

			final DerivationManager derMan = new DerivationManager();
			VFun<ConstraintItem> edgeCollector = new VFun<ConstraintItem>() {
				public void apply(ConstraintItem ci) {
					derMan.addEdge(ci);
				}
			};
			maxSat.foreachHard(edgeCollector);
			maxSat.foreachSoft(edgeCollector);
			for (Tuple t : tSet) derMan.computeReach(t);
			for (Tuple t : fSet) derMan.computeReach(t);
			int numTuplesDer = 0;
			Set<Tuple> tDerTuples = new HashSet<Tuple>();
			Set<Tuple> fDerTuples = new HashSet<Tuple>();
			Set<Tuple> tfDerTuples = new HashSet<Tuple>();
			boolean firstTime = true;
			for (Tuple t : tSet) {
				Set<Tuple> derTuples = derMan.getReach(t);
				numTuplesDer += derTuples.size();
				if (!firstTime) tDerTuples.retainAll(derTuples);
				else {
					firstTime = false;
					tDerTuples.addAll(derTuples);
				}
			}
			firstTime = true;
			for (Tuple t : fSet) {
				Set<Tuple> derTuples = derMan.getReach(t);
				numTuplesDer += derTuples.size();
				if (!firstTime) fDerTuples.retainAll(derTuples);
				else {
					firstTime = false;
					fDerTuples.addAll(derTuples);
				}
			}
			tfDerTuples.addAll(tDerTuples); tfDerTuples.retainAll(fDerTuples);
			int numQueries = tSet.size() + fSet.size();
			System.out.println("Number of vertices: " + derMan.getEdgesKeySize());
			System.out.println("Number of edges: " + derMan.getEdgesValueSize());
			System.out.println("Number of queries: " + numQueries);
			System.out.println("Number of tuples in all derivations per each query: " + (numTuplesDer / numQueries));
			System.out.println("Number of tuples common for all true queries: " + tDerTuples.size());
			System.out.println("Number of tuples common for all false queries: " + fDerTuples.size());
			System.out.println("Number of tuples common for all queries: " + tfDerTuples.size());
			System.out.println("Derivations of true queries: ");
			for (Tuple t : tSet)
				derMan.printDerivation(t, 20);
			System.out.println("Derivations of false queries: ");
			for (Tuple t : fSet)
				derMan.printDerivation(t, 20);

			String cmd[] = new String[2];
			File result = new File(Config.outDirName + File.separator + "result");
			cmd[0] = System.getenv("CHORD_INCUBATOR") + File.separator + "src"
						+ File.separator + "chord" + File.separator + "project"
						+ File.separator + "analyses" + File.separator + "softrefine"
						+ File.separator + "mifumax-mac";
			cmd[1] = consFile.getAbsolutePath();
			System.out.println("Start the solver.");
			Timer t = new Timer();
			t.init();
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			PrintWriter rpw = new PrintWriter(result);
			while ((line = in.readLine()) != null) {
				rpw.println(line);
			}
			in.close();
			rpw.flush();
			rpw.close();
			p.waitFor();
			System.out.println("Exit from solver.");
			if (p.exitValue() != 0)
				throw new RuntimeException("The solver did not terminate normally");
			t.done();
			System.out.println("Solver exclusive time: " + t.getExclusiveTimeStr());

			interpreteResult(result, maxSat);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private Model buildModel(final Set<Tuple> trueTuples) {
		return new Model() {
			@Override
			public boolean isTrue(Tuple t) {
				if (trueTuples.contains(t)) { return true; }
				else { return (paramHandler.transform(t) == null); }
			}
		};
	}

	private void interpreteResult(File f, MaxSatInput maxSat) {
		try {
			Scanner sc = new Scanner(f);
			Set<Tuple> trueTuples = new HashSet<Tuple>();
			while (sc.hasNext()) {
				String line = sc.nextLine();
				if (line.startsWith("s ")) {
					if (line.startsWith("s UNSATISFIABLE"))
						return;
					if (!line.startsWith("s OPTIMUM FOUND"))
						throw new RuntimeException("Expecting a solution but got " + line);
				}
				if (line.startsWith("v ")) {
					Scanner lineSc = new Scanner(line);
					String c = lineSc.next();
					if (!c.trim().equals("v"))
						throw new RuntimeException("Expected char of a solution line: " + c);
					while (lineSc.hasNext()) {
						int i = lineSc.nextInt();
						if (i > 0) {
							Tuple t = tupleVarMap.getTuple(i);
							trueTuples.add(t);
						}
					}
				}
			}
			final Model model = buildModel(trueTuples);

			// Printing some useful statistics
			final Set<ConstraintItem> trueConsts = new HashSet<ConstraintItem>();
			final Set<ConstraintItem> falseConsts = new HashSet<ConstraintItem>();
			VFun<ConstraintItem> evaluator = new VFun<ConstraintItem>() {
				public void apply(ConstraintItem ci) {
					if (ci.evaluate(model)) { trueConsts.add(ci); }
					else { falseConsts.add(ci); }
				}
			};
			maxSat.foreachSoft(evaluator);
			maxSat.foreachHard(evaluator);

			System.out.println("Obtained an output from the MaxSat solver.");
			System.out.println("Number of constraints that remain true: " + trueConsts.size());
			System.out.println("Number of constraints that become false: " + falseConsts.size());
			Set<ConstraintItem> falseAxioms = new HashSet<ConstraintItem>();
			Set<ConstraintItem> falseRules = new HashSet<ConstraintItem>();
			for (ConstraintItem ci: falseConsts) {
				if (ci.subTuples.isEmpty()) { falseAxioms.add(ci); } else { falseRules.add(ci); }
			}
			System.out.println("Falsified rule instances: " + falseRules);
			System.out.println("Falsified axioms: " + falseAxioms);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
