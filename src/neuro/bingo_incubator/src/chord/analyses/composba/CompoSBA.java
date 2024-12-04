package chord.analyses.composba;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.DomC;
import chord.analyses.composba.alloc.AllocEnvCFAAnalysis;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.Utils;

/*
 * This is an implementation of compositional SBA.
 * SBA analysis is performed on a set of training apps and the summaries learnt from them are stored.
 * When an application under test is to be analysed, the relevant stored summaries are reused to reduce redundant computation.
 *                                          
 * chord.composba.summaryDir[default ""]  string representing a directory where summaries are stored/read-from.
 * chord.composba.flow [default ""]  string that specifies flow in the compositional analysis
 *                                   baseline   : the baseline SBA analysis will be run
 *                                   baselinesummary   : the baseline SBA analysis will be run and all SEs will be dumped in summaryDir
 *                                   trainself  : SBA analysis will be run once on the app to collect and store summaries and the stored summaries
 *                                                will be reused on the same app in a different invocation of the analysis.
 *                                   traincross : SBA analysis will be run once on each of the specified apps and all their summaries will be stored
 *                                                as training data. All this training data will be applied to the application under test.
 *                                   usecross   : Use pre-existing training data on the application under test.
 *                                   verify     : Do verification (default: false).
 *                                   
 * chord.composba.trainapps [default""] : comma-separated list of apps to get training data from.
 * chord.composba.simulateFwd [default false] simulate fwd traversal which will track the heap accesses used to update ALL local vars.
 * chord.composba.simulateSuperPerf [default false] simulate the super perfect scenario
 */

@Chord(name = "composba", 
    consumes = { "H", "M", "FH", "HFH", "IHM", "THfilter", "rootM", "NonTerminatingM",
    "conNewInstIH", "conNewInstIM", "objNewInstIH", "objNewInstIM", "aryNewInstIH", "MI", "filterM",
    "trackedAlloc", "NonTerminatingM", "THfilter", "MIM"},
    produces = { "C", "CH", "CVC", "FC", "CFC", "CICM", "rootCM", "reachableCM", "reachableCI" },
    namesOfTypes = { "C" },
    types = { DomC.class }
)
public class CompoSBA extends JavaAnalysis {
	List<ITask> tasks;
	Timer timerSG;
	Timer timerSU;
	Timer timerSG1;
	Timer timerSU1;

	
	private AllocEnvCFAAnalysis analysis0;
	private AllocEnvCFAAnalysis analysis1;
	
	private Map<jq_Method, ArraySet<BitEdge<Quad>>> summEdges0;
	private Map<jq_Method, ArraySet<BitEdge<Quad>>> summEdges1;
	
	private boolean verify;
	private boolean useLibPrefix;
	
	public void run() {
		FlowKind flowKind = FlowKind.NOTHING;
		/****
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("THfilter-dlog"));
		tasks.add(ClassicProject.g().getTask("NonTerminatingM"));
		tasks.add(ClassicProject.g().getTask("cipa-java"));
		tasks.add(ClassicProject.g().getTask("cicg-java"));
		tasks.add(ClassicProject.g().getTask("trackedAlloc"));
		tasks.add(ClassicProject.g().getTask("filterM"));
		
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		****/
		
		String s = System.getProperty("chord.composba.flow");
		if (s.equals("baseline"))
            flowKind = FlowKind.BASELINE;
		else if (s.equals("baselinesummary"))
            flowKind = FlowKind.BASELINESUMMARY;
        else if (s.equals("trainself"))
            flowKind = FlowKind.TRAINSELF;
        else if (s.equals("traincross"))
            flowKind = FlowKind.TRAINCROSS;
        else if (s.equals("usecross"))
            flowKind = FlowKind.USECROSS;
        else
            throw new RuntimeException("Bad value for flow property: " + s);
		
		verify = Utils.buildBoolProperty("chord.composba.verify", false);
		useLibPrefix = Utils.buildBoolProperty("chord.composba.useLibPrefix", true);
		
		if (flowKind == FlowKind.BASELINE) {  // BASELINE RUN
			timerSG = new Timer("Baseline");
			timerSG.init();
			long initStart = System.nanoTime();
			analysis0 = new AllocEnvCFAAnalysis(true, false);  // baseline
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("AllocEnvCFA: INIT TIME: "+ initTime);
			System.out.println("AllocEnvCFA: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("AllocEnvCFA: BASELINE TIME: "+ Timer.getTimeStr(timeSG));
		}
		
		else if (flowKind == FlowKind.BASELINESUMMARY) {  // BASELINE RUN WITH FULL SUMMARY DUMP
			timerSG = new Timer("BaselineSummary");
			timerSG.init();
			analysis0 = new AllocEnvCFAAnalysis(true /* is baseline */, true /* dump summaries */);
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("AllocEnvCFA: BASELINE TIME WITH FULL SUMMARY DUMP: "+ Timer.getTimeStr(timeSG));
		}
		
		else if (flowKind == FlowKind.TRAINSELF) {  // GENERATE SUMMARY and CONSUME SUMMARY for same app
			timerSG = new Timer("Summary Generation");
			timerSG.init();
			long initStart = System.nanoTime();
			analysis0 = new AllocEnvCFAAnalysis(false /* is not baseline */, true /* generate summary */);
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("AllocEnvCFA: INIT TIME: "+ initTime);
			System.out.println("AllocEnvCFA: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("AllocEnvCFA: SUMMARY GENERATION TIME: "+ Timer.getTimeStr(timeSG));
			
			if (verify) {
				summEdges0 = analysis0.summEdges;
				timerSU = new Timer("Summary Use");
				timerSU.init();
				analysis1 = new AllocEnvCFAAnalysis(false /* is not baseline */, false /* consume summary */);
				analysis1.run();
				timerSU.done();
				long timeSU = timerSU.getInclusiveTime();
				System.out.println("AllocEnvCFA: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
				summEdges1 = analysis1.summEdges;
				
				Verifier ver = new Verifier(useLibPrefix);
				ver.compareSEMaps(summEdges0, summEdges1);
			}
		}
		else if (flowKind == FlowKind.USECROSS) { // Just CONSUME SUMMARY - can be used across apps. Baseline is run just for verification.	
			timerSU = new Timer("Summary Use");
			timerSU.init();
			long initStart = System.nanoTime();
			analysis1 = new AllocEnvCFAAnalysis(false /* is not baseline */, false /* consume summary */);
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("AllocEnvCFA: INIT TIME: "+ initTime);
			System.out.println("AllocEnvCFA: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis1.run();
			timerSU.done();
			long timeSU = timerSU.getInclusiveTime();
			System.out.println("AllocEnvCFA: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
			
			if (verify) {
				summEdges1 = analysis1.summEdges;
				
				analysis0 = new AllocEnvCFAAnalysis(true, false);   // baseline
				analysis0.run();
				summEdges0 = analysis0.summEdges;
				
				Verifier ver = new Verifier(useLibPrefix);
				ver.compareSEMaps(summEdges0, summEdges1);
			}
		}
	}
}

