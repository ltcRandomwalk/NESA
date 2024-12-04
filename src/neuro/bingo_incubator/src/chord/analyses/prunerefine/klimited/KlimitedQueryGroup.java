package chord.analyses.prunerefine.klimited;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.prunerefine.AbstractionSet;
import chord.analyses.prunerefine.PruneRefine;
import chord.analyses.prunerefine.Query;
import chord.analyses.prunerefine.QueryFactory;
import chord.analyses.prunerefine.QueryGroup;
import chord.analyses.prunerefine.RelGraph;
import chord.analyses.prunerefine.Status;
import chord.bddbddb.Rel.AryNIterable;
import chord.bddbddb.Rel.PairIterable;
import chord.project.ClassicProject;
import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.util.Execution;
import chord.util.StopWatch;
import chord.util.tuple.object.Pair;

import static chord.analyses.prunerefine.klimited.GlobalInfo.G;

//Group of queries which have the same abstraction and be have the same as far as we can tell.
public class KlimitedQueryGroup extends QueryGroup {
	Execution X = Execution.v();
	
	String prefix = ""; // Just for printing out
	Set<Query> queries = new LinkedHashSet<Query>();
	// Invariant: abs + prunedAbs is a full abstraction and gets same results as abs

	KlimitedAbstractionSet abs = new KlimitedAbstractionSet(); // Current abstraction for this query
	KlimitedAbstractionSet prunedAbs = new KlimitedAbstractionSet(); // This abstraction keeps all the slivers that have been pruned

	TypeStrategy typeStrategy;
	TypeStrategy pruningTypeStrategy;
	QueryFactory qFactory;

	public KlimitedQueryGroup(TypeStrategy typeStrategy, TypeStrategy pruningTypeStrategy, QueryFactory qFactory){
		this.typeStrategy = typeStrategy;
		this.pruningTypeStrategy = pruningTypeStrategy;
		this.qFactory = qFactory;
	}


	@Override
	public void runAnalysis(boolean runRelevantAnalysis) {
		X.logs("%srunAnalysis: %s", prefix, abs);
		Status.maxRunAbsSize = Math.max(Status.maxRunAbsSize, abs.size());
		Status.lastRunAbsSize = abs.size();

		// Domain (these are the slivers)
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		domC.clear();
		assert abs.project(G.emptyCtxt, typeStrategy) != null;
		List<Ctxt> sortedC = abs.getSortedAbstractions();

		domC.add(abs.project(G.emptyCtxt, typeStrategy));
		for (Ctxt c : sortedC) domC.add(c);
		domC.save();

		// Relations
		ProgramRel CH = (ProgramRel) ClassicProject.g().getTrgt("CH");
		ProgramRel CI = (ProgramRel) ClassicProject.g().getTrgt("CI");
		ProgramRel CC = (ProgramRel) ClassicProject.g().getTrgt("CC");
		CH.zero();
		CI.zero();
		CC.zero();
		for (Ctxt c : abs.getAbstractions()) { // From sliver c...
			if (G.hasHeadSite(c)) {
				//X.logs("%s %s", G.jstr(c.head()), typeStrategy.clusters.size());
				for (Quad k : typeStrategy.lift(c.head())) // k is the actual starting site of a chain that c represents
					for (jq_Method m : G.jm.get(k))
						for (Quad j : G.mj.get(m)) // Extend with some site j that could be prepended
							addPrepending(j, c, CH, CI, CC);
			}
			else {
				for (Quad j : G.jSet) // Extend with any site j
					addPrepending(j, c, CH, CI, CC);
			}
		}
		CH.save();
		CI.save();
		CC.save();

		// Determine CFA or object-sensitivity
/*		ProgramRel relobjI = (ProgramRel) ClassicProject.g().getTrgt("objI");
		relobjI.zero();
		if (G.useObjectSensitivity) {
			for (Quad i : G.domI) relobjI.add(i);
		}
		relobjI.save();
*/		
		ProgramRel relKcfaSenM = (ProgramRel) ClassicProject.g().getTrgt("kcfaSenM");
		ProgramRel relKobjSenM = (ProgramRel) ClassicProject.g().getTrgt("kobjSenM");
		ProgramRel relCtxtCpyM = (ProgramRel) ClassicProject.g().getTrgt("ctxtCpyM");
		relKcfaSenM.zero();
		relKobjSenM.zero();
		relCtxtCpyM.zero();
		if (G.useObjectSensitivity) {
			for (jq_Method m : G.domM) {
				if (m.isStatic()) relCtxtCpyM.add(m);
				else              relKobjSenM.add(m);
			}
		}
		else {
			for (jq_Method m : G.domM) relKcfaSenM.add(m);
		}
		relKcfaSenM.save();
		relKobjSenM.save();
		relCtxtCpyM.save();

		ProgramRel relInQuery = (ProgramRel) ClassicProject.g().getTrgt(G.inQueryRel);
		relInQuery.zero();
		for (Query q : queries) q.addToRel(relInQuery);
		relInQuery.save();

		ClassicProject.g().resetTrgtDone(domC); // Make everything that depends on domC undone
		ClassicProject.g().setTaskDone("klimited-prunerefine-java"); // We are generating all this stuff, so mark it as done...
		ClassicProject.g().setTrgtDone(domC);
		ClassicProject.g().setTrgtDone(CH);
		ClassicProject.g().setTrgtDone(CI);
		ClassicProject.g().setTrgtDone(CC);
//		ClassicProject.g().setTrgtDone(relobjI);
		ClassicProject.g().setTrgtDone(relKcfaSenM);
		ClassicProject.g().setTrgtDone(relKobjSenM);
		ClassicProject.g().setTrgtDone(relCtxtCpyM);
		ClassicProject.g().setTrgtDone(relInQuery);

		StopWatch watch = new StopWatch();
		watch.start();
		assert G.tasks != null;
		for (String task : G.tasks)
			ClassicProject.g().runTask(task);
		watch.stop();
		Status.lastClientTime = watch.ms;

		if (runRelevantAnalysis  && (G.pruneCtxts || G.refineSites)) {
			watch.start();
			assert G.relevantTask != null;
			ClassicProject.g().runTask(G.relevantTask);
			watch.stop();
			Status.lastRelevantTime = watch.ms;
		}

		if (G.inspectTransRels) {
			assert G.transTask != null;
			ClassicProject.g().runTask(G.transTask);
		}
	}

