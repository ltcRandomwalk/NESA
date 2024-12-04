package chord.analyses.incrsolver.sumgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.util.tuple.object.Pair;

public abstract class MaxsatSolver {
	List<Clause> hardClauses;
	List<Pair<Long,Clause>> softClauses; // all clauses with a single literal
	
	public MaxsatSolver(List<Clause> hardClauses, List<Pair<Long,Clause>> softClauses) {
		this.hardClauses = hardClauses;
		this.softClauses = softClauses;
	}
	
	public int getNumMaxSatVars(List<Clause> clauses) {
		HashSet<Integer> vars = new HashSet<Integer>();
		for (Clause c : clauses) {
			for (int l : c.ls) {
				vars.add(Math.abs(l));
			}
		}
		return vars.size();
	}
	
	public int getNumMaxSatCons() {
		return this.hardClauses.size() + this.softClauses.size();
	}
	
	public abstract Set<Integer> solveMaxSat();

}
