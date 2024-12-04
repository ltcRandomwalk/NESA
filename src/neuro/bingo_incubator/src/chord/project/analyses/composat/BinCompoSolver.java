package chord.project.analyses.composat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1. Partition strategy: binary
 * 2. Combination strategy: CEGAR
 * @author xin
 *
 */
public class BinCompoSolver implements Solver {
	private Partitioner partitioner;
	//The solver used to solver each subproblem. This also allows us to chain solvers together.
	private Solver solver;

	public BinCompoSolver(Partitioner partitioner, Solver solver){
		this.partitioner = partitioner;
		this.solver = solver;
	}
	
	@Override
	public Set<Integer> solve(Problem p, int depth) {
		if(SatConfig.DEBUG){
			System.out.println("Binary compositional MaxSAT solver is solving a problem with "
					+p.getNumVars()+" variables and "+p.getNumConstrs()+" constraints.");
			System.out.println("The partitioner used is "+this.partitioner.toString());
		}
		if(p.getConstraints().size() == 0)
			return new HashSet<Integer>();
		List<Problem> subproblems = this.partitioner.parition(p);
		if(subproblems.size() != 2)
			throw new RuntimeException("Only binary partition is accepted.");
		Problem p1 = subproblems.get(0);
		Problem p2 = subproblems.get(1);
		int numIter = 0;
		StringBuffer sb = new StringBuffer();
		for(int i = 0 ; i < depth; i++)
			sb.append("===");
		sb.append(depth);
		String seps = sb.toString();
		while(true){
			if(SatConfig.DEBUG){
				System.out.println(seps+"Iteration "+numIter+seps);
				System.out.println("P1 num of vars: "+p1.getNumVars()+", num of constraints: "+p1.getNumConstrs());
				System.out.println("P2 num of vars: "+p2.getNumVars()+", num of constraints: "+p2.getNumConstrs());
			}
			//Step 1, solve each subproblem separately
			Set<Integer> ps1 = solver.solve(p1, depth+1);
			if( ps1 == null)
				return null;
			Set<Integer> ns1 = new HashSet<Integer>(p1.getAtomDomain());
			ns1.removeAll(ps1);
			Set<Integer> ps2 = solver.solve(p2, depth+1);
			if(ps2 == null)
				return null;
			Set<Integer> ns2 = new HashSet<Integer>(p2.getAtomDomain());
			ns2.removeAll(ps2);

			if(p1.equals(p) || ps1 == null){//p1 is the same as p or p1 is UNSAT
				if(SatConfig.DEBUG)
					System.out.println("Find the solution by solving p1");
				return ps1;
			}

			if(p2.equals(p) || ps2 == null){//p1 is the same as p or p2 is UNSAT
				if(SatConfig.DEBUG)
					System.out.println("Find the solution by solving p2");
				return ps2;
			}
			
			double o1 = p1.evaluate(ps1);
			double o2 = p2.evaluate(ps2);
			System.out.println("o1: "+o1+", o2:"+o2);
			
			//Step 2, solve each subproblem under the constraint of the solution of the other problem
			Problem p11 = new Problem();
			for(Constraint c : p1.getConstraints())
				p11.addConstraint(c);
			for(Integer l : ps2)
				p11.addConstraint(new Constraint(l));
			for(Integer l : ns2)
				p11.addConstraint(new Constraint(-l));
			
			Problem p21 = new Problem();
			for(Constraint c : p2.getConstraints())
				p21.addConstraint(c);
			for(Integer l : ps1)
				p21.addConstraint(new Constraint(l));
			for(Integer l : ns1)
				p21.addConstraint(new Constraint(-l));
			
			Set<Integer> ps11 = solver.solve(p11, depth+1);
			if(ps11 == null)
				ps11 = ps2;
			Set<Integer> ns11 = new HashSet<Integer>(p11.getAtomDomain());
			ns11.removeAll(ps11);
			Set<Integer> ps21 = solver.solve(p21, depth+1);
			if(ps21 == null)
				ps21 = ps1;
			Set<Integer> ns21 = new HashSet<Integer>(p21.getAtomDomain());
			ns21.removeAll(ps21);

			double o11 = p1.evaluate(ps11);
			double o21 = p2.evaluate(ps21);
			System.out.println("o11: "+o11+", o21:"+o21);
			
			if(o11 == o1){
				if(SatConfig.DEBUG)
					System.out.println("Find the solution by solving p1+p2 solution");
				return ps11;
			}

			if(o21 == o2){
				if(SatConfig.DEBUG)
					System.out.println("Find the solution by solving p2+p1 solution");
				return ps21;
			}

			for(Constraint c : p2.checkViolation(ps21, ns21))
				p1.addConstraint(c);
			for(Constraint c : p1.checkViolation(ps11, ns11))
				p2.addConstraint(c);

			p1.resetAtomDomain();
			p2.resetAtomDomain();
			
			numIter++;
		}
	}

}
