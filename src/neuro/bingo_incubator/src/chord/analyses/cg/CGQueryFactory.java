package chord.analyses.cg;

import chord.analyses.prunerefine.Query;
import chord.analyses.prunerefine.QueryFactory;

public class CGQueryFactory implements QueryFactory{

	@Override
	public Query create(Object[] tuple) {
		return new CGQuery(tuple);
	}

	@Override
	public Query create(String[] tuple) {
		return new CGQuery(tuple);
	}
	
}