package chord.analyses.incrsolver.sumgen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import chord.project.Config;
import chord.util.Timer;
import chord.util.tuple.object.Pair;


public class Mcsls extends MaxsatSolver {
//	private double inflateRate = 0.5;
	private double inflateRate = 100;
	private int inflateLimit;
	private long top = Long.MAX_VALUE;
	private int numSols = 10;
	private ExecutorService executor;

	public Mcsls(List<Clause> hardClauses,
		List<Pair<Long, Clause>> softClauses, int numSols) {
		super(hardClauses, softClauses);
		this.executor =
			Executors.newCachedThreadPool(new NamedThreadFactory(
				"Incremental thread pool", true));
		this.numSols = numSols;
	}

	@Override
	public Set<Integer> solveMaxSat() {
		Set<Integer> ret = new HashSet<Integer>();

		this.inflateLimit = (int) (this.hardClauses.size() * this.inflateRate);
		System.out.println("Start the MCSLS solving process:");
		Timer t1 = new Timer("mcsls");
		t1.init();
		
		TreeMap<Long, List<Clause>> weightMap =
			new TreeMap<Long, List<Clause>>();

		for (Pair<Long, Clause> gc : softClauses) {
			List<Clause> wList = weightMap.get(Math.abs(gc.val0));
			if (wList == null) {
				wList = new ArrayList<Clause>();
				weightMap.put(Math.abs(gc.val0), wList);
			}
			wList.add(gc.val1);
		}
		List<Map.Entry<Long, List<Clause>>> clauseGroups = new ArrayList<Map.Entry<Long, List<Clause>>>(weightMap.entrySet());
		
	/*	int groupSize = clauseGroups.size();
		
		Map.Entry<Integer, List<Clause>> endGroup = clauseGroups.get(groupSize - 1);
		List<Clause> hardClauses = null;
		
		if (endGroup.getKey() == top) {
			hardClauses = endGroup.getValue();
			clauseGroups.remove(groupSize - 1);
		} else {
			hardClauses = new ArrayList<Clause>();
		}
	*/	
		List<Pair<Long, List<Clause>>> transClauseGroups = new ArrayList<Pair<Long, List<Clause>>>();

		for (Map.Entry<Long, List<Clause>> group : clauseGroups) {
			if (transClauseGroups.size() == 0) {
				transClauseGroups.add(new Pair<Long, List<Clause>>(group.getKey(), group.getValue()));
				continue;
			}
			Pair<Long, List<Clause>> lastGroup = transClauseGroups.get(transClauseGroups.size() - 1);
			double lgWeight = lastGroup.val0 * lastGroup.val1.size();
			if (lgWeight <= group.getKey()) {
				transClauseGroups.add(new Pair<Long, List<Clause>>(group.getKey(), group.getValue()));
				continue;
			}
			int splitNum = (int) (group.getKey() / lastGroup.val0);
			// MCSls does not support weights. To compensate it, is to make
			// copies of certain rules. But we also do not want the number of
			// constraints to blow up too much.
			if (splitNum * group.getValue().size() > this.inflateLimit) {
				lastGroup.val1.addAll(group.getValue());
			} else {
				for (int i = 0; i < splitNum; i++) {
					for (Clause cgc : group.getValue()) {
						lastGroup.val1.add(cgc);
					}
				}
			}
		}

		for (int i = transClauseGroups.size() - 1; i >= 0; i--) {
			List<Clause> softClauses = null;
			softClauses = transClauseGroups.get(i).val1;
			Set<Clause> cgcToRemove =
				this.relaxConstraints(hardClauses, softClauses);
			softClauses.removeAll(cgcToRemove);
			hardClauses.addAll(softClauses);
		}
		List<Clause> finalGcs = new ArrayList<Clause>(hardClauses);
		Set<Integer> solution = (new Mifumax(finalGcs, new ArrayList<Pair<Long, Clause>>())).solveMaxSat();
		ret = solution;

		t1.done();
		System.out.println("MCSls solving time: " + t1.getExclusiveTimeStr());
		return ret;
	}

