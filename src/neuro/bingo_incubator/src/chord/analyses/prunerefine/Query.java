package chord.analyses.prunerefine;

import chord.project.analyses.ProgramRel;

public abstract class Query implements Comparable<Query> {

	public abstract void addToRel(ProgramRel rel);
	public abstract String encode();
}
