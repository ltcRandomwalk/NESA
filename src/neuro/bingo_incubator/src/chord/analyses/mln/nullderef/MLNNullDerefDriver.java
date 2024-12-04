package chord.analyses.mln.nullderef;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
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

/**
 * -Dchord.mln.nonpldiK
 * -Dchord.mln.pointer
 * 
 * @author Ravi
 *
 */
@Chord(name = "nullderef-mln-gen")
public class MLNNullDerefDriver extends MLNAnalysisDriver {
	private int nonPLDIK;
	private String pointerAnalysis;

	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();

		if (!areCurrentRelsOracle) {
			ret.add("localNull");
			ret.add("nullDeref");
		} else {
			ret.add("localNotNull");
			ret.add("nullDeref");
		}

		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();
		if (!areCurrentRelsOracle) {
			ret.add("M");
			ret.add("P");
			ret.add("V");
			ret.add("F");
			ret.add("Z");
			ret.add("H");
		}

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		if (!areCurrentRelsOracle) {
			ret.add("PP");
			ret.add("MPhead");
			ret.add("MPtail");
			ret.add("rPInvkInst");
			ret.add("rPInvkRetInst");
			ret.add("rPInvkRetNullInst");
			ret.add("rPInvkRetNotNullInst");
			ret.add("rPInvkRetInstNotFilter");
			ret.add("nonRelevantP");
			ret.add("rPInvkSkip");
			ret.add("rPInvkSkipV");
			ret.add("rPobjValAsgnInst");
			ret.add("rPobjVarAsgnInst");
			ret.add("rPobjOnlyNullAsgnInst");
			ret.add("rPobjConstAsgnInst");
			ret.add("rPgetInstFldInst");
			ret.add("rPgetStatFldInst");
			ret.add("errorCandidates");
		}
		return ret;
	}

	@Override
	protected String getQueryRelation(){
		return "nullDeref";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[1];
		String chordMain = System.getenv("CHORD_INCUBATOR"); 
		configFiles[0] = chordMain + File.separator + "src/chord/analyses/mln/nullderef/nullDeref-dlog_XZ89_.config";
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
		tasks.add(ClassicProject.g().getTask("nullDeref-init-dlog"));
	}

	@Override
	protected void runOracle(){
		if (this.pointerAnalysis.equals("kobj")) {
			System.setProperty("chord.ctxt.kind", "co");
			System.setProperty("chord.kobj.k", ""+this.nonPLDIK);
		} else {
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", ""+this.nonPLDIK);
		}
		
		areCurrentRelsOracle = true;

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		
		ClassicProject.g().resetTaskDone("mustnotnull-java");
		ClassicProject.g().runTask("mustnotnull-java");
		ClassicProject.g().resetTaskDone("nullDerefErrors-dlog");
		ClassicProject.g().runTask("nullDerefErrors-dlog");
	}
	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase(){
		if (this.pointerAnalysis.equals("kobj")) {
			System.setProperty("chord.ctxt.kind", "co");
			System.setProperty("chord.kobj.k", "1");
		} else {
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", "0");
		}
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		ClassicProject.g().resetTaskDone("nullDeref-dlog_XZ89_");
		ClassicProject.g().runTask("nullDeref-dlog_XZ89_");
		
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
		this.nonPLDIK = Integer.getInteger("chord.mln.nonpldiK", 1);
		this.pointerAnalysis = System.getProperty("chord.mln.pointer", "kcfa");
		if(!this.pointerAnalysis.equals("kcfa") && !this.pointerAnalysis.equals("kobj")){
			throw new RuntimeException("Unknown pointer analysis");
		} 
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		return axiomTuples;
	}
	
	@Override
	protected void generateScope(String scopeFile){
		super.generateScope(scopeFile);
		try {
			if (areCurrentRelsOracle) return;
			
			ClassicProject.g().resetTaskDone("nullDerefCauses-dlog");
			ClassicProject.g().runTask("nullDerefCauses-dlog");
			PrintWriter pw = new PrintWriter(new File(scopeFile+"_relevant"));
			PrintWriter pw1 = new PrintWriter(new File(scopeFile+"_relevant_lineNum"));
			Set<String> relRels = new HashSet<String>();
			relRels.add("localNullApp");
			relRels.add("localNullAppHeap");
			relRels.add("localNullAppInvk");
			relRels.add("localNullAppHeapQ");
			relRels.add("localNullAppInvkQ");
			for(String irs : relRels){
				ProgramRel pr = (ProgramRel)ClassicProject.g().getTrgt(irs);
				pr.load();
				for(int[] indices : pr.getAryNIntTuples()){
					Tuple t = new Tuple(pr, indices);
					pw.println(t);
					Dom[] doms = t.getDomains();
					Inst i = (Inst) doms[0].get(indices[0]);
					Register v = (Register) doms[1].get(indices[1]);
					jq_Method m = i.getMethod();
					List<String> sourceName = m.getRegName(v);
					pw1.println(t.toString()+"?"+i.toString()+"?"+i.toJavaLocStr()+"?"+sourceName);
				}
			}
			pw.println();
			pw.flush();
			pw.close();
			pw1.flush();
			pw1.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
