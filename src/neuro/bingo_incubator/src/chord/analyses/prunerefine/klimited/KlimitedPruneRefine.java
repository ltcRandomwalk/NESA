package chord.analyses.prunerefine.klimited;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.DomC;
import chord.analyses.prunerefine.Histogram;
import chord.analyses.prunerefine.PruneRefine;
import chord.analyses.prunerefine.Query;
import chord.analyses.prunerefine.QueryFactory;
import chord.bddbddb.Rel.PairIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

import static chord.analyses.prunerefine.klimited.GlobalInfo.G;

@Chord(
		name = "klimited-prunerefine-java"/*,
		produces = { "C", "CC", "CH", "CI", "kcfaSenM", "kobjSenM", "ctxtCpyM" },
		namesOfTypes = { "C" },
		types = { DomC.class }*/
		)
public class KlimitedPruneRefine extends JavaAnalysis{
	private Execution X;

	// Options
	boolean useObjectSensitivity;
	boolean verifyAfterPrune;
	boolean pruneCtxts, refineSites;
	String queryFactoryClass;
	boolean disallowRepeats;
	TypeStrategy typeStrategy; // Types instead of allocation sites
	TypeStrategy pruningTypeStrategy; // Use this to prune
	KlimitedQueryGroup unprovenGroup;
	PruneRefine pruneRef;
	QueryFactory qFactory;
	String inQueryRel, outQueryRel, queryRel;
	boolean inspectTransRels;

	int minH, maxH, minI, maxI;

	List<String> initTasks = new ArrayList<String>();
	List<String> tasks = new ArrayList<String>();
	String relevantTask;
	String transTask;


	// Compute once using 0-CFA
	Set<Quad> hSet = new HashSet<Quad>();
	Set<Quad> iSet = new HashSet<Quad>();
	Set<Quad> jSet = new HashSet<Quad>(); // hSet union iSet
	HashMap<jq_Method,List<Quad>> rev_jm = new HashMap<jq_Method,List<Quad>>(); // method m -> sites that be the prefix of a context for m
	HashMap<Quad,List<jq_Method>> jm = new HashMap<Quad,List<jq_Method>>(); // site to methods
	HashMap<jq_Method,List<Quad>> mj = new HashMap<jq_Method,List<Quad>>(); // method to sites

	List<Query> allQueries = new ArrayList<Query>();


	////////////////////////////////////////////////////////////

