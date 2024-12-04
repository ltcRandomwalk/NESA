package chord.analyses.ursa.cipa;

import static chord.util.RelUtil.pRel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.URSAAnalysisDriver;
import chord.analyses.ursa.classifier.Classifier;
import chord.analyses.ursa.classifier.cipa.DynamicAnalysisClassifier;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Quad;

/**
 * -Dchord.ursa.classifier=<dynamic/none>
 * 
 */
@Chord(name = "ursa-cipa-java", consumes = { "checkExcludedV", "checkExcludedH", "checkExcludedT", "checkExcludedI" })
public class URSAcipaDriver extends URSAAnalysisDriver {
	private static Set<String> relsWithLabels;
	private static Set<String> oracleRels;
	private String classifierKind;
	private Classifier classifier;

	static {
		relsWithLabels = new ArraySet<String>();
		relsWithLabels.add("VH");
		relsWithLabels.add("FH");
		relsWithLabels.add("HFH");
		relsWithLabels.add("reachableI");
		relsWithLabels.add("reachableM");
		relsWithLabels.add("IM");
		relsWithLabels.add("MM");

		relsWithLabels.add("ptsVH");

		oracleRels = new ArraySet<String>();
		oracleRels.add("CVC");
		oracleRels.add("FC");
		oracleRels.add("CFC");
		oracleRels.add("reachableCI");
		oracleRels.add("reachableCM");
		oracleRels.add("CICM");
		oracleRels.add("CMCM");
		oracleRels.add("ptsVH");
	}

	@Override
	protected Set<String> getDerivedRelations() {
		Set<String> ret = new HashSet<String>();

		// cipa-0cfa-dlog
		ret.add("IHM");
		ret.add("VV");
		ret.add("specIMV");
		ret.add("objValAsgnInst");
		ret.add("objVarAsgnInst");
		ret.add("getInstFldInst");
		ret.add("putInstFldInst");
		ret.add("getStatFldInst");
		ret.add("putStatFldInst");
		ret.add("reachableT");
		// ret.add("VHfilter"); move to input due to the wild card rule
		ret.add("VH");
		ret.add("FH");
		ret.add("HFH");
		ret.add("rootM");
		ret.add("reachableI");
		ret.add("reachableM");
		ret.add("IM");
		ret.add("MM");
		ret.add("reachableH");
		ret.add("reachableV");
		ret.add("ptsVH");

		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();

		// domains from cipa-0cfa-dlog
		ret.add("T");
		ret.add("F");
		ret.add("M");
		ret.add("I");
		ret.add("H");
		ret.add("V");
		ret.add("Z");

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		// input relations from cipa-0cfa-dlog
		ret.add("VT");
		ret.add("HT");
		ret.add("cha");
		ret.add("sub");
		ret.add("MmethArg");
		ret.add("MmethRet");
		ret.add("IinvkArg0");
		ret.add("IinvkArg");
		ret.add("IinvkRet");
		ret.add("MI");
		ret.add("statIM");
		ret.add("specIM");
		ret.add("virtIM");
		ret.add("MobjValAsgnInst");
		ret.add("MobjVarAsgnInst");
		ret.add("MgetInstFldInst");
		ret.add("MputInstFldInst");
		ret.add("MgetStatFldInst");
		ret.add("MputStatFldInst");
		ret.add("clsForNameIT");
		ret.add("objNewInstIH");
		ret.add("objNewInstIM");
		ret.add("conNewInstIH");
		ret.add("conNewInstIM");
		ret.add("aryNewInstIH");
		ret.add("classT");
		ret.add("staticTM");
		ret.add("staticTF");
		ret.add("clinitTM");

		// hack, make constant relation VHfilter as input
		ret.add("VHfilter");

		// input relations from the client
		ret.add("checkExcludedH");
		ret.add("checkExcludedV");
		ret.add("MH");
		ret.add("MV");

		return ret;
	}

	@Override
	protected String getQueryRelation() {
		return "ptsVH";
	}