	void addPrepending(Quad j, Ctxt c, ProgramRel CH, ProgramRel CI, ProgramRel CC) {
		Quad jj = typeStrategy.project(j);
		Ctxt d = abs.project(c.prepend(jj), typeStrategy);
		if (!G.pruneCtxts) assert d != null;
		if (d != null) {
			//X.logs("PREPEND %s <- %s %s", G.cstr(d), G.jstr(j), G.cstr(c));
			(G.isAlloc(j) ? CH : CI).add(d, j);
			CC.add(c, d);
		}
	}

	KlimitedAbstractionSet relevantAbs() {
		// From Datalog, read out the pruned abstraction
		KlimitedAbstractionSet relevantAbs = new KlimitedAbstractionSet(); // These are the slivers we keep
		relevantAbs.add(abs.project(G.emptyCtxt, typeStrategy)); // Always keep this, because it probably won't show up in CH or CI
		for (String relName : new String[] {"r_CH", "r_CI"}) {
			if (G.useObjectSensitivity && relName.equals("r_CI")) continue;
			ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt(relName); rel.load();
			PairIterable<Ctxt,Quad> result = rel.getAry2ValTuples();
			for (Pair<Ctxt,Quad> pair : result)
				relevantAbs.add(pair.val0);
			rel.close();
		}
		return relevantAbs;
	}

	@Override
	public void pruneAbstraction() {
		if(G.pruneCtxts){
			//assert G.pruneCtxts;
			KlimitedAbstractionSet newAbs = relevantAbs();

			// Record the pruned slivers (abs - newAbs)
			for (Ctxt c : abs.getAbstractions())
				if (!newAbs.contains(c))
					prunedAbs.add(c);

			X.logs("%sSTATUS pruneAbstraction: %s -> %s", prefix, abs, newAbs);
			abs = newAbs;
			abs.assertDisjoint();
		}
	}

	// Remove queries that have been proven
	@Override
	public QueryGroup removeProvenQueries() {
		// From Datalog, read out all unproven queries
		Set<Query> unproven = new HashSet<Query>();
		ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt(G.outQueryRel); rel.load();
		G.readQueries(rel, unproven, qFactory);
		rel.close();

		// Put all proven queries in a new group
		KlimitedQueryGroup provenGroup = new KlimitedQueryGroup(typeStrategy,pruningTypeStrategy, qFactory);
		provenGroup.abs.add(prunedAbs); // Build up complete abstraction
		provenGroup.abs.add(abs);
		assert abs.size()+prunedAbs.size() == provenGroup.abs.size(); // No duplicates
		for (Query q : queries)
			if (!unproven.contains(q))
				provenGroup.queries.add(q);
		for (Query q : provenGroup.queries)
			queries.remove(q);

		X.logs("%sSTATUS %s queries unproven", prefix, queries.size());

		return provenGroup;
	}
	
	

