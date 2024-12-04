package chord.project.analyses.composat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProvenanceBinaryPartitioner implements Partitioner {
	private Set<Integer> seeds;

	public ProvenanceBinaryPartitioner(Set<Integer> seeds) {
		super();
		this.seeds = seeds;
	}

	@Override
	public List<Problem> parition(Problem p) {
		List<Problem> ret = new ArrayList<Problem>();
		Problem p1 = new Problem();
		Problem p2 = new Problem();
		ret.add(p1);
		ret.add(p2);
		Map<Integer,Set<Constraint>> provMap = new HashMap<Integer,Set<Constraint>>();
		
		//Step 1, build the provenance information map
		for(Constraint c : p.getConstraints()){
			for(Integer l : c.getLiterals()){
				if( l > 0){
					Set<Constraint> provSet = provMap.get(l);
					if(provSet == null){
						provSet = new HashSet<Constraint>();
						provMap.put(l, provSet);
					}
					provSet.add(c);
				}
			}
		}
		
		//Step 2, begin partitioning the problem
		Set<Integer> visitedSet = new HashSet<Integer>();
		List<Integer> workList = new ArrayList<Integer>(seeds);
		while(!workList.isEmpty()){
			int cur = workList.get(workList.size()-1);
			visitedSet.add(cur);
			workList.remove(workList.size()-1);
			Set<Constraint> provs = provMap.get(cur);
			if(provs != null){
				for(Constraint con : provs){
					p1.addConstraint(con);
					for(Integer l : con.getLiterals()){
						if(l < 0 && !visitedSet.contains(-l))
							workList.add(-l);
					}
				}
			}
		}
		
		for(Constraint c : p.getConstraints()){
			Set<Integer> lits = c.getLiterals();
			if(lits.size() == 1 && seeds.contains(Math.abs(lits.iterator().next())))
				p1.addConstraint(c);
			else
				if(!p1.getConstraints().contains(c))
					p2.addConstraint(c);
		}
		
		return ret;
	}

	@Override
	public String toString() {
		return "A binary parition which separates the constraints which are provenances of certain variables."
				+ "The contraints should be mostly in forms of Horn clauses";
	}	

}
