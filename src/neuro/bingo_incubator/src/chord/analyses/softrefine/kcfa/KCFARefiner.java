package chord.analyses.softrefine.kcfa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Compiler.Quad.Quad;
import chord.analyses.alloc.DomH;
import chord.analyses.argret.DomK;
import chord.analyses.invk.DomI;
import chord.bddbddb.Rel.IntAryNIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.softrefine.MaxSatGenerator;
import chord.project.analyses.softrefine.Tuple;
import chord.project.analyses.softrefine.Fun;
import chord.project.analyses.softrefine.ConstraintItem;
import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

/**
 * A general class to run experiments based on k-cfa analysis.
 * -Dchord.softrefine.client=<polysite/downcast/datarace>: specify the client to use
 * @author xin. modified by hongseok.
 * 
 */

@Chord(name = "kcfa-softrefiner")
public class KCFARefiner extends JavaAnalysis {
	List<ITask> tasks;
	PrintWriter debugPW;
	PrintWriter statPW;
	
	int client; // 0 polysite, 1 downcast, 2 datarace
	String clientFile;
	String clientConfigPath;
	
	DomI domI;
	DomH domH;
	DomK domK;
	
	ProgramRel IKRel;
	ProgramRel HKRel;
	ProgramRel allowIRel;
	ProgramRel denyIRel;
	ProgramRel allowHRel;
	ProgramRel denyHRel;
	ProgramRel queryRel;
	String queryRelName;
	
	private void printlnInfo(String s) {
		System.out.println(s);
		debugPW.println(s);
	}

