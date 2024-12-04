package chord.analyses.ursa.datarace;

import static chord.util.RelUtil.pRel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.URSAAnalysisDriver;
import chord.analyses.ursa.classifier.AllFalseClassifier;
import chord.analyses.ursa.classifier.BaggedClassifier;
import chord.analyses.ursa.classifier.Classifier;
import chord.analyses.ursa.classifier.datarace.DynamicAnalysisClassifier;
import chord.analyses.ursa.classifier.datarace.HandCraftedClassifier;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * -Dchord.ursa.classifier=<craftedAggr/craftedCons/dynamic/bag/uniform>
 * 
 */
@Chord(name = "ursa-datarace-java")
public class URSADataraceDriver extends URSAAnalysisDriver {
	private String classifierKind;

	@Override
	protected Set<String> getDerivedRelations() {
		Set<String> ret = new HashSet<String>();

		// mhp-cs-dlog
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

		// flowins-thresc-cs-dlog
		ret.add("escO");
		ret.add("CEC");

		// datarace-parallel-include-cs-dlog
		ret.add("mhe_cs");

		// datarace-cs-noneg-dlog
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

		// mhp-cs-dlog
		ret.add("AS");
		ret.add("I");
		ret.add("M");
		ret.add("P");
		ret.add("C");

		// flowins-thresc-cs-dlog
		ret.add("E");
		ret.add("M");
		ret.add("V");
		ret.add("Z");
		ret.add("C");
		ret.add("F");

		// datarace-parallel-include-cs-dlog
		ret.add("AS");
		ret.add("E");
		ret.add("P");
		ret.add("C");

		// datarace-cs-noneg-dlog
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

		// mhp-cs-dlog
		ret.add("PP");
		ret.add("MPhead");
		ret.add("MPtail");
		ret.add("PI");
		ret.add("CICM");
		ret.add("threadACM");
		ret.add("threadStartI");
		ret.add("threadCICM");

		// flowins-thresc-cs-dlog
		ret.add("CVC");
		ret.add("FC");
		ret.add("CFC");
		ret.add("MmethArg");
		ret.add("EV");
		ret.add("escE");

		// datarace-parallel-include-cs-dlog
		ret.add("PE");
		// ret.add("mhp_cs");

		// datarace-cs-noneg-dlog
		ret.add("EF");
		ret.add("statF");
		ret.add("excludeSameThread");
		ret.add("unlockedRaceHext");
		// ret.add("mhe_cs");
		// ret.add("CEC");

		return ret;
	}

	@Override
	protected String getQueryRelation() {
		return "racePairs_cs";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[4];
		String chordMain = System.getenv("CHORD_MAIN");
		configFiles[0] = chordMain + File.separator
				+ "src/chord/analyses/ursa/datarace/flowins-thresc-cs-dlog_XZ89_.config";
		configFiles[1] = chordMain + File.separator + "src/chord/analyses/ursa/datarace/mhp-cs-dlog_XZ89_.config";
		// configFiles[1] = chordMain + File.separator +
		// "src/chord/analyses/ursa/datarace/datarace-escaping-include-cs-dlog_XZ89_.config";
		configFiles[2] = chordMain + File.separator
				+ "src/chord/analyses/ursa/datarace/datarace-parallel-include-cs-dlog_XZ89_.config";
		configFiles[3] = chordMain + File.separator
				+ "src/chord/analyses/ursa/datarace/datarace-cs-noneg-dlog_XZ89_.config";
		return configFiles;
	}

