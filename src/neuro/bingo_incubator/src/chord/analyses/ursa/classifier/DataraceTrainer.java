package chord.analyses.ursa.classifier;

import static chord.util.RelUtil.pRel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.mln.ConstraintItem;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * -Dchord.ursa.nonpldiK=<1>
 * -Dchord.ursa.useThrEsc=<false>
 * -Dchord.ursa.threscFile=<null>
 * -Dchord.ursa.pointer=<kobj>
 * 
 * @author Xin
 *
 */
@Chord(name = "datarace-ursa-train")
public class DataraceTrainer extends ClassifierTrainer {
	private int nonPLDIK;
	private String pointerAnalysis;
	private boolean useThrEsc;
	private String thrEscFile;

	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("threadAC");
		ret.add("threadACH");
		ret.add("threadPM_cs");
		ret.add("threadPH_cs");
		ret.add("simplePM_cs");
		ret.add("simplePH_cs");
		ret.add("simplePT_cs");
		ret.add("PathEdge_cs");
		ret.add("mhp_cs");
		ret.add("SummEdge_cs");
	
		//flowins-thresc-cs-dlog
		ret.add("escO");
		ret.add("CEC");

		//datarace-parallel-include-cs-dlog
		ret.add("mhe_cs");

		//datarace-cs-noneg-dlog
		ret.add("statE");
		ret.add("escapingRaceHext");
		ret.add("parallelRaceHext");
		ret.add("datarace");
		ret.add("racePairs_cs");

		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("AS");
		ret.add("I");
		ret.add("M");
		ret.add("P");
		ret.add("C");
	
		//flowins-thresc-cs-dlog
		ret.add("E");
		ret.add("M");
		ret.add("V");
		ret.add("Z");
		ret.add("C");
		ret.add("F");

		//datarace-parallel-include-cs-dlog
		ret.add("AS");
		ret.add("E");
		ret.add("P");
		ret.add("C");
		
		//datarace-cs-noneg-dlog
		ret.add("AS");
		ret.add("E");
		ret.add("K");
		ret.add("C");
		ret.add("F");

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("PP");
		ret.add("MPhead");
		ret.add("MPtail");
		ret.add("PI");
		ret.add("CICM");
		ret.add("threadACM");
		ret.add("threadStartI");
		ret.add("threadCICM");
	
		//flowins-thresc-cs-dlog
		ret.add("CVC");
		ret.add("FC");
		ret.add("CFC");
		ret.add("MmethArg");
		ret.add("EV");
		ret.add("escE");
		
		//datarace-parallel-include-cs-dlog
		ret.add("PE");
	//	ret.add("mhp_cs");

		//datarace-cs-noneg-dlog
		ret.add("EF");
		ret.add("statF");
		ret.add("excludeSameThread");
		ret.add("unlockedRaceHext");
	//	ret.add("mhe_cs");
	//	ret.add("CEC");

