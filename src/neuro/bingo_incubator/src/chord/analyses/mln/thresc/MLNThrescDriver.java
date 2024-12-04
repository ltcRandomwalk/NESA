package chord.analyses.mln.thresc;

import static chord.util.RelUtil.pRel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.provenance.Tuple;

/**
 * 
 * @author Ravi
 *
 */
@Chord(name = "thresc-mln-gen")
public class MLNThrescDriver extends MLNAnalysisDriver {
	private int nonPLDIK;

	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();
		
		ret.add("escE");
		ret.add("escH");
		ret.add("escPVH");
		ret.add("escPVH_3_0");
		ret.add("escPVH_3_1");
		
		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();

		ret.add("E");
		ret.add("M");
		ret.add("P");
		ret.add("V");
		ret.add("F");
		ret.add("Z");
		ret.add("H");

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		ret.add("queryE");
		ret.add("VH");
		ret.add("FH");
		ret.add("HFH");
		ret.add("MmethArg");
		ret.add("MV");
		ret.add("MP");
		ret.add("EV");

		return ret;
	}

	@Override
	protected String getQueryRelation(){
		return "escE";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[1];
		String chordMain = System.getenv("CHORD_INCUBATOR"); 
		configFiles[0] = chordMain + File.separator + "src/chord/analyses/mln/thresc/flowins-thresc-dlog_XZ89_.config";
		return configFiles;
	}

	@Override
	protected void genTasks(){
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("queryE"));
		
		// we use the instrumented files from as we need all derivation paths for reverted constraints
		// also, we need to output all relations
		tasks.add(ClassicProject.g().getTask("flowins-thresc-dlog_XZ89_"));
	}

	/**
	 * Invoke kobj-refiner to get the result.
	 */
	@Override
	protected void runOracle(){
		throw new RuntimeException("Run thread-escape oracle separately since it needs client server mode.");
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
		throw new RuntimeException("Projection not supported for the thr-esc client");
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
	
	@Override
	protected Set<Tuple> readOracleQueries(String oraclePath) {
		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			BufferedReader br =
				new BufferedReader(new InputStreamReader(new FileInputStream(
					oraclePath)));
			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().equals("")) {
					continue;
				}
				if (line.startsWith("//")) {
					continue;
				}
				if (line.startsWith("!")) {
					br.close();
					throw new RuntimeException("Result produced by the MLN engine should not contain negated tuples");
				} else {
					String tuple = "escE(" + line + ")";
					Tuple current = new Tuple(tuple);
					queries.add(current);
				}
			}
			br.close();
			return queries;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
