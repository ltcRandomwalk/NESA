package chord.analyses.compomustalias;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.TrioIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.tdbu.BottomUpAnalysis;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

/**
 * The hybrid analysis which combines topdown analysis and bottomup analysis for
 * mustalias analysis
 * 
 * @author xin
 * 
 */
@Chord(name = "compo-hybrid-mustalias-java", consumes = { "reachableFromM",
		"checkExcludedM" })
public class MustAliasHybridAnalysis extends JavaAnalysis {
	public MustAliasTopDownAnalysis td;
	public MustAliasBottomUpAnalysis bu;
	private PrePassAnalysis pre;
	private DomH domH;
	private DomI domI;
	private DomV domV;
	private DomF domF;
	private int tdLimit;
	private int buLimit;
	private int bupeLimit;
	private int trackedSiteNum;
	private Set<Quad> trackedSites = new ArraySet<Quad>();
	private boolean init;
	private boolean autoAdjustBU = false;
	private boolean jumpEmpty = false;
	private boolean DEBUG = false;
	private boolean statistics = false;
	private boolean buAllMethods;
	public static CIPAAnalysis cipa;
	public static ICICG cicg;
	protected String cipaName, cicgName;
	public final static int defTdLimit = 50;
	public final static int defBuLimit = 1;
	public final static int defBupeLimit = Integer.MAX_VALUE;
	public final static int defTrackedSites = Integer.MAX_VALUE;

	private SummaryHandler summHandler = new SummaryHandler();
	private boolean genSummaries = false;
	private boolean consumeSummaries = false;
	private String summaryDir = "";
	private String suffix = "";
	private ProgramRel relAppCallback;
	private Map<jq_Method, Set<Pair<Quad,jq_Method>>> reachedFromMIM;
	public Map<jq_Method, Set<Pair<Quad,jq_Method>>> savedReachedFromMIM;
	
	public MustAliasHybridAnalysis() {
		init = false;
	}

	public MustAliasHybridAnalysis(boolean isBaseline, boolean sumgen, boolean isRef) {
		init = false;
		if (isRef) {
			this.genSummaries = false;
			this.consumeSummaries = false;
			tdLimit = 10000;
		} else {
			if (isBaseline && sumgen) {
				// case when you want to dump ALL the summaries in a baseline run
				this.genSummaries = false;
				this.consumeSummaries = false;
			} else if (!isBaseline && sumgen){
				// This is the regular summary generation case (non-baseline run) where backward traversal of provenance is done 
				this.genSummaries = true;
				this.consumeSummaries = false;
				suffix = "sumgen";
			} else if (!isBaseline && !sumgen) {
				// This is the case when summaries need to be consumed.
				this.genSummaries = false;
				this.consumeSummaries = true;
				suffix = "sumuse";
			} else {
				this.genSummaries = false;
				this.consumeSummaries = false;
			}
		}
		init();
		if (genSummaries || consumeSummaries) initSummaryHandler();
		if (consumeSummaries) summHandler.loadSavedSEs();
		bu.summH = summHandler;
	}
	
