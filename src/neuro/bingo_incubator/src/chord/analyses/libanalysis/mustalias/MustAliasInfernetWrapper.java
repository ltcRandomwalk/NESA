package chord.analyses.libanalysis.mustalias;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import chord.analyses.absmin.Abstraction;
import chord.analyses.absmin.GenericQueryFactory;
import chord.analyses.absmin.Query;
import chord.analyses.absmin.QueryFactory;
import chord.analyses.absmin.ScenarioKind;
import chord.analyses.alloc.DomH;
import chord.analyses.infernet.InfernetWrapper;
import chord.analyses.invk.DomI;
import chord.analyses.libanalysis.MethodAbstractionFactory;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Project;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.Scenario;
import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

@Chord(name = "mustAliasInfernetWrapper-java",
produces = { "nodePairs" },
namesOfTypes = { "nodePairs" },
types = { ArrayList.class }
)
public class MustAliasInfernetWrapper extends JavaAnalysis implements InfernetWrapper {

	MustAliasLibAnalysis mustAliasLibAnalysis;
	GenericQueryFactory qFactory;
	List<jq_Method> trackedM;
	MethodAbstractionFactory mFactory;
	Set<Abstraction> mAbs;
	boolean init = false;
	DomM domM;
	DomI domI;
	DomH domH;
	HashSet<String> trackedQueries;
	boolean DEBUG = false;
	
	@Override
	public void run(){
		getInitializationData();
	}
	
	private void init(){
		//mustAliasLibAnalysis = new MustAliasLibAnalysis();
		mustAliasLibAnalysis = (MustAliasLibAnalysis) ClassicProject.g().getTask("mustaliaslibanalysis");
		mustAliasLibAnalysis.init();
		qFactory = mustAliasLibAnalysis.getQueryFactory();
		mFactory = mustAliasLibAnalysis.getMethodAbstractionFactory();
		mAbs = new HashSet<Abstraction>();
		trackedQueries = new HashSet<String>();
		
		{
			trackedM = new ArrayList<jq_Method>();
			ClassicProject.g().runTask("trackedM-dlog");
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("trackedM");
			rel.load();
			Iterable<jq_Method> result = rel.getAry1ValTuples();
			for (jq_Method m : result)
				trackedM.add(m);
			rel.close();
		}
		
		domM = (DomM) ClassicProject.g().getTask("M");
		if(!ClassicProject.g().isTaskDone(domM)) ClassicProject.g().runTask(domM);
		domI = (DomI) ClassicProject.g().getTask("I");
		if(!ClassicProject.g().isTaskDone("I")) ClassicProject.g().runTask(domI);
		domH = (DomH) ClassicProject.g().getTask("H");
		if(!ClassicProject.g().isTaskDone("H")) ClassicProject.g().runTask(domH);
		
		getTrackedQueries();
		
		init = true;
	}
	
	/**
	 * Get lib method calls along all paths for all tracked queries
	 */
	public String getInitializationData(){
		if(!init)
			this.init();
		
		ArrayList<Pair<Inst,Inst>> nodePairList = (ArrayList<Pair<Inst,Inst>>) ClassicProject.g().getTrgt("nodePairs");
		for(String q : trackedQueries){
			nodePairList.add(decodeMustAliasQuery(q));
		}
		ClassicProject.g().setTrgtDone("nodePairs");
		
		ClassicProject.g().runTask("pathGen-java");
		HashMap<Pair<Inst,Inst>, List<Set<jq_Method>>> nodePairToPathMap = (HashMap<Pair<Inst,Inst>, List<Set<jq_Method>>>) 
				ClassicProject.g().getTrgt("nodePairToPath");
		
		StringBuilder retStr = new StringBuilder();
		Set<Pair<Inst,Inst>> allQueries = nodePairToPathMap.keySet();
		for(Pair<Inst,Inst> query: allQueries){
			retStr.append(qFactory.encode(new Object[] { query.val1, query.val0 }) + "#");
			List<Set<jq_Method>> allPaths = nodePairToPathMap.get(query);
			
			for(Set<jq_Method> path : allPaths){
				for(jq_Method m : path){
					retStr.append(domM.indexOf(m));
					retStr.append(',');
				}
				if(!path.isEmpty()){
					retStr.deleteCharAt(retStr.length() - 1);
					retStr.append("#");
				}
			}

			retStr.deleteCharAt(retStr.length() - 1);
			retStr.append("$");
		}
		
		if(!allQueries.isEmpty())
			retStr.deleteCharAt(retStr.length() - 1);
		
		if(DEBUG)
			System.out.println("INIT Data requested by Infernet:\n" + retStr.toString());
		
		return retStr.toString();
	}
	
