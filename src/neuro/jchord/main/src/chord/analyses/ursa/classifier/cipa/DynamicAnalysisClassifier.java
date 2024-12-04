package chord.analyses.ursa.classifier.cipa;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.classifier.Classifier;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.tuple.integer.IntPair;

public class DynamicAnalysisClassifier implements Classifier {
	private Set<Tuple> susTuples = null;
	private Set<Tuple> unknownTuples = null;

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(String path) {
		// TODO Auto-generated method stub

	}

	@Override
	public void load(String path) {
		// TODO Auto-generated method stub

	}

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		if(susTuples == null){
			this.susTuples = new HashSet<Tuple>();
			this.unknownTuples = new HashSet<Tuple>();
			ClassicProject.g().runTask("cipa-oracle-dynamic");
			
			ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
			vh.load();
			
			ProgramRel susVH = (ProgramRel) ClassicProject.g().getTrgt("susVH");
			susVH.load();
			
			ProgramRel unkVH = (ProgramRel) ClassicProject.g().getTrgt("unkVH");
			unkVH.load();
			
			for(int st[] : susVH.getAryNIntTuples()){
				this.susTuples.add(new Tuple(vh,st));
			}
			
			for(int ut[] : unkVH.getAryNIntTuples()){
				this.unknownTuples.add(new Tuple(vh,ut));
			}
			
			ProgramRel hfh = (ProgramRel)ClassicProject.g().getTrgt("HFH");
			hfh.load();
			
			ProgramRel susHFH = (ProgramRel)ClassicProject.g().getTrgt("susHFH");
			susHFH.load();
			
			for(int st[] : susHFH.getAryNIntTuples()){
				this.susTuples.add(new Tuple(hfh,st));
			}
			
			ProgramRel fh = (ProgramRel) ClassicProject.g().getTrgt("FH");
			fh.load();
			
			ProgramRel susFH = (ProgramRel) ClassicProject.g().getTrgt("susFH");
			susFH.load();
			
			for(int st[] : susFH.getAryNIntTuples()){
				this.susTuples.add(new Tuple(fh,st));
			}
			
			ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
			im.load();
			
			ProgramRel susIM = (ProgramRel) ClassicProject.g().getTrgt("susIM");
			susIM.load();
			
			ProgramRel unkIM = (ProgramRel) ClassicProject.g().getTrgt("unkIM");
			unkIM.load();
			
			for(int st[] : susIM.getAryNIntTuples()){
				this.susTuples.add(new Tuple(im,st));
			}
			
			for(int ut[] : unkIM.getAryNIntTuples()){
				this.unknownTuples.add(new Tuple(im,ut));
			}
		}

		if(susTuples.contains(t) && unknownTuples.contains(t))
			throw new RuntimeException("Error on "+t);
		
		if (susTuples.contains(t))
			return 1;
		
		if (unknownTuples.contains(t))
			return 0.5;

		return 0;

	}

	@Override
	public void init() {
		// TODO Auto-generated method stub

	}

	@Override
	public void done() {
		// TODO Auto-generated method stub

	}

}