	public void init() {
		if (init)
			return;
		init = true;
		tdLimit = Integer.getInteger("chord.mustalias.tdlimit", defTdLimit);
		buLimit = Integer.getInteger("chord.mustalias.bulimit", defBuLimit);
		bupeLimit = Integer.getInteger("chord.mustalias.bupelimit",
				defBupeLimit);
		trackedSiteNum = Integer.getInteger("chord.mustalias.trackedsites",
				defTrackedSites);
		statistics = Boolean.getBoolean("chord.mustalias.statistics");
		autoAdjustBU = Boolean.getBoolean("chord.mustalias.autoadjust");
		jumpEmpty = Boolean.getBoolean("chord.mustalias.jumpempty");
		DEBUG = Boolean.getBoolean("chord.mustalias.debug");
		String buAllms = System.getProperty("chord.mustalias.buallms", "false");
		if (buAllms.equals("false"))
			buAllMethods = false;
		else
			buAllMethods = true;

		BottomUpAnalysis.DEBUG = DEBUG;

		domH = (DomH) ClassicProject.g().getTrgt("H");
		ClassicProject.g().runTask(domH);
		domI = (DomI) ClassicProject.g().getTrgt("I");
		ClassicProject.g().runTask(domI);
		domV = (DomV) ClassicProject.g().getTrgt("V");
		ClassicProject.g().runTask(domV);
		domF = (DomF) ClassicProject.g().getTrgt("F");
		ClassicProject.g().runTask(domF);
		FieldBitSet.domF = domF;
		Variable.domF = domF;
		Variable.domV = domV;
		MustAliasBUEdge.domV = domV;

		fillTrackedSites();

		cipaName = System.getProperty("chord.mustalias.cipa", "cipa-java");
		cipa = (CIPAAnalysis) ClassicProject.g().getTask(cipaName);
		ClassicProject.g().runTask(cipa);

		cicgName = System.getProperty("chord.mustalias.cicg", "cicg-java");
		CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTask(cicgName);
		ClassicProject.g().runTask(cicgAnalysis);
        cicg = cicgAnalysis.getCallGraph();
		
        ITask reachTask = ClassicProject.g().getTask("compo-reachableFromM-dlog");
		ClassicProject.g().runTask(reachTask);
		ProgramRel relReachedFromMM = (ProgramRel) ClassicProject.g().getTrgt("reachableFromM");
		relReachedFromMM.load();
		Map<jq_Method, Set<jq_Method>> reachedFromMM = new HashMap<jq_Method, Set<jq_Method>>();
		Iterable<Pair<jq_Method, jq_Method>> tuples = relReachedFromMM.getAry2ValTuples();
		for (Pair<jq_Method, jq_Method> p : tuples) {
			Set<jq_Method> methods = reachedFromMM.get(p.val0);
			if (methods != null)
				methods.add(p.val1);
			else {
				methods = new ArraySet<jq_Method>();
				methods.add(p.val1);
				reachedFromMM.put(p.val0, methods);
			}
		}
		relReachedFromMM.close();
		
		ProgramRel relMIM = (ProgramRel) ClassicProject.g().getTrgt("MIM");
		relMIM.load();
		reachedFromMIM = new HashMap<jq_Method, Set<Pair<Quad,jq_Method>>>();
		TrioIterable<jq_Method,Quad,jq_Method> mimTuples = relMIM.getAry3ValTuples();
		for (Trio<jq_Method,Quad,jq_Method> t : mimTuples) {
			Set<Pair<Quad,jq_Method>> methods = reachedFromMIM.get(t.val0);
			if (methods == null) {
				methods = new ArraySet<Pair<Quad,jq_Method>>();
				reachedFromMIM.put(t.val0, methods);
			}
			methods.add(new Pair<Quad,jq_Method>(t.val1, t.val2));
		}
		relMIM.close();
		
		
		Set<jq_Method> rmsFrRoots = getRmsFrRoots(cicg.getRootsOrdered(),reachedFromMM);
			
		pre = new PrePassAnalysis(trackedSites,rmsFrRoots);
		pre.run();
		Set<jq_Method> noTDSEMs = pre.getNoFullSummsMethods();
		td = new MustAliasTopDownAnalysis(tdLimit, autoAdjustBU, jumpEmpty,
				buAllMethods, trackedSites,rmsFrRoots);
		td.init();
		bu = new MustAliasBottomUpAnalysis(td.getCallGraph(), buLimit,
				bupeLimit, reachedFromMM, noTDSEMs);
		td.setBU(bu);
	}

	private Set<jq_Method> getRmsFrRoots(Set<jq_Method> roots,Map<jq_Method,Set<jq_Method>> rmsMap){
		Set<jq_Method> ret = new HashSet<jq_Method>();
		ret.addAll(roots);
		for(jq_Method r: roots){
			Set<jq_Method> rms = rmsMap.get(r);
			if(rms!=null)
				ret.addAll(rms);
		}
		return ret;
	}
	
