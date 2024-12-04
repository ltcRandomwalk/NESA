package chord.analyses.prunerefine;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.net.Socket;
import java.net.ServerSocket;


import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Class;
import joeq.Class.jq_Type;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import chord.util.Execution;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alias.CtxtsAnalysis;
import chord.bddbddb.Rel.PairIterable;
import chord.bddbddb.Rel.TrioIterable;
import chord.bddbddb.Rel.QuadIterable;
import chord.bddbddb.Rel.PentIterable;
import chord.bddbddb.Rel.HextIterable;
import chord.bddbddb.Rel.RelView;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.heapacc.DomE;
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Messages;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.util.IndexMap;
import chord.util.ArraySet;
import chord.util.graph.IGraph;
import chord.util.graph.MutableGraph;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import chord.util.Utils;
import chord.util.StopWatch;

public class PruneRefine{
	private Execution X;

	// Options
	int verbose;
	int maxIters;

	List<Query> allQueries;
	List<Status> statuses = new ArrayList<Status>(); // Status of the analysis over iterations of refinement
	QueryGroup unprovenGroup;
	List<QueryGroup> provenGroups = new ArrayList<QueryGroup>();

	public PruneRefine(List<Query> allQueries, QueryGroup unprovenGroup){
		this.allQueries = allQueries;
		this.unprovenGroup = unprovenGroup;
	}

	////////////////////////////////////////////////////////////

	// Initialization to do anything.
	private void init() {
		X = new Execution("prunerefine");

		this.verbose                 = X.getIntArg("verbose", 0);
		this.maxIters                = X.getIntArg("maxIters", 1);

		X.flushOptions();

		int maxQueries = X.getIntArg("maxQueries", allQueries.size());

		int seed = X.getIntArg("randQuery", -1);
		if (seed != -1) {
			X.logs("Using %s/%s random queries (seed %s)", maxQueries, allQueries.size(), seed);
			Random rand = new Random(seed);
			List<Query> queries = new ArrayList<Query>();
			int[] perm = Utils.samplePermutation(rand, allQueries.size()); 
			for (int i = 0; i < maxQueries; i++)
				queries.add(allQueries.get(perm[i]));
			allQueries = queries;
		}
		else {
			List<Query> queries = new ArrayList<Query>();
			for (int i = 0; i < maxQueries; i++)
				queries.add(allQueries.get(i));
			allQueries = queries;
		}

		sortQueries(allQueries);
		X.logs("Starting with %s total queries", allQueries.size());
		outputQueries(allQueries, "initial.queries");

		X.flushOptions();
	}

	void finish() { X.finish(null); }

	public void runPruneRefine() {
		init();
		refinePruneLoop();
		finish();
	}

	void refinePruneLoop() {
		unprovenGroup.initializeAbstractionSet();

		X.logs("Unproven group with %s queries", allQueries.size());
		unprovenGroup.getQueries().addAll(allQueries);

		for (int iter = 1; ; iter++) {
			X.logs("====== Iteration %s", iter);
			boolean runRelevantAnalysis = iter < maxIters;
			unprovenGroup.runAnalysis(runRelevantAnalysis);
			unprovenGroup.backupRelations(iter);

			unprovenGroup.inspectAnalysisOutput();

			QueryGroup provenGroup = unprovenGroup.removeProvenQueries();
			if (provenGroup != null) provenGroups.add(provenGroup);

			if (runRelevantAnalysis) {
				unprovenGroup.pruneAbstraction();
				//	if(provenGroup!=null)
				//		provenGroup.verifyAfterPrune();
			}

			outputStatus(iter);

			if (statuses.get(statuses.size()-1).numUnproven == 0) {
				X.logs("Proven all queries, exiting...");
				X.putOutput("conclusion", "prove");
				break;
			}
			if (iter == maxIters) {
				X.logs("Reached maximum number of iterations, exiting...");
				X.putOutput("conclusion", "max");
				break;
			}
			if (converged()) {
				X.logs("Refinement converged, exiting...");
				X.putOutput("conclusion", "conv");
				break;
			}

			refineAbstraction();
		}
	}

	void refineAbstraction() {
		unprovenGroup.refineAbstraction();

		unprovenGroup.prePruneAbstraction();
	}

	int numUnproven() {
		int n = 0;
		for (QueryGroup g : provenGroups)
			n += g.getQueries().size();
		return allQueries.size()-n;
	}

	boolean converged() {
		if (statuses.size() < 2) return false;
		Status a = statuses.get(statuses.size()-2);
		Status b = statuses.get(statuses.size()-1);
		return a.absHashCode == b.absHashCode;
	}

	void outputStatus(int iter) {
		X.logs("outputStatus(iter=%s)", iter);

		X.addSaveFiles("abstraction.S."+iter);
		{
			PrintWriter out = OutDirUtils.newPrintWriter("abstraction.S."+iter);
			for (Object a : unprovenGroup.getAbs().getSortedAbstractions())
				out.println(unprovenGroup.getAbs().printAbstraction(a));
			out.close();
		}
		X.addSaveFiles("unproven.queries."+iter);
		outputQueries(sortQueries(new ArrayList<Query>(unprovenGroup.getQueries())), "unproven.queries."+iter);

		int numUnproven = numUnproven();
		Status status = new Status();
		status.numUnproven = numUnproven;
		status.runAbsSize = Status.lastRunAbsSize; // Before pruning (real measure of complexity)
		status.absSize = unprovenGroup.getAbs().size(); // After pruning
		status.absHashCode = unprovenGroup.getAbs().hashCode();
		status.absSummary = unprovenGroup.getAbs().toString();
		status.clientTime = Status.lastClientTime;
		status.relevantTime = Status.lastRelevantTime;
		statuses.add(status);

		X.logs(unprovenGroup.getAbs().printStatus());

		X.putOutput("currIter", iter);
		X.putOutput("maxRunAbsSize", Status.maxRunAbsSize);
		X.putOutput("lastRunAbsSize", Status.lastRunAbsSize);
		X.putOutput("numQueries", allQueries.size());
		X.putOutput("numProven", allQueries.size()-numUnproven);
		X.putOutput("numUnproven", numUnproven);
		X.putOutput("numUnprovenHistory", getHistory("numUnproven"));
		X.putOutput("runAbsSizeHistory", getHistory("runAbsSize"));
		X.putOutput("clientTimeHistory", getHistory("clientTime"));
		X.putOutput("relevantTimeHistory", getHistory("relevantTime"));
		X.flushOutput();
	}

	String getHistory(String field) {
		StringBuilder buf = new StringBuilder();
		for (Status s : statuses) {
			if (buf.length() > 0) buf.append(',');
			Object value;
			if (field.equals("numUnproven")) value = s.numUnproven;
			else if (field.equals("runAbsSize")) value = s.runAbsSize;
			else if (field.equals("clientTime")) value = new StopWatch(s.clientTime);
			else if (field.equals("relevantTime")) value = new StopWatch(s.relevantTime);
			else throw new RuntimeException("Unknown field: " + field);
			buf.append(value);
		}
		return buf.toString();
	}

	void outputQueries(List<Query> queries, String path) {
		PrintWriter out = OutDirUtils.newPrintWriter(path);
		for (Query q : queries)
			out.println(q.encode()+" "+q);
		out.close();
	}

	List<Query> sortQueries(List<Query> queries) {
		Collections.sort(queries);
		return queries;
	}
}