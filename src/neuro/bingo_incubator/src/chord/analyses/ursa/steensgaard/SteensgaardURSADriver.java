package chord.analyses.ursa.steensgaard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alloc.DomH;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import joeq.Compiler.Quad.Quad;

@Chord(name = "steensgaard-mln-gen")
public class SteensgaardURSADriver extends MLNAnalysisDriver {
	private final static int k = 2;
	
	private static Set<String> relsWithLabels;
	private static Set<String> oracleRels;

	static {
		relsWithLabels = new ArraySet<String>();
		relsWithLabels.add("sVH");
		relsWithLabels.add("sFH");
		relsWithLabels.add("sHH");
		
		relsWithLabels.add("ptsVT");
		relsWithLabels.add("unsafeDowncast");
	}


	@Override
	protected Set<String> getDerivedRelations() {
		Set<String> ret = new HashSet<String>();
		ret.add("alloc");
		ret.add("move");
		ret.add("sload");
		ret.add("sstore");
		ret.add("loadGlobal");
		ret.add("storeGlobal");
		ret.add("sHH");
		ret.add("sVH");
		ret.add("sFH");
		
		
		// client specific, now just support downcast
		ret.add("ptsVT");
		ret.add("unsafeDowncast");
		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();
		ret.add("T");
		ret.add("F");
		ret.add("M");
		ret.add("I");
		ret.add("H");
		ret.add("V");
		ret.add("Z");
		
		// client specific
		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();
		ret.add("VH");
		ret.add("HT");
		ret.add("MmethArg");
		ret.add("MmethRet");
		ret.add("IinvkArg");
		ret.add("IinvkRet");
		ret.add("IM");
		ret.add("HFH");
		ret.add("VH");
		ret.add("reachableM");
		ret.add("reachableI");
		ret.add("VHfilter");

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

		// client specific
		ret.add("sub");
		ret.add("McheckCastInst");
		ret.add("checkExcludedM");

		return ret;
	}

	@Override
	protected String getQueryRelation() {
		return "unsafeDowncast";
	}

	@Override
	protected String[] getConfigFiles() {
		String chordIncubator = System.getenv("CHORD_INCUBATOR");
		String steensgaardConfig = chordIncubator + File.separator + "src/chord/analyses/ursa/steensgaard/steensgaard-dlog_XZ89_.config";
		String downcast = chordIncubator + File.separator + "src/chord/analyses/ursa/steensgaard/steensgaard-downcast-dlog_XZ89_.config";

		String[] configFiles = new String[] { steensgaardConfig, downcast };
		return configFiles;

	}

	@Override
	protected void genTasks() {
		tasks = new ArrayList<ITask>();

		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		
		tasks.add(ClassicProject.g().getTask("steensgaard-dlog_XZ89_"));
		
		tasks.add(ClassicProject.g().getTask("steensgaard-downcast-dlog_XZ89_"));

	}

	@Override
	protected void runOracle() {
		System.setProperty("chord.ctxt.kind", "co");
		System.setProperty("chord.kobj.k", this.k+"");
		ClassicProject.g().runTask("cipa-0cfa-dlog");
		ClassicProject.g().runTask("ctxts-java");
		ClassicProject.g().runTask("argCopy-dlog");
		ClassicProject.g().runTask("cspa-kobj-dlog");
		ClassicProject.g().runTask("cspa-adapter-dlog");
		ClassicProject.g().runTask("steensgaard-downcast-dlog");
	}

	@Override
	protected void runBaseCase() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}

	@Override
	protected Set<Tuple> project(Tuple t) {
		// manual projection
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");

		Set<Tuple> ret = new ArraySet<Tuple>();

		// CVC -> sVH
		if (t.getRelName().equals("CVC")) {
			Ctxt c2 = (Ctxt) t.getValue(2);
			Quad h = c2.get(0);
			ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("sVH");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[1];
			indicies[1] = domH.indexOf(h);
			Tuple r = new Tuple(vh, indicies); // be careful here, the projected
												// tuple might or might not be
												// contained in the actual
												// relation.
			ret.add(r);
		} else
		// FC -> sFH
		if (t.getRelName().equals("FC")) {
			Ctxt c = (Ctxt) t.getValue(1);
			Quad h = c.get(0);
			ProgramRel fh = (ProgramRel) ClassicProject.g().getTrgt("sFH");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[0];
			indicies[1] = domH.indexOf(h);
			Tuple r = new Tuple(fh, indicies);
			ret.add(r);
		} else
		// CFC - > HH
		if (t.getRelName().equals("CFC")) {
			Ctxt c1 = (Ctxt) t.getValue(0);
			Ctxt c2 = (Ctxt) t.getValue(2);
			ProgramRel hh = (ProgramRel) ClassicProject.g().getTrgt("sHH");
			int indicies[] = new int[2];
			indicies[0] = domH.indexOf(c1.get(0));
			indicies[1] = domH.indexOf(c2.get(0));
			Tuple r = new Tuple(hh, indicies);
			ret.add(r);
		} 
		else
			ret.add(t);
		return ret;
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		return axiomTuples;
	}

	@Override
	protected void generateFeedback(String feedbackFile, Set<Tuple> baseTuples, Set<Tuple> oracleTuples) {
		try {
			PrintWriter pw = new PrintWriter(new File(feedbackFile));
			pw.println("// Feedback");
			int qtsNum = this.getQueryTupleNum();
			for (Tuple t : baseTuples) {
				if(!relsWithLabels.contains(t.getRelName()))
					continue;
				boolean ifSurvive = oracleTuples.contains(t);
				if (this.evidenceWeight == -1)
					if (ifSurvive)
						pw.println(t);
					else
						pw.println("!" + t);
				else if (this.evidenceWeight == -2)
					if (ifSurvive)
						pw.println((qtsNum + 1) + " " + t);
					else
						pw.println((0 - qtsNum - 1) + " " + t);
				else if (ifSurvive)
					pw.println(this.evidenceWeight + " " + t);
				else
					pw.println((0 - this.evidenceWeight) + " " + t);
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void generateInfFeedback(String feedbackFile, Set<Tuple> baseQueries, Set<Tuple> oracleQueries) {
		System.out.println("Warning: do nothing in generateInfFeedback.");
	}

	@Override
	protected void generateUserStudyFeedback(String feedbackFile) {
		System.out.println("Warning: do nothing in generateUserStudyFeedback.");
	}


}
