package chord.analyses.absmin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import chord.program.Program;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.BlackBox;
import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Scenario;
import chord.util.Execution;
import chord.util.Utils;

/**
 * Implementation of JobDispatcher to enforce the abstraction minimizing coarsening algorithm (POPL'11).
 * New jobs are generated as per the coarsening algorithm and job replies are incorporated to advance the
 * algorithm state.
 */
public class AbstractionMinimizer implements JobDispatcher {
	Execution EX = Execution.v();
	Random random = new Random();
	String sepMaj,sepMin;

	// If groups are already created and being loaded from disk, then if this flag is set,
	// rerun the client analysis, once per group, with job parameters based on the group parameters
	// This is particularly used to further refine the set of proven queries in each group 
	// (missing code to real code switch for minimal set if methods during missing method analysis)
	boolean switchToRealCase;

	// Sample the queries in play and only track these new sampled queries
	boolean sampleQueries;
	int sampleSize;

	Abstraction[] botX, topX;
	QueryFactory qFactory;
	AbstractionFactory aFactory;
	HashSet<String> queries;
	int aSize;  // Number of positions in any abstraction
	double initIncrProb = 0.5; // Coarsening algorithm parameter
	double incrThetaStepSize = 0.1; // Coarsening algorithm parameter
	int scanThreshold = 30; // Coarsening algorithm parameter
	String xmlToHtmlTask = null; // Chord Task to be run for conversion of generated XML files to HTML
	BlackBox box;

	int numScenarios = 0; // Total number of calls to the analysis oracle
	List<Group> groups = new ArrayList<Group>();

	Set<String> allY() { return queries; }

	/**
	 * @param isScan	Determines whether to use scan-coarsen or active-coarsen algorithm.
	 * @param qSet		Filter on set of queries to be considered.  Usually, it is null, but
	 *					if non-null, then only queries in this set are considered.
	 * @param aSet		Set of all abstractions.
	 */
	public AbstractionMinimizer(boolean isScan, Set<? extends Query> qSet, Set<? extends Abstraction> aSet,
			QueryFactory qFactory, AbstractionFactory aFactory,
			BlackBox box, String sepMaj, String sepMin, String xmlToHtmlTask,
			boolean switchToRealCase, boolean sampleQueries, int sampleSize) {
		this.sepMaj = sepMaj;
		this.sepMin = sepMin;
		this.xmlToHtmlTask = xmlToHtmlTask;
		this.switchToRealCase = switchToRealCase;
		this.sampleQueries = sampleQueries;
		this.sampleSize = sampleSize;
		this.box = box;
		this.qFactory = qFactory;
		this.aFactory = aFactory;
		this.aSize = aSet.size();
		this.botX = new Abstraction[aSize];
		this.topX = new Abstraction[aSize];

		int c = 0;
		for (Abstraction a : aSet) {
			botX[c] = a.copy();
			botX[c].minRefine();
			topX[c] = a.copy();
			topX[c].maxRefine();
			c++;
		}

		Scenario topSIn = new Scenario(ScenarioKind.COARSEN.toString(), Abstraction.concatAbstractions(encodeX(topX),sepMin), "", sepMaj);
		Scenario topSOut = new Scenario(box.apply(topSIn.toString()),sepMaj);
		HashSet<String> topY = new HashSet<String>(Arrays.asList(Query.splitQueries(topSOut.getOut(),sepMin)));
		doneTopUnprovenQueries(topY);

		Scenario botSIn = new Scenario(ScenarioKind.COARSEN.toString(), Abstraction.concatAbstractions(encodeX(botX),sepMin), "", sepMaj);
		Scenario botSOut = new Scenario(box.apply(botSIn.toString()),sepMaj);
		HashSet<String> botY = new HashSet<String>(Arrays.asList(Query.splitQueries(botSOut.getOut(),sepMin)));
		doneBotUnprovenQueries(botY);

		EX.logs("bot: %s tuples", botY.size());
		EX.logs("top: %s tuples", topY.size());

		Set<String> filteredQueries;
		if (qSet == null)
			filteredQueries = null;
		else {
			filteredQueries = new HashSet<String>();
			for (Query q: qSet) {
				filteredQueries.add(q.encode());
			}
		}

		// Keep only queries that bottom was unable to prove but top was able to prove
		this.queries = new HashSet();
		for (String y: botY) {
			if (!topY.contains(y)) { // Unproven by bottom, proven by top
				if (filteredQueries == null)
					queries.add(y);
				else if (filteredQueries.contains(y))
					queries.add(y);
			}
		}

		boolean error = false;
		for (String y : topY) {
			if (!botY.contains(y)) {
				System.out.println("Query unproven by top but proven by bottom: " + y);
				error = true;
			}
		}
		if (error) {
			System.out.println("Quitting (see above queries).");
			System.exit(1);
		}

		if (sampleQueries) {
			sampleQueries();
		}

		doneTrackedQueries(queries);

		botY.clear();
		topY.clear();

		EX.logs("|Y| = %s", queries.size());
		EX.putOutput("numY", queries.size());
		EX.putOutput("topComplexity", complexity(topX));

		groups.add(new Group(isScan, queries));

		outputStatus();
		loadGroups();
		loadScenarios();
	}

