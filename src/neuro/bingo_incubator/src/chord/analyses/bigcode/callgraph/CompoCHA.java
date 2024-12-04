package chord.analyses.bigcode.callgraph;

import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.DomC;
import chord.project.Chord;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.Utils;

/*
 * This is an implementation of compositional CHA.
 * CHA analysis is performed on a set of training apps and the summaries learnt from them are stored.
 * When an application under test is to be analysed, the relevant stored summaries are reused to reduce redundant computation.
 *                                          
 * chord.compoCHA.summaryDir[default ""]  string representing a directory where summaries are stored/read-from.
 * chord.compoCHA.flow [default ""]  string that specifies flow in the compositional analysis
 *                                   baseline   : the baseline SBA analysis will be run
 *                                   baselinesummary   : the baseline SBA analysis will be run and all SEs will be dumped in summaryDir
 *                                   trainself  : SBA analysis will be run once on the app to collect and store summaries and the stored summaries
 *                                                will be reused on the same app in a different invocation of the analysis.
 *                                   traincross : SBA analysis will be run once on each of the specified apps and all their summaries will be stored
 *                                                as training data. All this training data will be applied to the application under test.
 *                                   usecross   : Use pre-existing training data on the application under test.
 *                                   verify     : Do verification (default: false).
 *                                   
 * chord.compoCHA.trainapps [default""] : comma-separated list of apps to get training data from.
 * chord.compoCHA.simulateSuperPerf [default false] simulate the super perfect scenario
 * chord.compoCHA.verify
 */

@Chord(name = "compoCHA", 
    consumes = {"T", "M", "I", "MI"}
)
public class CompoCHA extends JavaAnalysis {
	List<ITask> tasks;
	Timer timerSG;
	Timer timerSU;
	Timer timerSG1;
	Timer timerSU1;

	
	private CHAAnalysis analysis0;
	private CHAAnalysis analysis1;
	
	private boolean verify;
	
	public void run() {
		FlowKind flowKind = FlowKind.NOTHING;
		
		String s = System.getProperty("chord.compoCHA.flow");
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
		
		verify = Utils.buildBoolProperty("chord.compoCHA.verify", false);
		
		if (flowKind == FlowKind.BASELINE) {  // BASELINE RUN
			timerSG = new Timer("Baseline");
			timerSG.init();
			long initStart = System.nanoTime();
			analysis0 = new CHAAnalysis(true, false);  // baseline
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("CHAAnalysis: INIT TIME: "+ initTime);
			System.out.println("CHAAnalysis: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CHAAnalysis: BASELINE TIME: "+ Timer.getTimeStr(timeSG));
		}
		
		else if (flowKind == FlowKind.BASELINESUMMARY) {  // BASELINE RUN WITH FULL SUMMARY DUMP
			timerSG = new Timer("BaselineSummary");
			timerSG.init();
			analysis0 = new CHAAnalysis(true /* is baseline */, true /* dump summaries */);
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CHAAnalysis: BASELINE TIME WITH FULL SUMMARY DUMP: "+ Timer.getTimeStr(timeSG));
		}
		
		else if (flowKind == FlowKind.TRAINSELF) {  // GENERATE SUMMARY and CONSUME SUMMARY for same app
			timerSG = new Timer("Summary Generation");
			timerSG.init();
			long initStart = System.nanoTime();
			analysis0 = new CHAAnalysis(false /* is not baseline */, true /* generate summary */);
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("CHAAnalysis: INIT TIME: "+ initTime);
			System.out.println("CHAAnalysis: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis0.run();
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CHAAnalysis: SUMMARY GENERATION TIME: "+ Timer.getTimeStr(timeSG));
			
			if (verify) {
				timerSU = new Timer("Summary Use");
				timerSU.init();
				analysis1 = new CHAAnalysis(false /* is not baseline */, false /* consume summary */);
				analysis1.run();
				timerSU.done();
				long timeSU = timerSU.getInclusiveTime();
				System.out.println("CHAAnalysis: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
				
				VerifierCHA ver = new VerifierCHA();
				ver.verify(analysis0, analysis1);
			}
		}
		else if (flowKind == FlowKind.USECROSS) { // Just CONSUME SUMMARY - can be used across apps. Baseline is run just for verification.	
			timerSU = new Timer("Summary Use");
			timerSU.init();
			long initStart = System.nanoTime();
			analysis1 = new CHAAnalysis(false /* is not baseline */, false /* consume summary */);
			long initEnd = System.nanoTime();
			long initTime = initEnd - initStart;
			System.out.println("CHAAnalysis: INIT TIME: "+ initTime);
			System.out.println("CHAAnalysis: Readable INIT TIME: "+ Timer.getTimeStr(initTime/1000000));
			analysis1.run();
			timerSU.done();
			long timeSU = timerSU.getInclusiveTime();
			System.out.println("CHAAnalysis: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
			
			if (verify) {
				analysis0 = new CHAAnalysis(true, false);   // baseline
				analysis0.run();
				
				VerifierCHA ver = new VerifierCHA();
				ver.verify(analysis0, analysis1);
			}
		}
	}
}