		return ret;
	}

	@Override
	protected String getQueryRelation(){
		return "racePairs_cs";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[4];
		String chordMain = System.getenv("CHORD_INCUBATOR"); 
		configFiles[0] = chordMain + File.separator + "src/chord/analyses/mln/datarace/flowins-thresc-cs-dlog_XZ89_.config";
		configFiles[1] = chordMain + File.separator + "src/chord/analyses/mln/datarace/mhp-cs-dlog_XZ89_.config";
	//	configFiles[1] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-escaping-include-cs-dlog_XZ89_.config";
		configFiles[2] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-parallel-include-cs-dlog_XZ89_.config";
		configFiles[3] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-cs-noneg-dlog_XZ89_.config";
		return configFiles;
	}

	@Override
	protected void genTasks(){
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		if (this.pointerAnalysis.equals("kobj")) {
			tasks.add(ClassicProject.g().getTask("cspa-kobj-dlog"));
		} else {
			tasks.add(ClassicProject.g().getTask("cspa-kcfa-dlog"));
		}
		tasks.add(ClassicProject.g().getTask("thrSenCSCG-dlog"));
		tasks.add(ClassicProject.g().getTask("reachableACM-dlog"));
		tasks.add(ClassicProject.g().getTask("syncCLC-dlog"));
		tasks.add(ClassicProject.g().getTask("datarace-nongrded-include-cs-dlog"));
		tasks.add(ClassicProject.g().getTask("escE-java")); //PLDI'16
		tasks.add(ClassicProject.g().getTask("datarace-cs-init-dlog"));
	//	tasks.add(ClassicProject.g().getTask("mhp-cs-dlog"));
		
		// we use the instrumented files from as we need all derivation paths for reverted constraints
		// also, we need to output all relations
		tasks.add(ClassicProject.g().getTask("flowins-thresc-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("mhp-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("datarace-parallel-include-cs-dlog_XZ89_"));
	//	tasks.add(ClassicProject.g().getTask("datarace-escaping-include-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("datarace-cs-noneg-dlog_XZ89_"));
	}

	/**
	 * Invoke kobj-refiner to get the result.
	 */
	@Override
	protected void runOracle(){
		if (this.pointerAnalysis.equals("kobj")) {
			System.setProperty("chord.ctxt.kind", "co");
			System.setProperty("chord.kobj.k", ""+this.nonPLDIK);
		} else {
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", ""+this.nonPLDIK);
		}
		if (this.useThrEsc){
			System.setProperty("chord.mln.useThrEsc", "true");
			System.setProperty("chord.mln.threscFile", this.thrEscFile);
		}

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase(){
//		if (this.pointerAnalysis.equals("kobj")) {
//			System.setProperty("chord.ctxt.kind", "co");
//			System.setProperty("chord.kobj.k", "1");
//		} else {
		// Always run 0-cfa, but note the labels on intermediate tuples might not make sense anymore
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", "0");
//		}
		System.clearProperty("chord.mln.threscFile");
		for (ITask t : tasks) {
			if(t.getName().equals("cspa-kobj-dlog"))
				t = ClassicProject.g().getTask("cspa-kcfa-dlog");
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}

	}

	//In kobj, there're two kinds of Cs: H and O. For simplicity, we project t to both possibilities
	@Override
	protected Set<Tuple> project(Tuple t){
		int[] newIndicies = new int[t.getIndices().length];
		Set<Tuple> ret = this.projectRecursively(t, newIndicies, 0);
		return ret;
	}

	private Set<Tuple> projectRecursively(Tuple t, int[] newIndicies, int index){
		Set<Tuple> ret = new HashSet<Tuple>();
		Dom doms[] = t.getDomains();
		Dom d = doms[index];
		int oriIndicies[] = t.getIndices();
		if(d instanceof DomC){
			DomC dc = (DomC)d;
			Ctxt ct = dc.get(oriIndicies[index]);
			Ctxt ct1 = ct.prefix(0);
			Ctxt ct2 = ct.prefix(1);
			int[] newIndicies1 = new int[newIndicies.length];
			int[] newIndicies2 = new int[newIndicies.length];
			System.arraycopy(newIndicies, 0, newIndicies1, 0, newIndicies.length);
			System.arraycopy(newIndicies, 0, newIndicies2, 0, newIndicies.length);
			newIndicies1[index] = dc.indexOf(ct1);
			newIndicies2[index] = dc.indexOf(ct2);
			if(index == newIndicies.length-1){
				Tuple t1 = new Tuple(t.getRel(),newIndicies1);
				Tuple t2 = new Tuple(t.getRel(),newIndicies2);
				ret.add(t1);
				ret.add(t2);
			}else{
				index++;
				ret.addAll(this.projectRecursively(t, newIndicies1, index));
				ret.addAll(this.projectRecursively(t, newIndicies2, index));
			}
		}else{
			int[] newIndicies1 = new int[newIndicies.length];
			System.arraycopy(newIndicies, 0, newIndicies1, 0, newIndicies.length);
			newIndicies1[index] = oriIndicies[index];
			if(index == newIndicies.length-1){
				Tuple t1 = new Tuple(t.getRel(),newIndicies1);
				ret.add(t1);
			}else{
				index++;
				ret.addAll(this.projectRecursively(t, newIndicies1, index));
			}		
		}
		return ret;
	}

	@Override
	protected void readSettings(){
		super.readSettings();
		this.nonPLDIK = Integer.getInteger("chord.ursa.nonpldiK", 1);
		this.useThrEsc = Boolean.getBoolean("chord.ursa.useThrEsc");
		this.thrEscFile = System.getProperty("chord.ursa.threscFile");
		if (this.useThrEsc && this.thrEscFile == null) {
			throw new RuntimeException("Specify thread escape proven queries file.");
		}
		
		this.pointerAnalysis = System.getProperty("chord.ursa.pointer", "kobj");
		if(!this.pointerAnalysis.equals("kcfa") && !this.pointerAnalysis.equals("kobj")){
			throw new RuntimeException("Unknown pointer analysis");
		} 

		System.setProperty("chord.datarace.exclude.init", "false");
		System.setProperty("chord.datarace.exclude.eqth", "true");
		System.setProperty("chord.datarace.exclude.nongrded", "true");
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("PathEdge_cs"), new int[]{0, 0, 1, 0, 0}));
		return axiomTuples;
	}

	@Override
	protected void trainAndSaveClassifier(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap,
			Set<ConstraintItem> provenance, Set<Tuple> relevantTuples, String classifierPath2) {
//		Classifier classifier = new RelNameClassifier();
		Classifier classifier = new ProvenanceClassifier();
		classifier.train(edbLabelMap, idbLabelMap, provenance, relevantTuples);
		classifier.save(classifierPath2);
	}	
	
}