	private void fillTrackedSites() {
		String fileName = trackedSiteNum + "ta";
		File input = new File(fileName);
		ArraySet<Integer> trackedIdxes = new ArraySet<Integer>();
		ProgramRel relCheckExcludedT = (ProgramRel) ClassicProject.g().getTrgt(
				"checkExcludedT");
		ClassicProject.g().runTask(relCheckExcludedT);
		relCheckExcludedT.load();
		int numH = domH.getLastA() + 1;
		for (int hIdx = 1; hIdx < numH; hIdx++) {
			Quad q = (Quad) domH.get(hIdx);
			if (q.getOperator() instanceof New) {
				jq_Class c = q.getMethod().getDeclaringClass();
				String qs = q.toString();
				if(!(qs.contains("java.lang.StringBuilder")||qs.contains("java.lang.StringBuffer")))//well string operations are not interesting
				if (!relCheckExcludedT.contains(c)) {
					trackedIdxes.add(hIdx);
				}
			}
		}
		if (trackedIdxes.size() > trackedSiteNum) {
			ArraySet<Integer> oldtrackedIdxes = trackedIdxes;
			trackedIdxes = new ArraySet<Integer>();
			if (input.exists()) {
				try {
					Scanner sc = new Scanner(input);
					while (sc.hasNext()) {
						String line = sc.nextLine();
						if (!line.equals("")) {
							trackedIdxes.add(Integer.parseInt(line));
						}
					}
				} catch (FileNotFoundException e) {
				}
			} else {
				while (trackedIdxes.size() < trackedSiteNum) {
					int rand = (int) (Math.random() *oldtrackedIdxes.size()) ;
					trackedIdxes.add(oldtrackedIdxes.get(rand));
				}
				PrintWriter pw;
				try {
					pw = new PrintWriter(input);
					for (int i : trackedIdxes)
						pw.println(i);
					pw.flush();
					pw.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		for (int i : trackedIdxes) {
			Quad q = (Quad) domH.get(i);
	//		System.out.println("Tracking: " + q);
			trackedSites.add(q);
		}
		relCheckExcludedT.close();
	}

	@Override
	public void run() {
		//init();
		System.out.println("TD Limit: " + tdLimit);
		System.out.println("BU Limit: " + buLimit);
		System.out.println("BUPE Limit: " + bupeLimit);
		System.out.println("Auto adjustment: " + autoAdjustBU);
		System.out.println("Jump empty: " + jumpEmpty);
		System.out.println("Statistics: " + statistics);
		System.out.println("BU run on all methods: " + buAllMethods);
		System.out.println("Max number of tracked alloc sites: " + trackedSiteNum);
		td.run();
		
		if (statistics) {
			System.out.println("Times of BU has run: " + bu.getBUTimes());
			System.out.println("Times of BU has been invoked on a method: " + bu.getBUMethodTimes());
			System.out.println("Total BU worklist size : " + bu.getBUMethWLSize());
			System.out.println("Total BU worklist size App : " + bu.getBUMethWLSizeApp());
			System.out.println("Total BU worklist size Lib : " + bu.getBUMethWLSizeLib());
			System.out.println("Times BU summaries computed/recomputed : " + bu.getBUSummsComputed());
			System.out.println("NumAppMethods : " + bu.getNumAppMethods());
			System.out.println("NumAppMethodsTimes : " + bu.getNumAppMethodsTimes());
			System.out.println("NumLibMethods : " + bu.getNumLibMethods());
			System.out.println("NumLibMethodsTimes : " + bu.getNumLibMethodsTimes());
			System.out.println("Times of BUSE matches: " + bu.getBUMatch());
			System.out.println("Times of BUSE unmatches: " + bu.getBUUnmatch());
			System.out.println("Times of BUPE size explodes: "
					+ bu.getCaseExplode());
			System.out.println("Times of TDSE not ready: "
					+ bu.getTDSENotReady());
			System.out.println("Times of no match case: " + bu.getNoCase());
		}
		Map<jq_Method, ArraySet<Edge>> ses = td.getAllSEs();
		int allSeNum = 0;
		int seNum = 0;
		TreeMap<Integer,Set<jq_Method>> seCounts = new TreeMap<Integer,Set<jq_Method>>();
		for (Map.Entry<jq_Method, ArraySet<Edge>> entry : ses.entrySet()) {
			int entrySeNum = bu.countEffectiveTDSE(entry.getValue());
			if(DEBUG)
				System.out.println(entry.getKey() + ", se sz: " + entry.getValue().size()
						+ ", isBUAnalyzed: " + bu.isMethodAnalyzed(entry.getKey()) + ", effective_sz: " + entrySeNum);
			Set<jq_Method> mSet = seCounts.get(entrySeNum);
			if(mSet == null){
				mSet = new HashSet<jq_Method>();
				seCounts.put(entrySeNum, mSet);
			}
			mSet.add(entry.getKey());
			seNum += entrySeNum;
			allSeNum += entry.getValue().size();
		}
		System.out.println("Total TD SE numbers (effective): " + seNum);
		System.out.println("Total TD SE numbers (all): " + allSeNum);
		System.out.println("Total BU SE numbers: "+bu.getTotalBUSENumber());
		
		long timeInTDBU = td.getTimeInTDBU();
		long timeInBU = bu.getTimeInBU();
		long timeInTD = timeInTDBU - timeInBU;
		System.out.println("TDBU running time in nanoseconds: " + timeInTDBU); 
		System.out.println("Only TD running time in nanoseconds: " + timeInTD);
		System.out.println("Only TD running time: " + Timer.getTimeStr(timeInTD/1000000));
		System.out.println("Only BU running time in nanoseconds: " + timeInBU);
		System.out.println("Only BU running time: " + Timer.getTimeStr(timeInBU/1000000));
		System.out.println("Only BU method running time: " + Timer.getTimeStr(bu.getTimeInBUMethod()/1000000));
		
		/******
		PrintWriter scOut = OutDirUtils.newPrintWriter("seCounts.txt");
		for(Map.Entry<Integer, Set<jq_Method>> entry:seCounts.entrySet()){
			//System.out.println("#####SE number: "+entry.getKey()+", method number: "+entry.getValue().size()+"######");
			for(jq_Method m : entry.getValue()){
				//System.out.println(m);
				scOut.println(entry.getKey());
				}
			//System.out.println();
		}
		scOut.flush();
		scOut.close();
		if (td.checkNoTDSEsM())
			System.out.println("The prepass really works!");
		else
			System.out.println("Something wrong with the prepass!");
		//if (DEBUG)
			//bu.printSummaries(System.out);
		PrintWriter out = OutDirUtils.newPrintWriter("results.xml." + suffix);
		Set<Pair<Quad, Quad>> provedQueries = td.getProvedQueries();
		Set<Pair<Quad, Quad>> errQueries = td.getErrQueries();
		out.println("<results>");
		out.println("<proven num=\"" + provedQueries.size() + "\">");
		for (Pair<Quad, Quad> pq : provedQueries) {
			out.println("<query>");
			out.print("<i Iid=\"" + domI.indexOf(pq.val0) + "\">");
			out.print(pq.val0.toByteLocStr());
			out.println("</i>");
			out.print("<h Hid=\"" + domH.indexOf(pq.val1) + "\">");
			out.print(pq.val1.toByteLocStr());
			out.println("</h>");
			out.println("</query>");
		}
		out.println("</proven>");
		out.println("<err num=\"" + errQueries.size() + "\">");
		for (Pair<Quad, Quad> pq : errQueries) {
			out.println("<query>");
			out.print("<i Iid=\"" + domI.indexOf(pq.val0) + "\">");
			out.print(pq.val0.toByteLocStr());
			out.println("</i>");
			out.println("<h Hid=\"" + domH.indexOf(pq.val1) + "\">");
			out.print(pq.val1.toByteLocStr());
			out.print("</h>");
			out.println("</query>");
		}
		out.println("</err>");
		out.println("</results>");
		out.flush();
		out.close();
		**********/
		
		if (genSummaries) summHandler.dumpLibSEsFiltered();
	}
	
	private void initSummaryHandler() {
		if (genSummaries) savedReachedFromMIM = new HashMap<jq_Method, Set<Pair<Quad,jq_Method>>>();
		summaryDir = System.getProperty("chord.compomustalias.summaryDir");
		summHandler.summaryDir = summaryDir;
		summHandler.dumpToFile = true;
		summHandler.genSummaries = genSummaries;
		summHandler.consumeSummaries = consumeSummaries;
		summHandler.savedSummEdges = bu.savedSummEdges;
		summHandler.pathEdges = null;
		summHandler.summEdges = bu.summEdges;	
		summHandler.savedReachedFromMIM = savedReachedFromMIM;
		summHandler.reachedFromMIM = reachedFromMIM;
		summHandler.init();
		CallGraphCondition.IMReachableFromM = reachedFromMIM;
	}

}