	public void doneTopUnprovenQueries(Set<String> queries) { }
	public void doneBotUnprovenQueries(Set<String> queries) { }
	public void doneTrackedQueries(Set<String> queries) { }

	private void sampleQueries() {
		int remSize = queries.size() - sampleSize;
		for (int i = 0; i < remSize; i++) {
			Object[] qArr = queries.toArray();
			int target = random.nextInt(queries.size());
			queries.remove(qArr[target]);
		}
		assert(queries.size() <= sampleSize);
	}

	/**
	 * Load pre-computed groups from disk
	 */
	void loadGroups() {
		String path = EX.path("groups");
		if (!new File(path).exists()) return;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
			String line;
			groups.clear();
			while ((line = in.readLine()) != null) {
				// Format: <isScan> ## <step size> ## <lower> ## <upper> ## queries
				String[] tokens = Utils.split(line, "##", false, false, 0);
				Group g = new Group(Boolean.parseBoolean(tokens[0]),new HashSet<String>(Arrays.asList(Query.splitQueries(tokens[4], sepMin))));
				g.incrTheta = invLogistic(Double.parseDouble(tokens[1]));
				g.lowerX = decodeX(Abstraction.splitAbstractions(tokens[2], sepMin));
				g.upperX = decodeX(Abstraction.splitAbstractions(tokens[3], sepMin));
				g.updateStatus();

				if(g.done && switchToRealCase){
					g.scanning = true; //To ensure that just 1 worker handles the real case for each group
					g.done = false;

				}else{
					switchToRealCase = false;
				}

				groups.add(g);
			}
			outputStatus();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Load pre-computed scenarios from disk
	 */
	void loadScenarios() {
		String scenariosPath = EX.path("scenarios");
		if (!new File(scenariosPath).exists()) return;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(scenariosPath)));
			String line;
			while ((line = in.readLine()) != null)
				incorporateScenario(new Scenario(line,sepMaj), false);
			in.close();
			outputStatus();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	List<Group> getCandidateGroups() {
		List<Group> candidates = new ArrayList();
		for (Group g : groups)
			if (g.wantToLaunchJob())
				candidates.add(g);
		return candidates;
	}

	/**
	 * Create new job to be sent to a worker
	 */
	@Override
	public Scenario createJob() {
		List<Group> candidates = getCandidateGroups();
		if (candidates.size() == 0) return null;
		// Sample a group proportional to the number of effects in that group
		// This is important in the beginning to break up the large groups
		double[] weights = new double[candidates.size()];
		for (int i = 0; i < candidates.size(); i++)
			weights[i] = candidates.get(i).Y.size();
		int chosen = sample(weights);
		Group g = candidates.get(chosen);
		return g.createNewScenario();
	}

	@Override
	public boolean isDone() {
		for (Group g : groups)
			if (!g.done) return false;
		return true;
	}

	@Override
	public void onJobResult(Scenario scenario) {
		//incorporateScenario(scenario, true);
		incorporateScenario(scenario, false);
		outputStatus();
	}

	@Override
	public void onError(int scenarioID) {
		for (Group g : groups){
			g.jobCounts.remove(scenarioID);
		}
	}

	String render(Abstraction[] X, Set<String> Y) { return String.format("|X|=%s,|Y|=%s", complexity(X), Y.size()); }

