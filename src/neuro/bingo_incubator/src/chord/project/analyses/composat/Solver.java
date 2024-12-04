package chord.project.analyses.composat;

import java.util.Set;

public interface Solver {

	/**
	 * Return the variables set TRUE or null if UNSAT
	 * @param p
	 * @return
	 */
	public Set<Integer> solve(Problem p, int depth);
	
}
