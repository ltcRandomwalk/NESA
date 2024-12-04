package chord.analyses.mln.cpts;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import chord.analyses.mln.MLNAnalysisDriver;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

import chord.bddbddb.Dom;
import joeq.Compiler.Quad.Quad;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.integer.IntPair;

/**
 * 
 * @author Aditya
 *
 */
@Chord(name = "cpts-mln-gen")
public class MLNCPts extends MLNAnalysisDriver {

	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();

		if (!areCurrentRelsOracle) {
			ret.add("copy0");
			ret.add("pt0");
		} else {
			ret.add("copy0");
			ret.add("pt0");
		}

		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();
		if (!areCurrentRelsOracle) {
			ret.add("ZC");
			ret.add("ZF");
			ret.add("ZI");
			ret.add("ZV");
		}

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		if (!areCurrentRelsOracle) {
			ret.add("copy");
			ret.add("store");
			ret.add("load");
			ret.add("ie");
			ret.add("pass");
			ret.add("ret");
			ret.add("pt");
		}
		return ret;
	}

	@Override
	protected String getQueryRelation(){
		return "pt0";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[1];
		String chordMain = System.getenv("CHORD_INCUBATOR"); 
		configFiles[0] = chordMain + File.separator + "src/canalyses/c-cipa-0cfa-dlog_XZ89_.config";
		return configFiles;
	}

	@Override
	protected void genTasks(){
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("c-cipa-0cfa-dlog_XZ89_"));
	}

	@Override
	protected void runOracle(){
		areCurrentRelsOracle = true;
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("c-cspa-kcfa-dlog_XZ89_"));
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
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		areCurrentRelsOracle = false;
	}

	@Override
	protected Set<Tuple> project(Tuple t){
		Set<Tuple> ret = new HashSet<Tuple>();
		ret.add(t);
		return ret;
	}

	@Override
	protected void readSettings(){
		super.readSettings(); 
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		return axiomTuples;
	}
}
