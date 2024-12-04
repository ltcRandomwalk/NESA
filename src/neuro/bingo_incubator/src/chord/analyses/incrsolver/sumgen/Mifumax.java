package chord.analyses.incrsolver.sumgen;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import chord.project.Config;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

public class Mifumax extends MaxsatSolver {

	private ExecutorService executor;
	
	public Mifumax(List<Clause> hardClauses,
		List<Pair<Long, Clause>> softClauses) {
		super(hardClauses, softClauses);
		this.executor =
			Executors.newCachedThreadPool(new NamedThreadFactory(
				"Incremental thread pool", true));
	}

	@Override
	public Set<Integer> solveMaxSat() {
		boolean DEBUG = true;

		File consFile =
			new File(Config.outDirName + File.separator + this.hashCode()
				+ "_mifu.wcnf");
		File expFile =
			new File(Config.outDirName + File.separator + this.hashCode()
				+ "_mifu.explicit");
		try {
			Timer t1 = new Timer("Maxsat input timer");
			t1.init();
			PrintWriter pw = new PrintWriter(consFile);
			PrintWriter pw1 = new PrintWriter(expFile);

			pw.println("c incremental solver: summary generation");
			pw.println("p wcnf " + this.getNumMaxSatVars(this.hardClauses) + " " + this.getNumMaxSatCons() + " "
				+ Long.MAX_VALUE);

			for (Clause gc : this.hardClauses) {
				pw.print(Long.MAX_VALUE);
				if (DEBUG) pw1.print("Hard Clause: " );
				for (int lit : gc.ls) {
					pw.print(" " + lit);
					if (DEBUG) pw1.print(lit < 0 ? "NOT" : "");
					if (DEBUG) pw1.print(lit + ", ");
					//if (DEBUG) pw1.print(tuplePlusIndex.getTuple(Math.abs(lit)) + ", ");
				}
				pw.println(" 0");
				if (DEBUG) pw1.println("");


			}
			
			for (Pair<Long, Clause> idCost : this.softClauses) {
				pw.print(idCost.val0);
				pw.print(" " + idCost.val1.ls[0]);
				pw.println(" 0");
				if (DEBUG) pw1.println("Soft Clause: " + idCost.val1.ls[0] + ", Weight: " + idCost.val0);
			//	if (DEBUG) pw1.println("Soft Clause: " + tuplePlusIndex.getTuple(idCost.val0) + ", Weight: " + idCost.val1);
			}
			pw.flush();
			pw.close();

			pw1.flush();
			pw1.close();

			t1.done();
			System.out.println("MaxSat input generation time: " + t1.getExclusiveTimeStr());

			String cmd[] = new String[2];
			String mifuPath = System.getenv("CHORD_MAIN") + File.separator + "src" + File.separator +
				"chord" + File.separator + "project" + File.separator + "analyses" + File.separator +
				"provenance" + File.separator + "mifumax";
			cmd[0] = mifuPath;
			cmd[1] = consFile.getAbsolutePath();

			System.out.println("Start the MAXSAT solver:");

			Timer t2 = new Timer("MaxSat");
			t2.init();

			ProcessBuilder pb = new ProcessBuilder(cmd[0], cmd[1]);
			pb.redirectErrorStream(true);
			final Process p = pb.start();
			ResultInterpreter ri =
				new ResultInterpreter(p.getInputStream());

			Future<Set<Integer>> futureResult =
				this.executor.submit(ri);

			if (p.waitFor() != 0) {
				throw new RuntimeException(
					"The MAXSAT solver did not terminate normally");
			}

			Set<Integer> retP = futureResult.get();

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
			t2.done();
			System.out.println("MaxSat solving time: " + t2.getExclusiveTimeStr());
			return retP;
		} catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	class ResultInterpreter implements Callable<Set<Integer>> {
		private InputStream resultStream;

		public ResultInterpreter(InputStream resultStream) {
			this.resultStream = resultStream;
		}

		@Override
		public Set<Integer> call() throws Exception {
			BufferedReader in =
				new BufferedReader(new InputStreamReader(this.resultStream));
			Set<Integer> sol = new HashSet<Integer>();
			double unsatWeight = 0.0;
			String line;
			while ((line = in.readLine()) != null) {
				if (line.startsWith("s ")) {
					if (line.startsWith("s UNSATISFIABLE")) {
						return null;
					}
					if (!line.startsWith("s OPTIMUM FOUND")) {
						throw new RuntimeException(
							"Expecting a solution but got " + line);
					}
				}
				if (line.startsWith("o ")) {
					String lsplits[] = line.split(" ");
					unsatWeight = Double.parseDouble(lsplits[1]);
				}
				if (line.startsWith("v ")) {
					String[] lsplits = line.split(" ");
					for (String s : lsplits) {
						s = s.trim();
						if (s.equals("v")) {
							continue;
						}
						int i = Integer.parseInt(s);
						if (i > 0) {
							sol.add(i);
						}
					}
				}
			}
			in.close();
			return sol;
		}
	}

}