	@Override
	protected void genTasks() {
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kcfa-dlog"));
		tasks.add(ClassicProject.g().getTask("thrSenCSCG-dlog"));
		tasks.add(ClassicProject.g().getTask("reachableACM-dlog"));
		tasks.add(ClassicProject.g().getTask("syncCLC-dlog"));
		tasks.add(ClassicProject.g().getTask("datarace-nongrded-include-cs-dlog"));
		tasks.add(ClassicProject.g().getTask("escE-java")); // PLDI'16
		tasks.add(ClassicProject.g().getTask("datarace-cs-init-dlog"));
		// tasks.add(ClassicProject.g().getTask("mhp-cs-dlog"));

		// also, we need to output all relations
		tasks.add(ClassicProject.g().getTask("ursa-flowins-thresc-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("ursa-mhp-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("ursa-datarace-parallel-include-cs-dlog_XZ89_"));
		// tasks.add(ClassicProject.g().getTask("datarace-escaping-include-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("ursa-datarace-cs-noneg-dlog_XZ89_"));
	}

	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase() {
		System.setProperty("chord.ctxt.kind", "cs");
		System.setProperty("chord.kcfa.k", "0");
		for (ITask t : tasks) {
			if (t.getName().equals("cspa-kobj-dlog"))
				t = ClassicProject.g().getTask("cspa-kcfa-dlog");
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}

	}

	@Override
	protected void readSettings() {
		super.readSettings();
		this.classifierKind = System.getProperty("chord.ursa.classifier", "dynamic");
		// System.setProperty("chord.datarace.exclude.init", "false");
		// System.setProperty("chord.datarace.exclude.eqth", "true");
		// System.setProperty("chord.datarace.exclude.nongrded", "true");
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("PathEdge_cs"), new int[] { 0, 0, 1, 0, 0 }));
		return axiomTuples;
	}

	@Override
	public void run() {
		super.run();
		ClassicProject.g().runTask("orderedEE-dlog");
		ProgramRel orderedEE = (ProgramRel) ClassicProject.g().getTrgt("OrderedEE");
		orderedEE.load();
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "correlEE.txt"));
			for (int n[] : orderedEE.getAryNIntTuples()) {
				for (int i : n)
					pw.print("escE(" + i + ") ");
				pw.println();
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath) {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "prediction.txt"));
			Classifier classifier = null;
			if (this.classifierKind.equals("dynamic")) {
				classifier = new DynamicAnalysisClassifier();
			} else if (this.classifierKind.equals("craftedAggr"))
				classifier = new HandCraftedClassifier(true);
			else if (this.classifierKind.equals("craftedCons"))
				classifier = new HandCraftedClassifier(false);
			else if (this.classifierKind.equals("bag"))
				classifier = new BaggedClassifier();
			else if (this.classifierKind.equals("uniform"))
				classifier = new AllFalseClassifier();
			else
				throw new RuntimeException("Unknown classifier " + this.classifierKind);
			for (Tuple t : tuples) {
				pw.println(t + " " + classifier.predictFalse(t, provenance));
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	@Override
	protected void generateAppScope(String fileName) {
		ClassicProject.g().runTask("checkExcludedP-dlog");
		ClassicProject.g().runTask("checkExcludedI-dlog");
		ClassicProject.g().runTask("checkExcludedE-dlog");

		ProgramRel checkExcludedI = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedI");
		checkExcludedI.load();
		ProgramRel checkExcludedP = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedP");
		checkExcludedP.load();
		ProgramRel checkExcludedE = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedE");
		checkExcludedE.load();

		try {
			PrintWriter pw = new PrintWriter(new File(fileName));

			// app causes
			ProgramRel pathEdge = (ProgramRel) ClassicProject.g().getTrgt("PathEdge_cs");
			pathEdge.load();

			for (int content[] : pathEdge.getAryNIntTuples()) {
				Tuple t = new Tuple(pathEdge, content);
				// check if P is app P
				if (!checkExcludedP.contains(content[1]))
					pw.println(t.toString());
			}

			ProgramRel escE = (ProgramRel) ClassicProject.g().getTrgt("escE");
			escE.load();

			for (int content[] : escE.getAryNIntTuples()) {
				Tuple t = new Tuple(escE, content);
				// check if E is appE
				if (!checkExcludedE.contains(content[0]))
					pw.println(t.toString());
			}

			ProgramRel cicm = (ProgramRel) ClassicProject.g().getTrgt("CICM");
			cicm.load();

			for (int content[] : cicm.getAryNIntTuples()) {
				Tuple t = new Tuple(cicm, content);
				// check if I is appI
				if (!checkExcludedI.contains(content[1]))
					pw.println(t.toString());
			}

			ProgramRel race = (ProgramRel) ClassicProject.g().getTrgt("racePairs_cs");
			race.load();

			for (int content[] : race.getAryNIntTuples()) {
				Tuple t = new Tuple(race, content);
				pw.println(t.toString());
			}

			pw.flush();
			pw.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