	/** 
	 * Incorporate incoming scenario into all groups
	 */
	void incorporateScenario(Scenario scenario, boolean saveToDisk) {
		numScenarios++;
		if (saveToDisk) {
			PrintWriter f = Utils.openOutAppend(EX.path("scenarios"));
			f.println(scenario);
			f.close();
		}

		Abstraction[] X = decodeX(Abstraction.splitAbstractions(scenario.getIn(),sepMin));
		Set<String> Y = new HashSet<String>(Arrays.asList(Query.splitQueries(scenario.getOut(),sepMin)));

		if(switchToRealCase){
			for (Group g : groups){
				if(g.jobCounts.containsKey(scenario.getID())){
					EX.logs("Incorporating scenario id=%s,%s into %s groups (numScenarios = %s) into %s", scenario.getID(), render(X, Y), groups.size(), numScenarios, g);
					g.Y = (HashSet<String>) Y;
					g.update(scenario.getID(), X, true);
					break;
				}
			}

		}else{
			EX.logs("Incorporating scenario id=%s,%s into %s groups (numScenarios = %s)", scenario.getID(), render(X, Y), groups.size(), numScenarios);
			List<Group> newGroups = new ArrayList();
			boolean changed = false;
			for (Group g : groups)
				changed |= incorporateScenario(scenario.getID(), X, Y, g, newGroups);
			groups = newGroups;
			if (!changed) // Didn't do anything - probably an outdated scenario
				EX.logs("  Useless: |X|=%s,|Y|=%s", complexity(X), Y.size());
		}
	}

	/** 
	 * Incorporate incoming scenario into group g
	 */
	boolean incorporateScenario(int id, Abstraction[] X, Set<String> Y, Group g, List<Group> newGroups) {
		// Don't need this since Y is with respect to allY
		// Don't update on jobs we didn't ask for! (Important because we are passing around subset of queries which make sense only with respect to the group that launched the job)

		if (!g.inRange(X)) { // We asked for this job, but now it's useless
			g.jobCounts.remove(id);
			newGroups.add(g);
			return false;
		}

		// Now we can make an impact
		EX.logs("  into %s", g);

		HashSet<String> Y0 = new HashSet<String>();
		HashSet<String> Y1 = new HashSet<String>();
		for (String y : g.Y) {
			if (Y.contains(y)) Y1.add(y);
			else               Y0.add(y);
		}
		if (Y0.size() == 0 || Y1.size() == 0) { // Don't split: all of Y still behaves the same
			assert !(Y0.size() == 0 && Y1.size() == 0); // At least one must be true
			g.update(id, X, Y1.size() > 0);
			newGroups.add(g);
		}
		else {
			Group g0 = new Group(g, Y0);
			Group g1 = new Group(g, Y1);
			g0.update(id, X, false);
			g1.update(id, X, true);
			newGroups.add(g0);
			newGroups.add(g1);
		}
		return true;
	}

	/**
	 * Output current algorithm status
	 */
	void outputStatus() {
		int numDone = 0, numScanning = 0, numQueries = 0;
		for (Group g : groups) {
			if (g.done) numDone++;
			else if (g.scanning) numScanning++;
		}

		EX.putOutput("numScenarios", numScenarios);
		EX.putOutput("numDoneGroups", numDone);
		EX.putOutput("numScanGroups", numScanning);
		EX.putOutput("numGroups", groups.size());

		// Print groups
		EX.logs("%s groups", groups.size());
		int sumComplexity = 0;
		Abstraction[] X = new Abstraction[aSize];
		int trackItr = 0;
		for (Group g : groups) {
			EX.logs("  %s", g);
			sumComplexity += complexity(g.upperX);
			numQueries += g.Y.size();
			if(trackItr == 0) {
				for (int c = 0; c < aSize; c++)
					X[c] = g.upperX[c];
			}else{
				for (int c = 0; c < aSize; c++)
					X[c] = X[c].getLevel() > g.upperX[c].getLevel()?X[c]:g.upperX[c];
			}
			trackItr++;
		}
		EX.putOutput("totalQueries", numQueries);
		EX.putOutput("sumComplexity", sumComplexity);
		EX.putOutput("complexity", complexity(X));

		EX.flushOutput();
	}

