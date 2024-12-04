package chord.analyses.compomustalias;

import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.composba.FlowKind;
import chord.project.Chord;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

/*
 * This is an implementation of compositional MustAlias.
 * MustAlias analysis is performed on a set of training apps and the summaries learnt from them are stored.
 * When an application under test is to be analysed, the relevant stored summaries are reused to reduce redundant computation.
 *                                          
 * chord.compomustalias.summaryDir[default ""]  string representing a directory where summaries are stored/read-from.
 * chord.compomustalias.flow [default ""]  string that specifies flow in the compositional analysis
 *                                   baseline   : the baseline analysis will be run
 *                                   baselinesummary   : the baseline analysis will be run and all SEs will be dumped in summaryDir
 *                                   trainself  : The analysis will be run once on the app to collect and store summaries and the stored summaries
 *                                                will be reused on the same app in a different invocation of the analysis.
 *                                   traincross : The analysis will be run once on each of the specified apps and all their summaries will be stored
 *                                                as training data. All this training data will be applied to the application under test.
 *                                   usecross   : Use pre-existing training data on the application under test.
 *                                   
 * chord.compotypestate.trainapps [default""] : comma-separated list of apps to get training data from.
 */

@Chord(name = "compomustalias")
public class CompoMustAlias extends JavaAnalysis {
	List<ITask> tasks;
	Timer timerSG;
	Timer timerB;
	Timer timerSU;
	
	private MustAliasHybridAnalysis analysis0;
	private MustAliasHybridAnalysis analysis1;
	private MustAliasHybridAnalysis analysis2;
	
	private Map<jq_Method, ArraySet<Edge>> summEdges0;
	private Map<jq_Method, ArraySet<Edge>> summEdges1;
	private Map<jq_Method, ArraySet<MustAliasBUEdge>> savedSummEdges;
	private Map<jq_Method, Set<Pair<Quad,jq_Method>>> savedReachedFromMIM;
	