	/**
	 * This method use mcsls solver to relax over-constrained problems. It means
	 * if the clauses in the return value are removed from the soft clauses,
	 * there would be no conflict in constraints.
	 *
	 * @param hardClauses
	 * @param softClauses
	 * @return
	 */
	private Set<Clause> relaxConstraints(List<Clause> hardClauses,
		List<Clause> softClauses) {
		File consFile =
			new File(Config.outDirName + File.separator + this.hashCode()
				+ "_mcsls.wcnf");
		try {
			PrintWriter pw = new PrintWriter(consFile);
			List<Clause> clauses = new ArrayList<Clause>();
			clauses.addAll(hardClauses);
			clauses.addAll(softClauses);
			long nbvar = this.getNumMaxSatVars(clauses);
			long nclauses = hardClauses.size() + softClauses.size();
			long top = softClauses.size() + 1;
			pw.println("p wcnf " + nbvar + " " + nclauses + " " + top);
			for (Clause gc : softClauses) {
				pw.print(1);
				for (int l : gc.ls) {
					pw.print(" " + l);
				}
				pw.println(" 0");
			}
			for (Clause gc : hardClauses) {
				pw.print(top);
				for (int l : gc.ls) {
					pw.print(" " + l);
				}
				pw.println(" 0");
			}
			pw.flush();
			pw.close();

			String mcslsPath = System.getenv("CHORD_INCUBATOR") + File.separator + "src" + File.separator +
				"chord" + File.separator + "analyses" + File.separator +
				"experiment" + File.separator + "solver" + File.separator + "mcsls";
			ProcessBuilder pb = new ProcessBuilder(mcslsPath, "-T", "3600", "-num", this.numSols
						+ "", "-alg", "cld", consFile.getAbsolutePath());
			pb.redirectErrorStream(true); // otherwise it might fill the pipe
			// and block
			final Process p = pb.start();
			ResultInterpreter ri =
				new ResultInterpreter(p.getInputStream(), softClauses, true);
			Future<Set<Clause>> futureResult = this.executor.submit(ri);
			Set<Clause> ret = futureResult.get();
			if (p.waitFor() != 0 && (ret == null || ret.size() == 0)) {
				System.out.println(ri.getLog());
				throw new RuntimeException(
					"The MAXSAT solver did not terminate normally");
			}
			if (p != null) {
				if (p.getOutputStream() != null) {
					p.getOutputStream().close();
				}
				if (p.getErrorStream() != null) {
					p.getErrorStream().close();
				}
				if (p.getInputStream() != null) {
					p.getInputStream().close();
				}
				p.destroy();
			}
			return ret;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	class ResultInterpreter implements Callable<Set<Clause>> {
		private InputStream resultStream;
		private List<Clause> softClauses;
		private boolean realWeight;
		private StringBuffer log;

		public ResultInterpreter(InputStream resultStream,
			List<Clause> softClauses, boolean realWeight) {
			this.resultStream = resultStream;
			this.softClauses = softClauses;
			this.realWeight = realWeight;
		}

		@Override
		public Set<Clause> call() throws Exception {
			BufferedReader in =
				new BufferedReader(new InputStreamReader(this.resultStream));
	
			HashMap<Double, List<Clause>> solutions = new HashMap<Double, List<Clause>>();
			boolean timedout = false;
			this.log = new StringBuffer();
			String line;
			while ((line = in.readLine()) != null) {
				this.log.append(line + "\n");
				timedout |= line.startsWith("c mcsls timed out");
				if (!line.startsWith("c MCS: ")) {
					continue;
				}
				Scanner scanLine = new Scanner(line);
				scanLine.next(); // c
				scanLine.next(); // MCS:
				List<Clause> ca = new ArrayList<Clause>();
				while (scanLine.hasNextInt()) {
					int nint = scanLine.nextInt();
					ca.add(this.softClauses.get(nint - 1));
				}
				scanLine.close();
				solutions.put(this.calWeight(ca), ca);
			/*	if (ret == null) {
					ret = ca;
				} else if (this.calWeight(ca) < this.calWeight(ret)) {
					ret = ca;
				}
			*/	
			}
			in.close();
			Double[] sorted_cost = new Double[solutions.size()];
			solutions.keySet().toArray(sorted_cost);
			Arrays.sort(sorted_cost);
			if (sorted_cost.length != 0) {
				return new HashSet<Clause>(solutions.get(sorted_cost[0]));
			} else {
				return new HashSet<Clause>();
			}
		}

		private double calWeight(List<Clause> gcs) {
		/*	if (this.realWeight) {
				Set<Clause> dupRemoved = new HashSet<Clause>(gcs);
				double ret = 0;
				for (Clause gc : dupRemoved) {
					ret += gc.weight;
				}
				return ret;
				
			} else {	
				return gcs.size();
			}
		*/	
			return gcs.size();
		}

		public String getLog() {
			return this.log.toString();
		}
	}

}