	/**
	 * Save groups information to disk in raw as well as XML format. If xmlToHtmlTask is not null, also generate HTML format file
	 */
	public void saveState() {
		{
			PrintWriter out = Utils.openOut(EX.path("groups"));
			for (Group g : groups)
				out.println(g.scanning + "##" + logistic(g.incrTheta) +
					"##" + Abstraction.concatAbstractions(encodeX(g.lowerX),sepMin) +
					"##" + Abstraction.concatAbstractions(encodeX(g.upperX),sepMin) +
					"##" + Query.concatQueries(g.Y.toArray(new String[0]), sepMin));
			out.close();
		}
		{
			PrintWriter out = Utils.openOut(EX.path("groups.xml"));
			out.println("<groups>");
			for (Group g : groups) {
				String groupTag = "<group ";
				groupTag += "Xlower=\"" + complexity(g.lowerX) + "\"" + " Xupper=\"" +  complexity(g.upperX) + "\""; 
				groupTag += " Y=\"" + g.Y.size() + "\"" + " Prob=\"" + logistic(g.incrTheta) + "\"";
				groupTag += " Mids=\"";

				int flag = 0;
				for (int c = 0; c < aSize; c++) {
					if (g.upperX[c].getLevel() != botX[c].getLevel()) {
						if(flag==1)
							groupTag += " ";
						groupTag += g.upperX[c].encodeForXML();
						flag = 1;
					}
				}

				groupTag += "\">";

				out.println(groupTag);

				for (String y : g.Y) {
					Query q = qFactory.create(y);
					out.println("<query " + q.encodeForXML() + "/>");
				}
				out.println("</group>");

			}
			out.println("</groups>");
			out.close();

			out.flush();

			if(this.xmlToHtmlTask != null) {
				ClassicProject.g().resetTaskDone(this.xmlToHtmlTask);
				ClassicProject.g().runTask(this.xmlToHtmlTask);
			}

			out.close();
		}
	}

	protected void saveGroupState(Group g, String fileName) {
		PrintWriter out = Utils.openOutAppend(EX.path(fileName));
		out.println("=== "+g);
		out.println("Abstractions:");
		for (int c = 0; c < aSize; c++)
			if (g.upperX[c].getLevel() != botX[c].getLevel())
				out.println("  "+g.upperX[c].encode() + "##" + g.upperX[c].toString());
		out.println("Queries:");
		for (String y : g.Y) {
			Query q = qFactory.create(y);
			out.println("  "+ y + "##" + q.toString());
		}
		out.close();
	}

	protected void saveQueries(Collection<String> Y, String fileName) {
		PrintWriter out = Utils.openOutAppend(EX.path(fileName));
		out.println("Queries:");
		out.println("Num:" + Y.size());

		for (String y : Y) {
			Query q = qFactory.create(y);
			out.println("  "+ y + "##" + q.toString());
		}
		out.close();
	}

	@Override
	public int maxWorkersNeeded() {
		// If everyone's scanning, just need one per scan
		// Otherwise, need as many workers as we can get.
		int n = 0;
		for (Group g : groups) {
			if (g.done) continue;
			if (g.scanning) n++;
			else return 10000; // Need a lot
		}
		return n;
	}

	// General utilities	
	String[] encodeX(Abstraction[] X) {
		String[] enc = new String[X.length];
		for (int c = 0; c < aSize; c++) {
			enc[c] = new String(X[c].encode());
		}
		return enc;
	}

	Abstraction[] decodeX(String[] x) {
		Abstraction[] X = new Abstraction[aSize];
		int c = 0;
		for (String s : x) {
			X[c] = this.aFactory.create(s);
			c++;				
		}
		return X;
	}

	int sample(double[] weights) {
		double sumWeight = 0;
		for (double w : weights) sumWeight += w;
		double target = random.nextDouble() * sumWeight;
		double accum = 0;
		for (int i = 0; i < weights.length; i++) {
			accum += weights[i];
			if (accum >= target) return i;
		}
		throw new RuntimeException("Bad");
	}
	