	public void run() {
		FlowKind flowKind = FlowKind.NOTHING;
		
		String s = System.getProperty("chord.compomustalias.flow");
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
		
		if (flowKind == FlowKind.BASELINE) {  // BASELINE RUN
			timerSG = new Timer("Baseline");
			timerSG.init();
			long hybridObjStart = System.nanoTime();
			analysis0 = new MustAliasHybridAnalysis(true, false, false);  // baseline
			long hybridObjEnd = System.nanoTime();
			long hybridObjInitTime = hybridObjEnd - hybridObjStart;
			System.out.println("Hybrid obj init time: " + hybridObjInitTime);
			long hybridRunStart = System.nanoTime();
			analysis0.run();
			long hybridRunEnd = System.nanoTime();
			long hybridRunTime = hybridRunEnd - hybridRunStart;
			System.out.println("Hybrid obj run time: " + hybridRunTime);
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CompoMustAlias: BASELINE TIME: "+ Timer.getTimeStr(timeSG));
			
			//Verifier ver = new Verifier();
			//ver.compareSEMapsBU(analysis0.bu.summEdges, analysis1.bu.summEdges);
		}
		
		else if (flowKind == FlowKind.TRAINSELF) {  // GENERATE SUMMARY and CONSUME SUMMARY for same app
			timerSG = new Timer("Summary Generation");
			timerSG.init();
			long hybridObjStart = System.nanoTime();
			analysis0 = new MustAliasHybridAnalysis(false /* not baseline */, true /* gen summaries */, false /* is ref */);
			long hybridObjEnd = System.nanoTime();
			long hybridObjInitTime = hybridObjEnd - hybridObjStart;
			System.out.println("Hybrid obj init time: " + hybridObjInitTime);
			long hybridRunStart = System.nanoTime();
			analysis0.run();
			long hybridRunEnd = System.nanoTime();
			long hybridRunTime = hybridRunEnd - hybridRunStart;
			System.out.println("Hybrid obj run time: " + hybridRunTime);
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CompoMustAlias: SUMMARY GENERATION TIME: "+ Timer.getTimeStr(timeSG));
			savedSummEdges = analysis0.bu.savedSummEdges;
			summEdges0 = analysis0.td.getSummEdges();
			savedReachedFromMIM = analysis0.savedReachedFromMIM;
			
			Verifier ver = new Verifier();
			//ver.dumpSavedSummaryEdges(savedSummEdges);
			
			timerSU = new Timer("Summary Use");
			timerSU.init();
			long hybridObjStart1 = System.nanoTime();
			analysis1 = new MustAliasHybridAnalysis(false /* not baseline */, false /* consume summaries */, false /* is ref */);
			long hybridObjEnd1 = System.nanoTime();
			long hybridObjInitTime1 = hybridObjEnd1 - hybridObjStart1;
			System.out.println("CompoMustAlias: REUSE SECOND INST: Hybrid obj init time: " + hybridObjInitTime1);
			//analysis1.bu.savedSummEdges = savedSummEdges;
			//analysis1.savedReachedFromMIM = savedReachedFromMIM;
			long hybridRunStart1 = System.nanoTime();
			analysis1.run();
			long hybridRunEnd1 = System.nanoTime();
			long hybridObjRunTime1 = hybridRunEnd1 - hybridRunStart1;
			System.out.println("CompoMustAlias: REUSE SECOND INST: Hybrid obj run time: " + hybridObjRunTime1);
			timerSU.done();
			long timeSU = timerSU.getInclusiveTime();
			System.out.println("CompoMustAlias: REUSE SECOND INST: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
			summEdges1 = analysis1.td.getSummEdges();
			
			ver.compareSEMaps(summEdges0, summEdges1, analysis0.bu.summEdges, analysis1.bu.summEdges);
		}
		else if (flowKind == FlowKind.USECROSS) { // Just CONSUME SUMMARY - can be used across apps. Baseline is run just for verification.
			
			timerSU = new Timer("Summary Use");
			timerSU.init();
			long hybridObjStart = System.nanoTime();
			analysis1 = new MustAliasHybridAnalysis(false /* is not baseline */, false /* consume summary */, false /* is ref */);
			long hybridObjEnd = System.nanoTime();
			long hybridObjInitTime = hybridObjEnd - hybridObjStart;
			System.out.println("Hybrid obj init time: " + hybridObjInitTime);
			long hybridRunStart = System.nanoTime();
			analysis1.run();
			long hybridRunEnd = System.nanoTime();
			long hybridRunTime = hybridRunEnd - hybridRunStart;
			System.out.println("Hybrid obj run time: " + hybridRunTime);
			timerSU.done();
			long timeSU = timerSU.getInclusiveTime();
			
			System.out.println("CompoMustAlias: SUMMARY REUSE TIME: "+ Timer.getTimeStr(timeSU));
			summEdges1 = analysis1.td.getSummEdges();
			
			timerSG = new Timer("Baseline");
			timerSG.init();
			long hybridObjStart1 = System.nanoTime();
			analysis0 = new MustAliasHybridAnalysis(true, false, false);   // baseline
			long hybridObjEnd1 = System.nanoTime();
			long hybridObjInitTime1 = hybridObjEnd1 - hybridObjStart1;
			System.out.println("CompoMustAlias: BASELINE SECOND INST: Hybrid obj init time: " + hybridObjInitTime1);
			long hybridRunStart1 = System.nanoTime();
			analysis0.run();
			long hybridRunEnd1 = System.nanoTime();
			long hybridObjRunTime1 = hybridRunEnd1 - hybridRunStart1;
			System.out.println("CompoMustAlias: BASELINE SECOND INST: Hybrid obj run time: " + hybridObjRunTime1);
			timerSG.done();
			long timeSG = timerSG.getInclusiveTime();
			System.out.println("CompoMustAlias: BASELINE SECOND INST:  BASELINE TIME: "+ Timer.getTimeStr(timeSG));
			summEdges0 = analysis0.td.getSummEdges();
			
			Verifier ver = new Verifier();
			ver.compareSEMaps(summEdges0, summEdges1, analysis0.bu.summEdges, analysis1.bu.summEdges);
		}
	}
}

