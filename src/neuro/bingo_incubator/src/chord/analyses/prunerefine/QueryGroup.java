package chord.analyses.prunerefine;


import java.util.List;
import java.util.Set;

// Group of queries which have the same abstraction and be have the same as far as we can tell.
public abstract class QueryGroup {
	
	public abstract Set<Query> getQueries();

	// Invariant: abs + prunedAbs is a full abstraction and gets same results as abs
	public abstract AbstractionSet getAbs(); // Current abstraction for this query
	public abstract AbstractionSet getPrunedAbs(); // This abstraction keeps all the slivers that have been pruned

	public abstract void runAnalysis(boolean runRelevantAnalysis);


	public abstract void pruneAbstraction();
	
	// Remove queries that have been proven
	public abstract QueryGroup removeProvenQueries();

	public abstract void inspectAnalysisOutput();
	
	public abstract void refineAbstraction();
	
	public abstract void backupRelations(int iter);
	
	public abstract void initializeAbstractionSet();
	
	public abstract void verifyAfterPrune();
	
	public abstract void prePruneAbstraction();
	
}