	int complexity(Abstraction[] X) {
		int sum = 0;
		for (int c = 0; c < aSize; c++) {
			assert X[c].getLevel() >= botX[c].getLevel() : c + " " + X[c].getLevel() + " " + botX[c].getLevel();
			sum += X[c].getLevel() - botX[c].getLevel();
		}
		return sum;
	}
	Abstraction[] copy(Abstraction[] X) {
		Abstraction[] newX = new Abstraction[aSize];
		for (int c = 0; c < aSize; c++) {
			newX[c] = X[c].copy();
		}
		return newX;
	}
	void set(Abstraction[] X1, Abstraction[] X2) { 
		for (int c = 0; c < aSize; c++) {
			X1[c].copy(X2[c]);
		} 
	}
	boolean eq(Abstraction[] X1, Abstraction[] X2) {
		for (int c = 0; c < aSize; c++)
			if (X1[c].getLevel() != X2[c].getLevel()) return false;
		return true;
	}
	boolean lessThanEq(Abstraction[] X1, Abstraction[] X2) {
		for (int c = 0; c < aSize; c++)
			if (X1[c].getLevel() > X2[c].getLevel()) return false;
		return true;
	}
	int findUniqueDiff(Abstraction[] X1, Abstraction[] X2) {
		int diffc = -1;
		for (int c = 0; c < aSize; c++) {
			int d = Math.abs(X1[c].getLevel()-X2[c].getLevel());
			if (d > 1) return -1; // Not allowed
			if (d == 1) {
				if (diffc != -1) return -1; // Can't have two diff
				diffc = c;
			}
		}
		return diffc;
	}

	double logistic(double theta) { return 1/(1+Math.exp(-theta)); }
	double invLogistic(double mu) { return Math.log(mu/(1-mu)); }

	/**
	 * Group abstraction is used to cluster, together, all queries that share the same lower and upper bound for minimal abstractions
	 *
	 */
	class Group {
		boolean done;
		boolean scanning;
		Abstraction[] lowerX;
		Abstraction[] upperX;
		HashSet<String> Y; // Unproven 
		double incrTheta; // For the step size
		HashMap<Integer,Integer> jobCounts; // job ID -> number of jobs in the queue at the time when this job was created

		boolean inRange(Abstraction[] X) { return lessThanEq(lowerX, X) && lessThanEq(X, upperX); }

		@Override public String toString() {
			String status = done ? "done" : (scanning ? "scan" : "rand");
			return String.format("Group(%s,%s<=|X|<=%s,|Y|=%s,incrProb=%.2f,#wait=%s)",
					status, complexity(lowerX), complexity(upperX), Y.size(), logistic(incrTheta), jobCounts.size());
		}

		public Group(boolean isScan, HashSet<String> Y) {
			this.done = false;
			this.scanning = isScan;
			this.lowerX = copy(botX);
			this.upperX = copy(topX);
			this.Y = Y;
			this.incrTheta = invLogistic(initIncrProb);
			this.jobCounts = new HashMap();
		}

		public Group(Group g, HashSet<String> Y) {
			this.done = g.done;
			this.scanning = g.scanning;
			this.lowerX = copy(g.lowerX);
			this.upperX = copy(g.upperX);
			this.Y = Y;
			this.incrTheta = g.incrTheta;
			this.jobCounts = new HashMap(g.jobCounts);
		}

		boolean wantToLaunchJob() {
			if (done) return false;
			if (scanning) return jobCounts.size() == 0; // Don't parallelize
			return true;
		}

		/**
		 * Generates a new job to be provided to a worker in the parallelizer framework
		 */
		Scenario createNewScenario() {
			if (switchToRealCase) {
				EX.logs("createNewScenarioForRealCase %s: ", this);
				Scenario realScenario = new Scenario(ScenarioKind.SWITCHTOREAL.toString(),
					Abstraction.concatAbstractions(encodeX(this.upperX), sepMin),
					Query.concatQueries(this.Y.toArray(new String[0]), sepMin), sepMaj);
				jobCounts.put(realScenario.getID(), 1+jobCounts.size());
				return realScenario;
			} else {
				double incrProb = logistic(incrTheta);
				EX.logs("createNewScenario %s: incrProb=%.2f", this, incrProb);
				if (scanning) {
					if (jobCounts.size() == 0) { // This is sequential - don't waste energy parallelizing
						int diff = complexity(upperX) - complexity(lowerX);
						assert diff > 0 : diff;
						int target_j = random.nextInt(diff);
						EX.logs("Scanning: dipping target_j=%s of diff=%s", target_j, diff);
						// Sample a minimal dip from upperX
						int j = 0;
						Abstraction[] X = new Abstraction[aSize];
						for (int c = 0; c < aSize; c++) {
							X[c] = lowerX[c].copy();
							for (int i = lowerX[c].getLevel(); i < upperX[c].getLevel(); i++, j++)
								if (j != target_j) X[c].refine();
						}
						return createScenario(X);
					} else {
						EX.logs("Scanning: not starting new job, still waiting for %s (shouldn't happen)", jobCounts.keySet());
						return null;
					}
				} else {
					// Sample a random element between the upper and lower bounds
					Abstraction[] X = new Abstraction[aSize];
					for (int c = 0; c < aSize; c++) {
						X[c] = lowerX[c].copy();
						for (int i = lowerX[c].getLevel(); i < upperX[c].getLevel(); i++)
							if (random.nextDouble() < incrProb) X[c].refine();
					}
					if (!eq(X, lowerX) && !eq(X, upperX)) // Don't waste time
						return createScenario(X);
					else
						return null;
				}
			}
		}