	public String getUpdateData(String bestMethodList){	
		if(!init)
			this.init();
		
		String[] methodIDs = Utils.split(bestMethodList, ",", false, true, 0);
		Set<Integer> methodIDList = new HashSet<Integer>();
		for(String mID : methodIDs){
			methodIDList.add(Integer.parseInt(mID));
		}
		
		mAbs.clear();
		for(jq_Method m : trackedM){
			Abstraction a  = mFactory.create(m);
			if(methodIDList.contains(domM.indexOf(m))){
				a.maxRefine();
			}else{
				a.minRefine();
			}
			mAbs.add(a);
		}
		
		String sepMin = mustAliasLibAnalysis.getSepMinor();
		String sepMaj = mustAliasLibAnalysis.getSepMajor();
		
		Scenario currSIn = new Scenario(ScenarioKind.COARSEN.toString(),
				Abstraction.concatAbstractions(mustAliasLibAnalysis.encodeX(mAbs),sepMin),
				Query.concatQueries(trackedQueries.toArray(new String[0]),sepMin), sepMaj);
		Scenario currSOut = new Scenario(mustAliasLibAnalysis.apply(currSIn.toString()),sepMaj);
		HashSet<String> currY = new HashSet<String>(Arrays.asList(Query.splitQueries(currSOut.getOut(),sepMin)));
		
		StringBuilder retStr = new StringBuilder();
		for(String q : currY){
			retStr.append(q);
			retStr.append("#");
		}
		if(!currY.isEmpty())
			retStr.deleteCharAt(retStr.length() - 1);
		
		return retStr.toString();
	}
	
	// Dirty Hack
	public Pair<Inst,Inst> decodeMustAliasQuery(String q){
		
		if(!ClassicProject.g().isTaskDone("I")){
			domI = (DomI) ClassicProject.g().getTask("I");
			ClassicProject.g().runTask(domI);
		}
		if(!ClassicProject.g().isTaskDone("H")){
			domH = (DomH) ClassicProject.g().getTask("H");
			ClassicProject.g().runTask(domH);
		}
		String[] vals = q.split(",");
		Quad startInst = (Quad) domH.get(Integer.parseInt(vals[1]));
		Inst endInst = (Inst) domI.get(Integer.parseInt(vals[0]));
		return new Pair<Inst, Inst>(startInst, endInst);
	}
	
	public void getTrackedQueries(){
		String sepMin = mustAliasLibAnalysis.getSepMinor();
		String sepMaj = mustAliasLibAnalysis.getSepMajor();

		mAbs.clear();
		for(jq_Method m : trackedM){
			Abstraction a = mFactory.create(m);
			a.maxRefine();
			mAbs.add(a);
		}
		
		Scenario topSIn = new Scenario(ScenarioKind.COARSEN.toString(), Abstraction.concatAbstractions(mustAliasLibAnalysis.encodeX(mAbs),sepMin), "", sepMaj);
		Scenario topSOut = new Scenario(mustAliasLibAnalysis.apply(topSIn.toString()),sepMaj);
		HashSet<String> topY = new HashSet<String>(Arrays.asList(Query.splitQueries(topSOut.getOut(),sepMin)));
		
		for(Abstraction a : mAbs)
			a.minRefine();

		Scenario botSIn = new Scenario(ScenarioKind.COARSEN.toString(), Abstraction.concatAbstractions(mustAliasLibAnalysis.encodeX(mAbs),sepMin), "", sepMaj);
		Scenario botSOut = new Scenario(mustAliasLibAnalysis.apply(botSIn.toString()),sepMaj);
		HashSet<String> botY = new HashSet<String>(Arrays.asList(Query.splitQueries(botSOut.getOut(),sepMin)));


		// Keep only queries that bottom was unable to prove but top was able to prove
		for (String y: botY) {
			if (!topY.contains(y)) { // Unproven by bottom, proven by top
				trackedQueries.add(y);
			}
		}

		boolean error = false;
		for (String y : topY) {
			if (!botY.contains(y)) {
				System.out.println("Query unproven by top but proven by bottom: " + y);
				error = true;
			}
		}
		if (error) {
			System.out.println("Quitting (see above queries).");
			System.exit(1);
		}

		doneTrackedQueries(trackedQueries);
	}
	
	public void doneTrackedQueries(Set<String> trackedY) {
		// note: topY and botY cleared by abstraction minimizer after call to doneTrackedQueries
		// hence print results here.

		Execution X = new Execution(getName());
		PrintWriter out;

		out = Utils.openOut(X.path("TrackedQueries.txt"));
		for (String query : trackedY) {
			Query q = qFactory.create(query);
			out.println(q.toString());
		}
		out.close();

	}			
}