	private void preprocess() {
		try {
			debugPW = new PrintWriter(new File(Config.outDirName + File.separator + "debug.txt"));
			statPW = new PrintWriter(new File(Config.outDirName + File.separator + "stat.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		String client = System.getProperty("chord.softrefine.client");
		if (client.equals("polysite")) {
			this.client = 0;
			clientFile = "polysite-dlog_XZ89_";
			clientConfigPath = "src/chord/analyses/provenance/monosite/polysite-dlog_XZ89_.config";
			queryRelName = "polySite";
		} else if (client.equals("downcast")) {
			this.client = 1;
			clientFile = "pro-downcast-dlog_XZ89_";
			clientConfigPath = "src/chord/analyses/provenance/downcast/pro-downcast-dlog_XZ89_.config";
			queryRelName = "unsafeDowncast";
		} else if (client.equals("datarace")){
			this.client = 2;
			clientFile = "pro-datarace-dlog_XZ89_";
			clientConfigPath = "src/chord/analyses/provenance/race/pro-datarace-dlog_XZ89_.config";
			queryRelName = "racePairs";
		} else
			throw new RuntimeException("Unknown client: " + client);

		// Analyses that we need to run
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("soft-simple-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("soft-argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("soft-kcfa-bit-init-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("soft-cspa-kcfa-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask(clientFile));

		System.setProperty("chord.ctxt.kind", "cs");
		System.setProperty("chord.kobj.khighest", "1");
		System.setProperty("chord.kcfa.khighest", "1");
		
		domI = (DomI) ClassicProject.g().getTrgt("I");
		domK = (DomK) ClassicProject.g().getTrgt("K");
		domH = (DomH) ClassicProject.g().getTrgt("H");
		ClassicProject.g().runTask(domI);
		ClassicProject.g().runTask(domK);
		ClassicProject.g().runTask(domH);
		
		IKRel = (ProgramRel) ClassicProject.g().getTrgt("IK");
		HKRel = (ProgramRel) ClassicProject.g().getTrgt("HK");
		allowIRel = (ProgramRel) ClassicProject.g().getTrgt("AllowI");
		denyIRel = (ProgramRel) ClassicProject.g().getTrgt("DenyI");
		allowHRel = (ProgramRel) ClassicProject.g().getTrgt("AllowH");
		denyHRel = (ProgramRel) ClassicProject.g().getTrgt("DenyH");
		queryRel = (ProgramRel) ClassicProject.g().getTrgt(queryRelName);
	}

	private void setIK(Quad q){
		IKRel.add(q,0);
		denyIRel.add(q,1);
	}

	private void setHK(Quad q){
		HKRel.add(q,1);
		allowHRel.add(q,1);
	}
	
	private void zero(List<ProgramRel> rels) {
		for (ProgramRel rel : rels) rel.zero();
	}
	
	private void save(List<ProgramRel> rels) {
		for (ProgramRel rel : rels) rel.save();
	}
	
	/* Run the analysis */
	private void runCFA() {
		List<ProgramRel> IRels = Arrays.asList(IKRel, allowIRel, denyIRel);
		zero(IRels);
		for (int i = 0; i < domI.size(); i++) {
			Quad I = (Quad) domI.get(i);
			setIK(I);
		}
		save(IRels);
		
		List<ProgramRel> HRels = Arrays.asList(HKRel, allowHRel, denyHRel);
		zero(HRels);
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			setHK(H);
		}
		save(HRels);
		
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	/* Collect unresolved (i.e., derived) query tuples */
	private Set<Tuple> getUnresolvedQueries() {
		queryRel.load();
		Set<Tuple> ret = new HashSet<Tuple>();
		for (int[] idx : queryRel.getAryNIntTuples())
			ret.add(new Tuple(queryRel, idx));
		return ret;
	}
	
	private void postprocess() {
		debugPW.flush(); 
		debugPW.close();
		statPW.flush(); 
		statPW.close();
	}
	
	@Override
	public void run() {
		preprocess();	
		printlnInfo("==============================================");
		printlnInfo("Prologue of the analysis");
		String opt = System.getProperty("chord.softrefine.queryOption", "all");
		printlnInfo("Options: chord.softrefine.queryOption = " + opt);
		printlnInfo("Abstractions: k=0 for methods and k=1 for heaps");

		printlnInfo("==============================================");
		printlnInfo("Main part of the analysis");
		runCFA();

		printlnInfo("==============================================");
		Set<Tuple> unresolvedQueries = getUnresolvedQueries();
		printlnInfo("Number of unresolved queries: " + unresolvedQueries.size());
		for (Tuple t : unresolvedQueries) printlnInfo(t.toVerboseString());
		
		printlnInfo("==============================================");
		printlnInfo("Epilogue of the analysis");
		// ResultAnalyzer resultAnalyzer = new ResultAnalyzer(clientConfigPath);
		// resultAnalyzer.analyze(queryRel);

		printlnInfo("==============================================");
		printlnInfo("Completion of the analysis");
		postprocess();
	}
}

class ResultAnalyzer {
	MaxSatGenerator gen;
	
	public ResultAnalyzer(String clientConfigPath) {
		String chordIncu = System.getenv("CHORD_INCUBATOR");
		String kinitConfig = chordIncu + File.separator + "src/chord/analyses/softrefine/kcfa/kcfa-bit-init-dlog_XZ89_.config";
		String kcfaConfig = chordIncu + File.separator + "src/chord/analyses/softrefine/kcfa/pro-cspa-kcfa-dlog_XZ89_.config";
		String clientConfig = chordIncu + File.separator + clientConfigPath;
		String[] configFiles = new String[]{ kinitConfig, kcfaConfig, clientConfig };
		gen = new MaxSatGenerator(configFiles, new PTHandler());
		MaxSatGenerator.DEBUG = true;
	}
	
	private Set<Tuple> getFalseQueries(ProgramRel queryRel) {
		Set<Tuple> fQueries = new HashSet<Tuple>();
		try {
			String oracleFileName = System.getenv("CHORD_INCUBATOR") + File.separator 
					+ "src" + File.separator + "chord" + File.separator + "analyses" + File.separator 
					+ "softrefine" + File.separator + "kcfa" + File.separator + "falseTuples.txt";
			File oracle = new File(oracleFileName);
			Scanner sc = new Scanner(oracle);		
			while (sc.hasNext()) {
				String line = sc.nextLine();
				int openP = line.indexOf('(');
				int closeP = line.indexOf(')');
				String[] params = line.substring(openP+1, closeP).split(",");
				int[] indices = new int[params.length];
				for (int i = 0; i < params.length; i++)
					indices[i] = Integer.parseInt(params[i]);
				fQueries.add(new Tuple(queryRel, indices));
			}
			sc.close();
			return fQueries;
		} catch (FileNotFoundException e) {
			return fQueries;
		}	
	}
	
	private Set<Tuple> getTrueQueries(ProgramRel queryRel, Set<Tuple> fQueries) {
		Set<Tuple> tQueries = new HashSet<Tuple>();
		IntAryNIterable iter = queryRel.getAryNIntTuples();
		for (int[] indices : iter) {
			Tuple t = new Tuple(queryRel, indices);
			if (!fQueries.contains(t)) tQueries.add(t);
		}
		return tQueries;
	}
	
	void analyze(ProgramRel queryRel) {
		queryRel.load();
		Set<Tuple> fQueries = getFalseQueries(queryRel);
		Set<Tuple> tQueries = getTrueQueries(queryRel, fQueries);
		Fun<ConstraintItem,Boolean> isHard = new Fun<ConstraintItem,Boolean>() {
			private boolean isCTuple(Tuple t) {
				String relName = t.getRelName();
				return relName.contains("C"); // checks whether the relation name contains C for contexts.
			}
			public Boolean apply(ConstraintItem ci) {
				List<Tuple> conds = ci.subTuples;
				int numCTuples = 0;
				for (Tuple t : conds) { if (isCTuple(t)) numCTuples++; }
				return (numCTuples < 2);
			}
		};
		gen.update();
		gen.solve(tQueries, fQueries, isHard);
	}
}