		Scenario createScenario(Abstraction[] X) {
			// Always include all the queries, otherwise, it's unclear what the reference set is
			Scenario scenario = new Scenario(ScenarioKind.COARSEN.toString(),
				Abstraction.concatAbstractions(encodeX(X),sepMin),
				Query.concatQueries(allY().toArray(new String[0]),sepMin), sepMaj);
			jobCounts.put(scenario.getID(), 1+jobCounts.size());
			return scenario;
		}

		/*
		 * Update group status based on incoming scenario information
		 */
		void update(int id, Abstraction[] X, boolean unproven) {
			if (done) {
				if (jobCounts.containsKey(id)) {
					jobCounts.remove(id);
				}
				return;
			}

			if(switchToRealCase){
				jobCounts.remove(id);
				updateStatus();
				return;
			}

			// Update refinement probability to make p(y=1) reasonable
			// Only update probability if we were responsible for launching this run
			// This is important in the initial iterations when getting data for updateLower to avoid polarization of probabilities.
			if (jobCounts.containsKey(id)) {
				double oldIncrProb = logistic(incrTheta);
				double singleTargetProb = Math.exp(-1); // Desired p(y=1)

				// Exploit parallelism: idea is that probability that two of the number of processors getting y=1 should be approximately p(y=1)
				double numProcessors = jobCounts.size(); // Approximate number of processors (for this group) with the number of things in the queue.
				double targetProb = 1 - Math.pow(1-singleTargetProb, 1.0/Math.sqrt(numProcessors+1)); // HACK

				// Due to parallelism, we want to temper the amount of probability increment
				double stepSize = incrThetaStepSize; // Simplify
				if (!unproven) incrTheta -= (1-targetProb) * stepSize; // Proven -> cheaper abstractions
				else incrTheta += targetProb * stepSize; // Unproven -> more expensive abstractions

				EX.logs("    targetProb = %.2f (%.2f eff. proc), stepSize = %.2f/sqrt(%d) = %.2f, incrProb : %.2f -> %.2f [unproven=%s]",
						targetProb, numProcessors,
						incrThetaStepSize, jobCounts.get(id), stepSize,
						oldIncrProb, logistic(incrTheta), unproven);
				jobCounts.remove(id);
			}

			// Detect minimal dip: negative scenario that differs by upperX by one site (that site must be necessary)
			// This should only really be done in the scanning part
			if (unproven) {
				int c = findUniqueDiff(X, upperX);
				if (c != -1) {
					EX.logs("    updateLowerX %s: found that c=%s is necessary", this, upperX[c].encode());
					lowerX[c] = upperX[c];
				}
			}
			else { // Proven
				EX.logs("    updateUpperX %s: reduced |upperX|=%s to |upperX|=%s", this, complexity(upperX), complexity(X));
				set(upperX, X);
			}

			updateStatus();
		}

		void updateStatus() {
			if (scanning) {
				if (eq(lowerX, upperX)) {
					EX.logs("    DONE with group %s!", this);
					done = true;
				}
			} else {
				int lowerComplexity = complexity(lowerX);
				int upperComplexity = complexity(upperX);
				int diff = upperComplexity-lowerComplexity;

				EX.logs("Prob:" + logistic(incrTheta));
				if (upperComplexity == 1) { // Can't do better than 1
					EX.logs("    DONE with group %s!", this);
					done = true;
				}
				else if (diff <= scanThreshold) {
					EX.logs("    SCAN group %s now!", this);
					scanning = true;
				}
				else if(logistic(incrTheta) >= 0.999) { // Heuristic: At this point almost all configurations have been tried for current
														// lower & upper bounds. Consequently, the set of queries in this group, 
														// most likely requires the abstractions defined by upperX
					EX.logs("    Incr Prob = 1.DONE with group %s!", this);
					done = true;
				}
			}	
		}
	}
}