	private void generateSearchSpace() {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "ursa_search_space.txt"));
			// checkExcluds
			ProgramRel relExV = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedV");
			relExV.load();

			ProgramRel relExI = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedI");
			relExI.load();

			ProgramRel relExH = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedH");
			relExH.load();

			ProgramRel relExT = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedT");
			relExT.load();

			// VH
			ProgramRel relVH = (ProgramRel) ClassicProject.g().getTrgt("VH");
			relVH.load();

			for (int ts[] : relVH.getAryNIntTuples()) {
				if (!relExV.contains(ts[0])) {
					Tuple t = new Tuple(relVH, ts);
					pw.println(t.toString());
				}
			}

			// IM
			ProgramRel relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
			relIM.load();

			for (int ts[] : relIM.getAryNIntTuples()) {
				if (!relExI.contains(ts[0])) {
					Tuple t = new Tuple(relIM, ts);
					pw.println(t.toString());
				}
			}

			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Set<Tuple> generateFinalQueries(String queryFile) {
		this.generateSearchSpace();
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		boolean usePtsVH = true;

		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			PrintWriter pw = new PrintWriter(new File(queryFile));

			if (usePtsVH) {
				// VH
				{
					ProgramRel checkExcludedV = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedV");
					ProgramRel checkExcludedH = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedH");
					checkExcludedV.load();
					checkExcludedH.load();

					ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
					vh.load();
					for (int[] indices : vh.getAryNIntTuples()) {
						Quad h = (Quad) domH.get(indices[1]);
						if (checkExcludedV.contains(indices[0]) || checkExcludedH.contains(indices[1]))
							continue;
						if (h.getOperator() instanceof New) {
							String cl = New.getType(h).getType().toString();
							if (cl.equals("java.lang.StringBuilder") || cl.equals("java.lang.StringBuffer")) {
								continue;
							}
						}
						Tuple t = new Tuple(vh, indices);
						pw.println(t);
						queries.add(t);
					}
				}

				// IM
				{
					ProgramRel checkExcludedI = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedI");
					checkExcludedI.load();
					ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
					im.load();
					for (int[] indices : im.getAryNIntTuples()) {
						if (checkExcludedI.contains(indices[0]))
							continue;
						Tuple t = new Tuple(im, indices);
						pw.println(t);
						queries.add(t);
					}

				}

			} else {

				// VH
				{
					ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
					vh.load();
					for (int[] indices : vh.getAryNIntTuples()) {
						Quad h = (Quad) domH.get(indices[1]);
						if (h.getOperator() instanceof New) {
							String cl = New.getType(h).getType().toString();
							if (cl.equals("java.lang.StringBuilder") || cl.equals("java.lang.StringBuffer")) {
								continue;
							}
						}
						Tuple t = new Tuple(vh, indices);
						pw.println(t);
						queries.add(t);
					}
				}

				// FH
				{
					ProgramRel fh = (ProgramRel) ClassicProject.g().getTrgt("FH");
					fh.load();
					for (int[] indices : fh.getAryNIntTuples()) {
						Tuple t = new Tuple(fh, indices);
						pw.println(t);
						queries.add(t);
					}

				}

				// HFH
				{
					ProgramRel hfh = (ProgramRel) ClassicProject.g().getTrgt("HFH");
					hfh.load();
					for (int[] indices : hfh.getAryNIntTuples()) {
						jq_Field f = domF.get(indices[1]);
						if (f != null)
							if (f.getDeclaringClass().toString().equals("java.lang.Throwable")) {
								continue;
							}
						Tuple t = new Tuple(hfh, indices);
						pw.println(t);
						queries.add(t);
					}

				}

				// IM
				{
					ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
					im.load();
					for (int[] indices : im.getAryNIntTuples()) {
						Tuple t = new Tuple(im, indices);
						pw.println(t);
						queries.add(t);
					}

				}
			}

			pw.flush();
			pw.close();
			return queries;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	protected String[] getConfigFiles() {

		String chordMain = System.getenv("CHORD_MAIN");

		String cipaConfig = chordMain + File.separator + "src/chord/analyses/ursa/cipa/cipa-0cfa-dlog_XZ89_.config";
		String ptsConfig = chordMain + File.separator + "src/chord/analyses/ursa/cipa/cipa-pts-dlog_XZ89_.config";

		String[] configFiles = new String[] { cipaConfig, ptsConfig };
		return configFiles;

	}

	@Override
	protected void genTasks() {
		tasks = new ArrayList<ITask>();

		tasks.add(ClassicProject.g().getTask("ursa-cipa-0cfa-dlog_XZ89_"));

		tasks.add(ClassicProject.g().getTask("ursa-cipa-pts-dlog_XZ89_"));
	}

	private String getClient() {
		String clientStr = null;
		clientStr = "pts";
		return clientStr;
	}

	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase() {

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}

	private Quad getH(Ctxt c) {
		if (c.length() == 0)
			return null;
		return c.get(0);
	}

	@Override
	protected void readSettings() {
		super.readSettings();

		this.classifierKind = System.getProperty("chord.ursa.classifier", "dynamic");

		System.setProperty("chord.ctxt.kind", "co");
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("rootM"), new int[] { 0 }));
		axiomTuples.add(new Tuple(pRel("reachableM"), new int[] { 0 }));
		return axiomTuples;
	}


	@Override
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath) {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "prediction.txt"));
			// always run dynamic now
			// if(this.classifierKind.equals("dynamic")){
			classifier = new DynamicAnalysisClassifier();
			// }
			// else if (this.classifierKind.equals("none"))
			// classifier = null;
			// else
			// throw new RuntimeException("Unknown classifier
			// "+this.classifierKind);
			for (Tuple t : tuples) {
				if (classifier == null)
					pw.println(t + " 0");
				else
					pw.println(t + " " + classifier.predictFalse(t, provenance));
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

}
