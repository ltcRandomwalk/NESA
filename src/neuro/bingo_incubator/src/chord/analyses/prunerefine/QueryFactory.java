package chord.analyses.prunerefine;

public interface QueryFactory {
	Query create(Object[] tuple);
	Query create(String[] tuple);
}
