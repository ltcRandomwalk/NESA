package chord.analyses.ursa.classifier.datarace;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.analyses.mln.ConstraintItem;
import chord.analyses.ursa.classifier.Classifier;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

public class DynamicAnalysisClassifier implements Classifier{
	private Set<Tuple> susTuples = null;
	private Set<Tuple> unknownTuples = null;

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {
		
	}

	@Override
	public void save(String path) {
		
	}

	@Override
	public void load(String path) {
		
	}

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		if(susTuples == null){
			this.susTuples = new HashSet<Tuple>();
			this.unknownTuples = new HashSet<Tuple>();
			ClassicProject.g().runTask("datarace-oracle-dynamic");
			ProgramRel susE = (ProgramRel) ClassicProject.g().getTrgt("susEscE");
			susE.load();
			ProgramRel escE = (ProgramRel) ClassicProject.g().getTrgt("escE");
			escE.load();
			ProgramRel unkE = (ProgramRel) ClassicProject.g().getTrgt("unkEscE");
			unkE.load();
			for (int[] st : susE.getAryNIntTuples())
				this.susTuples.add(new Tuple(escE, st));
			for (int[] ut : unkE.getAryNIntTuples())
				this.unknownTuples.add(new Tuple(escE, ut));

			ProgramRel susPathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("susPathEdge_cs");
			susPathEdge_cs.load();
			ProgramRel pathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("PathEdge_cs");
			pathEdge_cs.load();
			ProgramRel unkPathEdge_cs = (ProgramRel) ClassicProject.g().getTrgt("unkPathEdge_cs");
			unkPathEdge_cs.load();
			for (int[] st : susPathEdge_cs.getAryNIntTuples())
				this.susTuples.add(new Tuple(pathEdge_cs, st));
			for (int[] ut : unkPathEdge_cs.getAryNIntTuples())
				this.unknownTuples.add(new Tuple(pathEdge_cs,ut));

			ProgramRel susCICM = (ProgramRel) ClassicProject.g().getTrgt("susCICM");
			susCICM.load();
			ProgramRel cicm = (ProgramRel) ClassicProject.g().getTrgt("CICM");
			cicm.load();
			for (int[] st : susCICM.getAryNIntTuples())
				this.susTuples.add(new Tuple(cicm, st));

			ProgramRel susCFC = (ProgramRel) ClassicProject.g().getTrgt("susCFC");
			susCFC.load();
			ProgramRel cfc = (ProgramRel) ClassicProject.g().getTrgt("CFC");
			cfc.load();
			for (int[] st : susCFC.getAryNIntTuples())
				this.susTuples.add(new Tuple(cfc, st));
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
