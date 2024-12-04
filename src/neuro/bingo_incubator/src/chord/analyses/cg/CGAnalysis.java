package chord.analyses.cg;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.ProgramDom;
import chord.bddbddb.Rel.IntPairIterable;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.object.Pair;
import chord.project.OutDirUtils;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;

@Chord(name="cg-java",
	consumes = { "M" }
)
public class CGAnalysis extends JavaAnalysis {
	@Override
	public void run() {
		ProgramDom domM = (ProgramDom) ClassicProject.g().getTrgt("M");
		int numM = domM.size();
		
		int[] numReachM_kcfa_Prune = new int[numM];
		ClassicProject.g().runTask("klimited-prunerefine-java");
		ProgramRel rel_kcfa_Prune = (ProgramRel) ClassicProject.g().getTrgt("outCsreachMM");
		rel_kcfa_Prune.load();
		IntPairIterable tuples_kcfa_Prune = rel_kcfa_Prune.getAry2IntTuples();
		for (IntPair tuple : tuples_kcfa_Prune) {
			int m1 = tuple.idx0;
			numReachM_kcfa_Prune[m1]++;
		}
		rel_kcfa_Prune.close();
		PrintWriter out_kcfa_Prune= OutDirUtils.newPrintWriter("results_kcfa_Prune.txt");
		for (int m = 0; m < numM; m++) {
			out_kcfa_Prune.println(numReachM_kcfa_Prune[m] + " " + domM.get(m));
		}
		out_kcfa_Prune.close();
				
		
		System.setProperty("chord.ctxt.kind", "ci");
		System.setProperty("chord.kcfa.k", "0");
		ClassicProject.g().runTask("cspa-0cfa-dlog");
		
		ClassicProject.g().runTask("reach-dlog");
		
		int[] numReachM = new int[numM];
		ProgramRel rel_0cfa = (ProgramRel) ClassicProject.g().getTrgt("reachMM");
		rel_0cfa.load();
		IntPairIterable tuples_0cfa = rel_0cfa.getAry2IntTuples();
		for (IntPair tuple : tuples_0cfa) {
			int m = tuple.idx0;
			numReachM[m]++;
		}
		rel_0cfa.close();
		
		PrintWriter out_0cfa = OutDirUtils.newPrintWriter("results_0cfa.txt");
		for (int m = 0; m < numM; m++) {
			out_0cfa.println(numReachM[m] + " " + domM.get(m));
		}
		out_0cfa.close();
		
		
		int[] numReachM_kcfa_noPrune = new int[numM];
		ClassicProject.g().runTask("cinsencg-java");
		ProgramRel rel_kcfa_noPrune = (ProgramRel) ClassicProject.g().getTrgt("csreachMM");
		rel_kcfa_noPrune.load();
		IntPairIterable tuples_kcfa_noPrune = rel_kcfa_noPrune.getAry2IntTuples();
		for (IntPair tuple : tuples_kcfa_noPrune) {
			int m1 = tuple.idx0;
			numReachM_kcfa_noPrune[m1]++;
		}
		rel_kcfa_noPrune.close();
		PrintWriter out_kcfa_noPrune= OutDirUtils.newPrintWriter("results_kcfa_noPrune.txt");
		for (int m = 0; m < numM; m++) {
			out_kcfa_noPrune.println(numReachM_kcfa_noPrune[m] + " " + domM.get(m));
		}
		out_kcfa_noPrune.close();
	}
}