	@Override
	public void inspectAnalysisOutput() {
		// Display the transition graph over relations
		if (!G.inspectTransRels)
			return;
		
		try {
			RelGraph graph = new RelGraph();
			assert G.transTask != null;
			String dlogPath = ((DlogAnalysis)ClassicProject.g().getTask(G.transTask)).getFileName();
			X.logs("Reading transitions from "+dlogPath);
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(dlogPath)));
			String line;
			while ((line = in.readLine()) != null) {
				if (!line.startsWith("# TRANS")) continue;
				String[] tokens = line.split(" ");
				graph.loadTransition(tokens[2], tokens[3], tokens[4], tokens[5], parseIntArray(tokens[6]), parseIntArray(tokens[7]));
			}
			in.close();
			graph.display();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void refineAbstraction() {
		String oldAbsStr = abs.toString();
		KlimitedAbstractionSet newAbs = new KlimitedAbstractionSet();

		if (G.refineSites) {
			Set<Quad> relevantSites = relevantAbs().inducedHeadSites();
			X.logs("%s%s/%s sites relevant", prefix, relevantSites.size(), G.jSet.size());
			for (Ctxt c : abs.getAbstractions()) { // For each sliver...
				if (G.isSummary(c) && (!G.hasHeadSite(c) || relevantSites.contains(c.head()))) // If have a relevant head site (or empty)
					newAbs.addRefinements(c, 1, typeStrategy);
				else
					newAbs.add(c); // Leave atomic ones alone (already precise as possible)
			}
		}
		else {
			for (Ctxt c : abs.getAbstractions()) { // For each sliver
				if (G.isSummary(c))
					newAbs.addRefinements(c, 1, typeStrategy);
				else
					newAbs.add(c); // Leave atomic ones alone (already precise as possible)
			}
			newAbs.assertDisjoint();
		}

		abs = newAbs;
		String newAbsStr = abs.toString();

		assert !abs.getAbstractions().contains(G.summarize(G.emptyCtxt));

		X.logs("%sSTATUS refineAbstraction: %s -> %s", prefix, oldAbsStr, newAbsStr);
	}

	@Override
	public void backupRelations(int iter) {
		try {
			if (X.getBooleanArg("saveRelations", false)) {
				X.logs("backupRelations");
				String path = X.path(""+iter);
				new File(path).mkdir();

				DomC domC = (DomC) ClassicProject.g().getTrgt("C");
				domC.save(path, true);

				String[] names = new String[] { "CH", "CI", "CH", G.inQueryRel, G.outQueryRel };
				for (String name : names) {
					X.logs("  Saving relation "+name);
					ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(name);
					rel.load();
					rel.print(path);
					//rel.close(); // Crashes
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void initializeAbstractionSet(){
		X.logs("Initializing abstraction with length minH=%s,minI=%s slivers (|jSet|=%s)", G.minH, G.minI, G.jSet.size());
		// Initialize abstraction (CREATE)
		abs.add(G.initEmptyCtxt(G.minI));
		for (Quad j : G.jSet) {
			int len = G.isAlloc(j) ? G.minH : G.minI;
			if (len > 0)
				abs.addRefinements(G.summarize(G.emptyCtxt.append(typeStrategy.project(j))), len-1, typeStrategy);
		}
	}

	@Override
	public void verifyAfterPrune(){
		if (G.verifyAfterPrune) {
			assert G.pruneCtxts;
			X.logs("verifyAfterPrune");
			// Make sure this is a complete abstraction
			assert abs.inducedHeadSites().equals(G.jSet);
			runAnalysis(true);
		}
	}

	@Override
	public void prePruneAbstraction(){
		if (pruningTypeStrategy == null || !G.pruneCtxts){
			X.logs("Prepruning disabled");
			return;
		}

		X.logs("==== Using type strategy %s to prune", pruningTypeStrategy);
		// Use pruningTypeStrategy to project unprovenGroup.abs onto a helper abstraction
		KlimitedQueryGroup helperGroup = new KlimitedQueryGroup(pruningTypeStrategy, null, qFactory);
		helperGroup.prefix = "  helper: ";
		helperGroup.queries.addAll(queries);
		assert typeStrategy.isIdentity();
		for (Ctxt c : abs.getAbstractions()) {
			Ctxt cc = pruningTypeStrategy.project(c);
			//X.logs("HELPER %s -> %s", G.cstr(c), G.cstr(cc));
			helperGroup.abs.add(cc);
		}
		helperGroup.abs.assertDisjoint();
		X.logs("  projected original %s to helper %s", abs, helperGroup.abs);

		// Run the analysis using the helper abstraction
		helperGroup.runAnalysis(true);
		helperGroup.removeProvenQueries(); // See how many we can prove
		helperGroup.pruneAbstraction();

		// Refine original and use helper abstraction to prune
		KlimitedAbstractionSet prunedAbs = new KlimitedAbstractionSet();
		for (Ctxt c : abs.getAbstractions())
			if (helperGroup.abs.contains(pruningTypeStrategy.project(c)))
				prunedAbs.add(c);
		X.logs("  helper pruned original from %s to %s", abs, prunedAbs);
		abs = prunedAbs;
	}

	int[] parseIntArray(String s) {
		String[] tokens = s.split(",");
		int[] l = new int[tokens.length];
		for (int i = 0; i < l.length; i++)
			l[i] = Integer.parseInt(tokens[i]);
		return l;
	}

	@Override
	public Set<Query> getQueries() {
		return queries;
	}

	@Override
	public AbstractionSet getAbs() {
		return abs;
	}

	@Override
	public AbstractionSet getPrunedAbs() {
		return prunedAbs;
	}
}