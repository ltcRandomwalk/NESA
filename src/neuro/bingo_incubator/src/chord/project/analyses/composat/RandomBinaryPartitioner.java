package chord.project.analyses.composat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Randomly partition the problem into two subproblems.
 * @author xin
 *
 */
public class RandomBinaryPartitioner implements Partitioner {

	@Override
	public List<Problem> parition(Problem p) {
		List<Constraint> cons = new ArrayList<Constraint>(p.getConstraints());
		Set<Integer> consIndices1 = new HashSet<Integer>();
		while(consIndices1.size() < cons.size()/2){
			int index = (int)(Math.random()*cons.size());
			consIndices1.add(index);
		}
		Problem p1 = new Problem();
		Problem p2 = new Problem();
		for(int i = 0 ; i < cons.size() ; i++){
			if(consIndices1.contains(i))
				p1.addConstraint(cons.get(i));
			else
				p2.addConstraint(cons.get(i));
		}
		List<Problem> ret = new ArrayList<Problem>();
		ret.add(p1);
		ret.add(p2);
		return ret;
	}
	
	@Override
	public String toString(){
		return "Random binary partitioner";
	}

}
