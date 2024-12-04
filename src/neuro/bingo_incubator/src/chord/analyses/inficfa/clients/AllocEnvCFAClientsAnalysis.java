package chord.analyses.inficfa.clients;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.inficfa.BitAbstractState;
import chord.analyses.inficfa.BitEdge;
import chord.analyses.inficfa.alloc.AllocEnvCFAAnalysis;
import chord.analyses.typestate.Edge;
import chord.analyses.typestate.Helper;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

@Chord(name = "allocEnvCFAClients-java")
public class AllocEnvCFAClientsAnalysis extends JavaAnalysis {
	
	@Override
	public void run(){
		//1. Callgraph client
		ClassicProject.g().runTask("allocEnvCFA-java");
		
		Timer queryGenTimer = new Timer("allocEnvCFA-QueryGen");
		queryGenTimer.init();
		
		System.out.println("ENTER generateClientQueries");

		ClassicProject.g().runTask("monosite-dlog");
		
		ProgramRel rel1 = (ProgramRel) ClassicProject.g().getTrgt("allQueries");
		rel1.load();
		Iterable<Quad> tuples1 = rel1.getAry1ValTuples();
		PrintWriter out1 = OutDirUtils.newPrintWriter("allMonositeQueries_CFA2.txt");
		for (Quad tuple : tuples1) {
			out1.println(tuple);
		}
		rel1.close();
		out1.close();
		
		ProgramRel rel2 = (ProgramRel) ClassicProject.g().getTrgt("polySite");
		rel2.load();
		Iterable<Quad> tuples2 = rel2.getAry1ValTuples();
		PrintWriter out2 = OutDirUtils.newPrintWriter("failedMonositeQueries_CFA2.txt");
		for (Quad tuple : tuples2) {
			out2.println(tuple);
		}
		rel2.close();
		out2.close();
		
		ClassicProject.g().runTask("downcast-dlog");
		
		ProgramRel rel3 = (ProgramRel) ClassicProject.g().getTrgt("downcast");
		rel3.load();
		Iterable<Pair<Register, jq_Type>> tuples3 = rel3.getAry2ValTuples();
		PrintWriter out3 = OutDirUtils.newPrintWriter("allDowncastQueries_CFA2.txt");
		for (Pair<Register, jq_Type> tuple : tuples3) {
			out3.println(tuple.val0 + " " + tuple.val1);
		}
		rel3.close();
		out3.close();
		
		ProgramRel rel4 = (ProgramRel) ClassicProject.g().getTrgt("unsafeDowncast");
		rel4.load();
		Iterable<Pair<Register, jq_Type>> tuples4 = rel4.getAry2ValTuples();
		PrintWriter out4 = OutDirUtils.newPrintWriter("unsafeDowncastQueries_CFA2.txt");
		for (Pair<Register, jq_Type> tuple : tuples4) {
			out4.println(tuple.val0 + " " + tuple.val1);
		}
		rel4.close();
		out4.close();
		
		ClassicProject.g().runTask("allRacePairs-dlog");
		ClassicProject.g().runTask("datarace-cs-java");
		
		ProgramRel rel5 = (ProgramRel) ClassicProject.g().getTrgt("allRacePairs");
		rel5.load();
		Iterable<Pair<Quad, Quad>> tuples5 = rel5.getAry2ValTuples();
		PrintWriter out5 = OutDirUtils.newPrintWriter("allRaceQueries_CFA2.txt");
		for (Pair<Quad, Quad> tuple : tuples5) {
			out5.println(tuple.val0 + " " + tuple.val1);
		}
		rel5.close();
		out5.close();
		
		ProgramRel rel6 = (ProgramRel) ClassicProject.g().getTrgt("racePairs_cs");
		rel6.load();
		Iterable<Pair<Quad, Quad>> tuples6 = rel6.getAry2ValTuples();
		PrintWriter out6 = OutDirUtils.newPrintWriter("failedRaceQueries_CFA2.txt");
		for (Pair<Quad, Quad> tuple : tuples6) {
			out6.println(tuple.val0 + " " + tuple.val1);
		}
		rel6.close();
		out6.close();
		
		System.out.println("EXIT generateClientQueries");
		
		queryGenTimer.done();
		long inclusiveTime = queryGenTimer.getInclusiveTime();
		System.out.println("Total time for generating queries: "
				+ Timer.getTimeStr(inclusiveTime));
		
	}
}
