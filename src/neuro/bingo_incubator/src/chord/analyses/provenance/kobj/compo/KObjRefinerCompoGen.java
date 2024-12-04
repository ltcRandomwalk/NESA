package chord.analyses.provenance.kobj.compo;

import chord.analyses.provenance.kobj.KOBJRefiner;
import chord.analyses.provenance.kobj.PTHandler;
import chord.project.Chord;
import chord.project.analyses.provenance.DefaultModel;
import chord.project.analyses.provenance.MaxSatGenerator;
import chord.project.analyses.provenance.Model;

@Chord(name = "kobj-refiner-compogen")
public class KObjRefinerCompoGen extends KOBJRefiner {

	@Override
	protected MaxSatGenerator createMaxSatGenerator(PTHandler ptHandler,
			int queryWeight) {
		Model model;
		if(modelStr.endsWith("default"))
			model = new DefaultModel();
		else
			throw new RuntimeException("A model must be specified to bias the refinement!");
		HintGenerator hintGen = new KObjHintGenerator();
		MaxSatGenerator g = new MaxSATGeneratorWithHints(configFiles, queryRelName, ptHandler, model, queryWeight, hintGen);
		return g;
	}
	
}