	// Initialization to do anything.
	private void init() {
		X = new Execution(getName());

		G = new GlobalInfo();
		G.jm = jm;
		G.mj = mj;
		G.rev_jm = rev_jm;
		G.hSet = hSet;
		G.iSet = iSet;
		G.jSet = jSet;

		this.useObjectSensitivity    = X.getBooleanArg("useObjectSensitivity", false);
		this.verifyAfterPrune        = X.getBooleanArg("verifyAfterPrune", false);
		this.pruneCtxts              = X.getBooleanArg("pruneCtxts", false);
		this.refineSites             = X.getBooleanArg("refineSites", false);
		this.queryFactoryClass       = X.getStringArg("queryFactoryClass", null);
		this.disallowRepeats         = X.getBooleanArg("disallowRepeats", false);
		this.inQueryRel      		 = X.getStringArg("inQueryRel", null);
		this.outQueryRel       		 = X.getStringArg("outQueryRel", null);
		this.queryRel       		 = X.getStringArg("queryRel", null);
		this.inspectTransRels        = X.getBooleanArg("inspectTransRels", false);
		this.typeStrategy            = new TypeStrategy(X.getStringArg("typeStrategy", "identity"), disallowRepeats);

		if (X.getStringArg("pruningTypeStrategy", null) != null)
			this.pruningTypeStrategy   = new TypeStrategy(X.getStringArg("pruningTypeStrategy", null), disallowRepeats);


		this.minH = X.getIntArg("minH", 1);
		this.maxH = X.getIntArg("maxH", 2);
		this.minI = X.getIntArg("minI", 1);
		this.maxI = X.getIntArg("maxI", 2);

		this.initTasks.add("klimited-prunerefine-init-dlog");
		for (String name : X.getStringArg("initTaskNames", "").split(","))
			this.initTasks.add(name);
		for (String name : X.getStringArg("taskNames", "").split(","))
			this.tasks.add(name);
		this.relevantTask = X.getStringArg("relevantTaskName", null);
		this.transTask = X.getStringArg("transTaskName", null);

		X.flushOptions();

		G.useObjectSensitivity = useObjectSensitivity;
		G.verifyAfterPrune = verifyAfterPrune;
		G.pruneCtxts = pruneCtxts;
		G.refineSites = refineSites;
		G.inspectTransRels = inspectTransRels;
		G.minH = minH;
		G.maxH = maxH;
		G.minI = minI;
		G.maxI = maxI;
		G.inQueryRel = inQueryRel;
		G.outQueryRel = outQueryRel;
		G.queryRel = queryRel;
		G.initTasks = initTasks;
		G.tasks = tasks;
		G.relevantTask = relevantTask;
		G.transTask = transTask;



		// Initialization Datalog programs
		for (String task : initTasks)
			ClassicProject.g().runTask(task);



		// Reachable things
		{
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("reachableH"); rel.load();
			Iterable<Quad> result = rel.getAry1ValTuples();
			for (Quad h : result) hSet.add(h);
			rel.close();
		}
		{
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("reachableI"); rel.load();
			Iterable<Quad> result = rel.getAry1ValTuples();
			for (Quad i : result) iSet.add(i);
			rel.close();
		}
		X.logs("Finished 0-CFA: |hSet| = %s, |iSet| = %s", hSet.size(), iSet.size());
		if (useObjectSensitivity) iSet.clear(); // Don't need call sites
		jSet.addAll(hSet);
		jSet.addAll(iSet);

		// Allocate memory
		for (Quad h : hSet) jm.put(h, new ArrayList<jq_Method>());
		for (Quad i : iSet) jm.put(i, new ArrayList<jq_Method>());
		for (jq_Method m : G.domM) mj.put(m, new ArrayList<Quad>());
		for (jq_Method m : G.domM) rev_jm.put(m, new ArrayList<Quad>());

		// Extensions of sites depends on the target method
		if (!useObjectSensitivity) {
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("IM"); rel.load();
			PairIterable<Quad,jq_Method> result = rel.getAry2ValTuples();
			for (Pair<Quad,jq_Method> pair : result) {
				Quad i = pair.val0;
				jq_Method m = pair.val1;
				assert iSet.contains(i) : G.istr(i);
				jm.get(i).add(m);
				rev_jm.get(m).add(i);
			}
			rel.close();
		}
		else {
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("HtoM"); rel.load();
			PairIterable<Quad,jq_Method> result = rel.getAry2ValTuples();
			for (Pair<Quad,jq_Method> pair : result) {
				Quad h = pair.val0;
				jq_Method m = pair.val1;
				assert hSet.contains(h) : G.hstr(h);
				jm.get(h).add(m);
				rev_jm.get(m).add(h);
			}
			rel.close();
		}

		// Sites contained in a method
		if (!useObjectSensitivity) {
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("MI"); rel.load();
			PairIterable<jq_Method,Quad> result = rel.getAry2ValTuples();
			for (Pair<jq_Method,Quad> pair : result) {
				jq_Method m = pair.val0;
				Quad i = pair.val1;
				mj.get(m).add(i);
			}
			rel.close();
		}
		{ // Note: both allocation and call sites need this
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt("MH"); rel.load();
			PairIterable<jq_Method,Quad> result = rel.getAry2ValTuples();
			for (Pair<jq_Method,Quad> pair : result) {
				jq_Method m = pair.val0;
				Quad h = pair.val1;
				mj.get(m).add(h);
			}
			rel.close();
		}

		// Compute statistics on prependings (for analysis) and extensions (for refinement)
		{ // prepends
			Histogram hist = new Histogram();
			for (Quad j : jSet) {
				int n = 0;
				for (jq_Method m : jm.get(j))
					n += mj.get(m).size();
				hist.add(n);
			}
			X.logs("For analysis (building CH,CI,CC): # prependings of sites: %s", hist);
		}
		{ // extensions
			Histogram hist = new Histogram();
			for (Quad j : jSet)
				hist.add(rev_jm.get(j.getMethod()).size());
			X.logs("For refinement (growing slivers): # extensions of sites: %s", hist);
		}

		// Init type strategies
		typeStrategy.init();
		if (pruningTypeStrategy != null)
			pruningTypeStrategy.init();

		Class qFactoryClass;
		try {
			qFactoryClass = Class.forName(queryFactoryClass);
			qFactory = (QueryFactory) qFactoryClass.newInstance();
		} catch (Exception e) {
			X.logs(e.getMessage());
			assert false : "Incorrect queryFactoryClassName";
		}

		unprovenGroup = new KlimitedQueryGroup(typeStrategy, pruningTypeStrategy, qFactory);


		// Compute which queries we should answer in the whole program
		String focus = X.getStringArg("focusQuery", null);
		if (focus != null) {
			//	throw new RuntimeException("Not supported");
			String[] tokens = Utils.split(focus, ":", true, true, 0);
			for(String token:tokens){
				String[] subTokens = Utils.split(token, ",", true, true, 0); 
				allQueries.add(qFactory.create(subTokens));
			}
		}
		else {
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt(G.queryRel); rel.load();
			G.readQueries(rel, allQueries, qFactory);
			rel.close();
		}
		X.flushOptions();

		pruneRef = new PruneRefine(allQueries, unprovenGroup);
	}

	void finish() { X.finish(null); }

	public void run() {
		init();
		pruneRef.runPruneRefine();
		finish();
	}

}
