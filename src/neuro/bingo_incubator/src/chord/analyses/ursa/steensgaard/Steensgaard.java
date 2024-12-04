package chord.analyses.ursa.steensgaard;

import java.util.HashSet;
import java.util.Set;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.object.Pair;

@Chord(name="steensgaard-java")
public class Steensgaard extends JavaAnalysis {

	@Override
	public void run() {
		ClassicProject.g().runTask("cipa-0cfa-dlog");
		ProgramRel relVH = (ProgramRel)ClassicProject.g().getTrgt("VH");
		relVH.load();
		Set<Pair<Integer,Integer>> vhPairs = new HashSet<Pair<Integer,Integer>>();
		for(IntPair p : relVH.getAry2IntTuples())
			vhPairs.add(new Pair<Integer,Integer>(p.idx0,p.idx1));
		
		ClassicProject.g().runTask("steensgaard-dlog");
		
		Set<Pair<Integer,Integer>> sVhPairs = new HashSet<Pair<Integer,Integer>>();
		ProgramRel relSVH = (ProgramRel)ClassicProject.g().getTrgt("sVH");
		relSVH.load();
		for(IntPair p : relSVH.getAry2IntTuples()){
			sVhPairs.add(new Pair<Integer,Integer>(p.idx0, p.idx1));
		}
		
		if(!sVhPairs.containsAll(vhPairs)){
			throw new RuntimeException("Bug in steensgaard");
		}
	}
	
}
