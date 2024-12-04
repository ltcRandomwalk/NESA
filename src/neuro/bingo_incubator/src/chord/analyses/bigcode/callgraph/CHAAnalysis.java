package chord.analyses.bigcode.callgraph;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Class;
import joeq.Class.jq_Array;
import joeq.Class.jq_Primitive;
import joeq.Class.jq_Reference;
import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Main.HostedVM;
import joeq.UTF.Utf8;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.program.ClassHierarchy;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.IndexSet;
import chord.util.Timer;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

/**
 * Class Hierarchy Analysis (CHA) 
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name = "cha-java",
consumes = {"T", "M", "I", "MI"}
)
public class CHAAnalysis extends JavaAnalysis {
    private static final String MAIN_CLASS_NOT_DEFINED =
        "ERROR: Property chord.main.class must be set to specify the main class of program to be analyzed.";
    private static final String MAIN_METHOD_NOT_FOUND =
        "ERROR: Could not find main class '%s' or main method in that class.";

    public static final boolean DEBUG = false;

    public Set<jq_Reference> classes;

    // classes whose clinit and super class/interface clinits have been processed
    private Set<jq_Class> classesVisitedForClinit;

    // methods deemed reachable so far
    public Set<jq_Method> methods;
    
    // Set of non-abstract methods deemed reachable so far.
    public Set<jq_Method> reachableMethods;
    
    // Set of all root methods discovered so far.
    public Set<jq_Method> rootMethods;
    
 // callgraph constructed so far in current iteration
    public Map<Quad, Set<jq_Method>> callGraph;

    // worklist for methods that have been seen but whose cfg isn't processed yet
    private List<jq_Method> methodWorklist;
    
    
    private Map<jq_Reference, Set<jq_Reference>> subTypes;
    private TIntObjectHashMap<BitSet> subTypesBitset;

    private jq_Class javaLangObject;

    private final ClassHierarchy ch;
    
    // Input properties
	private SummaryHandler summHandler;
	private String summaryDir = "";
	private boolean genSummaries = false;
	private boolean consumeSummaries = false;
	private boolean dumpToFile = false;
	private boolean degrade = false;
	private boolean stats = false;
	private boolean simulateSuperPerf = false;
	private boolean trackCG = false;
	private boolean partialSummaries = false;
	private int elementsProcessed = 0;
	
	private boolean isInit = false;
	
	public Map<jq_Method, ArraySet<Summary>> savedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	public Map<Summary, ArraySet<jq_Method>> finalSummaries = new HashMap<Summary, ArraySet<jq_Method>>();
	public Map<jq_Method, ArraySet<Summary>> loadedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	public Map<jq_Method, ArraySet<Summary>> usedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	public Set<jq_Method> surfaceMethods = new HashSet<jq_Method>();
	public Set<jq_Class> classesToBeReloaded = new HashSet<jq_Class>();
	private Map<jq_Method, Set<Quad>> MIMap = new HashMap<jq_Method, Set<Quad>>();
	
	DomT domT;
	DomM domM;
	DomI domI;
	
	private int usedSummariesCnt = 0;
	private Set<String> ignoreMethods;
	
	private Set<String> classesNotToBeReloaded;
    
    public CHAAnalysis() {
        ch = new ClassHierarchy();
    }
	
	public CHAAnalysis(boolean isBaseline, boolean sumgen) {
		ch = new ClassHierarchy();
		this.genSummaries = sumgen;
		this.consumeSummaries = (!isBaseline && !sumgen);
		this.dumpToFile = !(isBaseline && !sumgen);
		init();
		System.out.println("Initialization done");
	}
	
	private void init() {
		summaryDir = System.getProperty("chord.compoCHA.summaryDir");
		System.out.println("summaryDir: " + summaryDir);
		degrade = Utils.buildBoolProperty("chord.compoCHA.degrade", false);
		System.out.println("degrade: " + degrade);
		if (degrade) assert(consumeSummaries && !genSummaries);
		stats = Utils.buildBoolProperty("chord.compoCHA.stats", false);
		System.out.println("stats: " + stats);
		trackCG = Utils.buildBoolProperty("chord.compoCHA.trackCG", false);
		System.out.println("trackCG: " + trackCG);
		partialSummaries = Utils.buildBoolProperty("chord.compoCHA.partialSummaries", false);
		System.out.println("partialSummaries: " + partialSummaries);
		
		
		domT = (DomT) ClassicProject.g().getTrgt("T");
		domM = (DomM) ClassicProject.g().getTrgt("M");
		domI = (DomI) ClassicProject.g().getTrgt("I");
		
		classesNotToBeReloaded = new HashSet<String>();
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangObject().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangClass().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangString().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangSystem().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangThrowable().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getAddressArray().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangError().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangRuntimeException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangNullPointerException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangIndexOutOfBoundsException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangArrayIndexOutOfBoundsException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangArrayStoreException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangNegativeArraySizeException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangArithmeticException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangIllegalMonitorStateException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangClassCastException().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangClassLoader().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangReflectField().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangReflectMethod().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangReflectConstructor().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangThread().getName());
		classesNotToBeReloaded.add(PrimordialClassLoader.getJavaLangRefFinalizer().getName());
		
		ignoreMethods = new HashSet<String>();
		ignoreMethods.add("<init>:()V@java.lang.Object");
		ignoreMethods.add("clone:()Ljava/lang/Object;@java.lang.Object");
		ignoreMethods.add("equals:(Ljava/lang/Object;)Z@java.lang.Object");
		ignoreMethods.add("getClass:()Ljava/lang/Class;@java.lang.Object");
		ignoreMethods.add("hashCode:()I@java.lang.Object");
		ignoreMethods.add("notify:()V@java.lang.Object");
		ignoreMethods.add("notifyAll:()V@java.lang.Object");
		ignoreMethods.add("registerNatives:()V@java.lang.Object");
		ignoreMethods.add("toString:()Ljava/lang/String;@java.lang.Object");
		ignoreMethods.add("wait:()V@java.lang.Object");
		ignoreMethods.add("wait:(J)V@java.lang.Object");
		
		subTypes = new HashMap<jq_Reference, Set<jq_Reference>>();
		Set<String> classes = ch.allClassNamesInPath();
		for (String superName : classes) {
			jq_Reference superT = (jq_Reference) jq_Type.parseType(superName);
			Set<String> subNames = ch.getConcreteSubclasses(superName);
			Set<jq_Reference> subT = subTypes.get(superT);
			if (subT == null) {
				subT = new ArraySet<jq_Reference>();
				subTypes.put(superT, subT);
			}
			for (String subName : subNames) {
				jq_Reference d = (jq_Reference) jq_Type.parseType(subName);
				subT.add(d);
            }
		}
		
		subTypesBitset = new TIntObjectHashMap<BitSet>();
		if (consumeSummaries) {
			for (String superName : classes) {
				jq_Reference superT = (jq_Reference) jq_Type.parseType(superName);
				Set<String> subNames = ch.getConcreteSubclasses(superName);
				int superTNdx = domT.indexOf(superT);
				BitSet subT = null;
				if (!subTypesBitset.containsKey(superTNdx)) {
					subT = new BitSet();
					subTypesBitset.put(superTNdx, subT);
				}
				subT = subTypesBitset.get(superTNdx);
				for (String subName : subNames) {
					jq_Reference d = (jq_Reference) jq_Type.parseType(subName);
					subT.set(domT.indexOf(d));
				}
			}
		}
		
		MIMap = new HashMap<jq_Method, Set<Quad>>();
		if (!partialSummaries && genSummaries) {
			ProgramRel relMI = (ProgramRel) ClassicProject.g().getTrgt("MI");
			relMI.load();

			Iterable<Pair<jq_Method, Quad>> tuples = relMI.getAry2ValTuples();
			for (Pair<jq_Method, Quad> t : tuples){
				Set<Quad> quads = MIMap.get(t.val0);
				if (quads == null) {
					quads = new HashSet<Quad>();
					MIMap.put(t.val0, quads);
				}
				quads.add(t.val1);
			}
			relMI.close();
		}
		
		initSummaryHandler();
		isInit = true;
	}
	
	private void initSummaryHandler() {
		summHandler = new SummaryHandler();
		summHandler.summaryDir = summaryDir;
		summHandler.genSummaries = genSummaries;
		summHandler.consumeSummaries = consumeSummaries;
		summHandler.dumpToFile = dumpToFile;
		summHandler.savedSummaries = finalSummaries;
		summHandler.loadedSummaries = loadedSummaries;
		
		simulateSuperPerf = Utils.buildBoolProperty("chord.compoCHA.simulateSuperPerf", false);
		System.out.println("simulateSuperPerf: " + simulateSuperPerf);
		summHandler.simulateSuperPerf = simulateSuperPerf;
		boolean superPerfIgnoreAppCallbk = Utils.buildBoolProperty("chord.compoCHA.superPerfIgnoreAppCallbk", false);
		assert !superPerfIgnoreAppCallbk : "SuperPerfIgnoreAppCallbk not implemented for RTA";
		System.out.println("superPerfIgnoreAppCallbk: " + superPerfIgnoreAppCallbk);
		summHandler.superPerfIgnoreAppCallbk = superPerfIgnoreAppCallbk;
		boolean useLibPrefix = Utils.buildBoolProperty("chord.compoCHA.useLibPrefix", true);
		System.out.println("useLibPrefix: " + useLibPrefix);
		summHandler.useLibPrefix = useLibPrefix;
		
		summHandler.init();
		
		if (consumeSummaries) {
			loadedSummaries.clear();
        	summHandler.loadSavedSEs();
		}
	}

	@Override
	public void run() {
		if (!isInit) init();
		
		int degradePassNum = 0;
		do {
                usedSummaries.clear();
		System.out.println("ENTER: CHA");
		Timer timer = new Timer();
		timer.init();
        Timer chatimer = new Timer();
        chatimer.init();
        long chaStart = System.nanoTime();
        
		classes = new ArraySet<jq_Reference>();
		classesVisitedForClinit = new HashSet<jq_Class>();
		methods = new HashSet<jq_Method>();
		methodWorklist = new ArrayList<jq_Method>();
        reachableMethods = new HashSet<jq_Method>();
        rootMethods = new HashSet<jq_Method>();
        callGraph = new HashMap<Quad, Set<jq_Method>>();
        surfaceMethods.clear();
        classesToBeReloaded.clear();

//		HostedVM.initialize();
		javaLangObject = PrimordialClassLoader.getJavaLangObject();
		String mainClassName = Config.mainClassName;
		if (mainClassName == null)
			Messages.fatal(MAIN_CLASS_NOT_DEFINED);
		jq_Class mainClass = (jq_Class) jq_Type.parseType(mainClassName);
		prepareClass(mainClass);
		jq_NameAndDesc nd = new jq_NameAndDesc("main", "([Ljava/lang/String;)V");
		jq_Method mainMethod = (jq_Method) mainClass.getDeclaredMember(nd);
		if (mainMethod == null)
			Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
		visitClinits(mainClass);
		visitMethod(mainMethod);
		if (genSummaries && !partialSummaries) rootMethods.add(mainMethod);
		
		while (!methodWorklist.isEmpty()) {
			jq_Method m = methodWorklist.remove(methodWorklist.size() - 1);
		//	System.out.println("Processing method: " + m.toString());
			if (consumeSummaries) {
	    		boolean matchLoad = false;
	        	final Set<Summary> seLoadSet = loadedSummaries.get(m);
	     //   	if (seLoadSet == null) System.out.println("No summary for method: " + m);
	        	if (seLoadSet != null) {
	        		for (Summary se : seLoadSet) {
	        			if (checkAndApply(m, se)) {
	     //   				System.out.println("Matching summary for method: " + m.toString());
	        				usedSummariesCnt++;
	        				matchLoad = true;
	        				if (stats || degrade) {
	        					ArraySet<Summary> usedSumms = usedSummaries.get(m);
	        					if (usedSumms == null) {
	        						usedSumms = new ArraySet<Summary>();
	        						usedSummaries.put(m, usedSumms);
	        					}
	        					usedSumms.add(se);
	        				}
	        				break;
	        			}
	        		}
	        	}
	        	if (matchLoad) continue;
	        }
			ControlFlowGraph cfg = m.getCFG();
			if (DEBUG) System.out.println("Processing CFG of method: " + m);
			processCFG(cfg, m);
		}
/*		System.out.println("CHA loop done.");
		System.out.println("Reloading classes.");
		PrimordialClassLoader loader = PrimordialClassLoader.loader;
		for (jq_Class r : classesToBeReloaded) {
//			System.out.println(r);
			loader.unloadBSType(r);
		}
		for (jq_Class r : classesToBeReloaded) {
//			PrimordialClassLoader loader = PrimordialClassLoader.loader;
//			loader.replaceClass(r.getName());
//			Utf8 newDesc = Utf8.get("LREPLACE"+r.getName().replace('.', '/')+";") ;
//			jq_Class newClass = (jq_Class) loader.getBSType(newDesc);
//			loader.clearMap();
			jq_Reference c = (jq_Reference) jq_Type.parseType(r.getName());
	        if (c == null)
	            throw new NoClassDefFoundError(r.getName());
	        c.prepare();
	        assert (c != r);
		}
*/		
		System.out.println("CHA done.");
		long chaEnd = System.nanoTime();
		chatimer.done();
		long chaTime = chaEnd - chaStart;
		System.out.println("CHA TIME: "+ chaTime);
		System.out.println("CHA TIME Readable: " + chatimer.getInclusiveTimeStr());
		
		if (genSummaries) {
			Timer dump = new Timer();
			dump.init();
			
			if (!partialSummaries) {
				computeFinalSummaries(); 
			} else {
				for (jq_Method m : savedSummaries.keySet()) {
					ArraySet<Summary> seSet = savedSummaries.get(m);
					assert seSet.size() == 1 : "Should not have multiple summaries per method";
					Summary s0 = seSet.get(0);
					ArraySet<jq_Method> validMethods = finalSummaries.get(s0);
					if (validMethods == null) {
						validMethods = new ArraySet<jq_Method>();
						finalSummaries.put(s0, validMethods);
					}
					validMethods.add(m);

				}
			}
			
			summHandler.dumpLibSEsFiltered();
			dump.done();
			long dumpTime = dump.getInclusiveTime();
			System.out.println("SUMMARY DUMPING TIME: " + dumpTime);
			System.out.println("SUMMARY DUMPING TIME: " + Timer.getTimeStr(dumpTime));
		}
        
        System.out.println("CompoCHA: Instructions processed: " + elementsProcessed);
        System.out.println("CompoCHA: Reachable classes: " + classes.size());
        System.out.println("CompoCHA: Reachable methods: " + methods.size());
        System.out.println("CompoCHA: Surface methods: " + surfaceMethods.size());
        System.out.println("CompoCHA: Classes to be reloaded: " + classesToBeReloaded.size());
        System.out.println("CompoCHA: Reachable non-abstract methods: " + reachableMethods.size());
        System.out.println("CompoCHA: Used summaries cnt: " + usedSummariesCnt);
        
        if (stats) {
        	// loaded summ stats
        	int total = 0;
//        	int tCond = 0;
        	int subCond = 0;
        	for (jq_Method m : usedSummaries.keySet()) {
        		Set<Summary> seSet = usedSummaries.get(m);
        		if (seSet != null) {
        			for (Summary se : seSet) {
        				total++;
//        				tCond += se.srcNode.reachT.cardinality();
//	            		for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext(); ) {
//	            			iter.advance();
//	                    	subCond += iter.value().cardinality();
//	                    }
        				for (jq_Reference superT : se.srcNode.subTypesL.keySet()) {
	                    	subCond += se.srcNode.subTypesL.get(superT).size();
	                    }
        			}
        		}
        	}
        	System.out.println("CompoSBA: Total used summaries: " + total);
//        	System.out.println("CompoSBA: Total tCond size: " + tCond + ", Average: " + ((double)tCond)/total);
        	System.out.println("CompoSBA: Total subCond size: " + subCond + ", Average: " + ((double)subCond)/total);
        	
            PrintWriter summWrite = OutDirUtils.newPrintWriter("used_summ_sources");
    		PrintWriter summWrite2 = OutDirUtils.newPrintWriter("used_summ_ids");
    		for (jq_Method m : usedSummaries.keySet()) {
    			Set<Summary> seSet = usedSummaries.get(m);
                if (seSet != null) {
                	for (Summary se : seSet) {
                		Pair<jq_Method,Summary> pr = new Pair<jq_Method,Summary>(m, se);
                		Set<String> apps = summHandler.loadedSummOrig.get(pr);
                		for (String app : apps) {
                			summWrite.print(app+"\t");
                		}
                		summWrite.print("\n");
                		
                		Set<String> seIDs = summHandler.loadedSummID.get(pr);
                		for (String seID : seIDs) {
                			summWrite2.println(seID);
                		}
                	}
                }
    		}
    		summWrite.close();
    		summWrite2.close();

        }
        if (degrade) {
			System.out.println("CompoCHA: Degrade iteration " + degradePassNum + " done.");
			degradePassNum++;
			int availableSummCnt = 0;
			for (jq_Method m : loadedSummaries.keySet()) {
				Set<Summary> seSet = loadedSummaries.get(m);
	            if (seSet != null) {
	            	availableSummCnt += seSet.size();
	            }
			}
			System.out.println("CompoCHA: Total saved summaries available before: " + availableSummCnt);
			
			int usedSummCnt = 0;
			for (jq_Method m : surfaceMethods) {
				Set<Summary> seSet = usedSummaries.get(m);
	            if (seSet != null) {
	            	usedSummCnt += seSet.size();
	            	Set<Summary> seSet1 = loadedSummaries.get(m);
	            	seSet1.removeAll(seSet);
	            	if (seSet1.isEmpty()) loadedSummaries.remove(m);
	            }
			}
			surfaceMethods.clear();
			System.out.println("CompoCHA: Total saved summaries available after: " + (availableSummCnt - usedSummCnt));
			System.out.println("----------------------------------------------------------------------------------");
			if (usedSummCnt == 0) break;
		}
        
        
		System.out.println("LEAVE: CHA");
		timer.done();
		System.out.println("Time: " + timer.getInclusiveTimeStr());
		} while(degrade);
	}
	
    private void computeFinalSummaries() {
    	CICG cg = new CICG(domM, rootMethods, reachableMethods, callGraph, MIMap);
    	List<Set<jq_Method>> sccs = cg.getTopSortedSCCs();
    	System.out.println("SCC construction done. Num SCCs: " + sccs.size());

    	// analyze the sccs in a reverse topological order
    	for (int i = sccs.size() - 1; i >= 0; i--) {
    		Set<jq_Method> scci = sccs.get(i);
    	//	System.out.println("Processing SCC " + i + ", SCC size: " + scci.size());
    	//	if (scci.size() == 1) System.out.println(scci.iterator().next());
    		
    		boolean appAccessPres = false;
    		//Process the entire SCC once
    		for (jq_Method m : scci) {
				ArraySet<Summary> summs0 = savedSummaries.get(m);
    			assert summs0.size() == 1 : "Should not have multiple summaries per method";
    			Summary s0 = summs0.get(0);
    			if ((simulateSuperPerf && s0.IsCallbkPres()) || (!simulateSuperPerf && s0.isAppAccessed())) {
    				appAccessPres = true;
    			//	System.out.println("S0: " + s0.toParsableString());
    				break;
    			}
    			for (jq_Method target : cg.getSuccs(m)) {
					ArraySet<Summary> summs1 = savedSummaries.get(target);
	    			assert summs1.size() == 1 : "Should not have multiple summaries per method";
	    			Summary s1 = summs1.get(0);
	    			if (!simulateSuperPerf && s1.isAppAccessed()) {
	    				s0.setAppAccessed();
	    				appAccessPres = true;
	    		//		System.out.println("S1: " + s1.toParsableString());
	    				break;
	    			} else if (simulateSuperPerf && s0.IsCallbkPres()) {
	    				s0.setCallbkPres();
	    				appAccessPres = true;
	    				break;
	    			} else {
	    				s0.merge(s1);
	    			}
    			}
    			if (appAccessPres) break;
			}
    		if (appAccessPres) {
    			for (jq_Method m : scci) {
    				ArraySet<Summary> summs1 = savedSummaries.get(m);
    				assert summs1.size() == 1 : "Should not have multiple summaries per method";
    				Summary s1 = summs1.get(0);
    				if (!simulateSuperPerf) s1.setAppAccessed();
    				else s1.setCallbkPres();
    			}
    			continue;
    		}
    		// Next merge the summaries for each method in SCC
    		
    			ArraySet<Summary> summs0 = savedSummaries.get(scci.iterator().next());
    			assert summs0.size() == 1 : "Should not have multiple summaries per method";
    			Summary s0 = summs0.get(0);
    			for (jq_Method m : scci) {
    				ArraySet<Summary> summs1 = savedSummaries.get(m);
    				assert summs1.size() == 1 : "Should not have multiple summaries per method";
    				Summary s1 = summs1.get(0);
    				s0.merge(s1);
    			}
    			for (jq_Method m : scci) {
//    				ArraySet<Summary> summs1 = savedSummaries.get(m);
//    				assert summs1.size() == 1 : "Should not have multiple summaries per method";
//    				Summary s1 = summs1.get(0);
//    				s1.merge(s0);
    				ArraySet<Summary> summs1 = new ArraySet<Summary>();
    				summs1.add(s0);
    				savedSummaries.put(m, summs1);
    			}
    		
    		
    		// Sanity check; will remove later
/*    		boolean changed = false;
    		int itr = 0;
    		do {
    			System.out.println("Iteration: " + itr++);
    			changed = false;
    			for (jq_Method m : scci) {
    				ArraySet<Summary> summs0 = savedSummaries.get(m);
        			assert summs0.size() == 1 : "Should not have multiple summaries per method";
        			Summary s0 = summs0.get(0);
        			for (jq_Method target : cg.getSuccs(m)) {
    					ArraySet<Summary> summs1 = savedSummaries.get(target);
    	    			assert summs1.size() == 1 : "Should not have multiple summaries per method";
    	    			Summary s1 = summs1.get(0);
    	    			if (s0.merge(s1)) {
    	    				changed = true;
    	    			}
    				}
    			}
    		} while (changed);
*/
    			
    			ArraySet<jq_Method> validMethods = finalSummaries.get(s0);
    			if (validMethods == null) {
    				validMethods = new ArraySet<jq_Method>();
    				finalSummaries.put(s0, validMethods);
    			}
    			validMethods.addAll(scci);
    	}
    }

    private void visitMethod(jq_Method m) {
        if (methods.add(m)) {
            if (!m.isAbstract()) {
            	if (genSummaries && !partialSummaries) reachableMethods.add(m);
                if (DEBUG) System.out.println("\tAdding method: " + m);
                methodWorklist.add(m);
            }
        }
    }
    
    private void processCFG(ControlFlowGraph cfg, jq_Method m) {
    	Summary summ = genSummaries ? new Summary() : null;
    	String mClassName = m.getDeclaringClass().getName();
    	if (!classesNotToBeReloaded.contains(mClassName))
    		classesToBeReloaded.add((jq_Class) jq_Type.parseType(mClassName));
    	
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
            	elementsProcessed++;
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Method n = Invoke.getMethod(q).getMethod();
                    jq_Class c = n.getDeclaringClass();
                    if (ignoreMethods.contains(n.toString())) continue;
                    visitClass(c);
                    visitMethod(n);
                    surfaceMethods.add(n);
                    
                    if (trackCG || genSummaries) {
                    	if (!n.isAbstract()) {
                    		Set<jq_Method> targets = callGraph.get(q);
                    		if (targets == null) {
                    			targets = new HashSet<jq_Method>();
                    			callGraph.put(q, targets);
                    		}
                    		targets.add(n);
                    	}
                    }
                    if (summ != null) {
                    	summ.dstNode.reachT.set(domT.indexOf(c));
                    	summ.dstNode.reachM.set(domM.indexOf(n));
                    	if (trackCG) {
                    		if (!n.isAbstract()) {
                    			int qidx = domI.indexOf(q);
                    			BitSet targets = null;
                    			if (!summ.dstNode.callGraph.containsKey(qidx)) {
                    				targets = new BitSet();
                    				summ.dstNode.callGraph.put(qidx, targets);
                    			}
                    			targets = summ.dstNode.callGraph.get(qidx);
                    			targets.set(domM.indexOf(n));
                    		}
                    	}
                    }
                    
                    if (op instanceof InvokeVirtual ||
                            op instanceof InvokeInterface) {
                        jq_NameAndDesc nd = n.getNameAndDesc();
                        String cName = c.getName();
                        
                        if (summ != null) {
                        	int cidx = domT.indexOf(c);
                        	BitSet subT = null;
                        	if (!summ.srcNode.subTypes.containsKey(cidx)) {
                        		subT = new BitSet();
                        		summ.srcNode.subTypes.put(cidx, subT);
                        	}
                        }
                    	
                        Set<jq_Reference> subs = subTypes.get(c);
                        if (subs == null)
                            continue;
                        for (jq_Reference dRef : subs) {
                            jq_Class d = (jq_Class) dRef;
                            visitClass(d);
                            assert (!d.isInterface());
                            assert (!d.isAbstract());
                            jq_InstanceMethod m2 = d.getVirtualMethod(nd);
                            if (m2 == null)
                                System.out.println(d + " " + nd);
                            assert (m2 != null);
                            visitMethod(m2);
                            surfaceMethods.add(m2);
                            if (trackCG || genSummaries) {
                            	if (!m2.isAbstract()) {
                            		Set<jq_Method> targets = callGraph.get(q);
                            		if (targets == null) {
                            			targets = new HashSet<jq_Method>();
                            			callGraph.put(q, targets);
                            		}
                            		targets.add(m2);
                            	}
                            }
                            
                            if (summ != null) {
                            	summ.dstNode.reachT.set(domT.indexOf(d));
                            	summ.dstNode.reachM.set(domM.indexOf(m2));
                            	int cidx = domT.indexOf(c);
                            	BitSet subT = null;
                            	if (!summ.srcNode.subTypes.containsKey(cidx)) {
                            		subT = new BitSet();
                            		summ.srcNode.subTypes.put(cidx, subT);
                            	}
                            	subT = summ.srcNode.subTypes.get(cidx);
                            	subT.set(domT.indexOf(d));
                            	
                            	if (trackCG) {
                            		if (!m2.isAbstract()) {
                            			int qidx = domI.indexOf(q);
                            			BitSet targets = null;
                            			if (!summ.dstNode.callGraph.containsKey(qidx)) {
                            				targets = new BitSet();
                            				summ.dstNode.callGraph.put(qidx, targets);
                            			}
                            			targets = summ.dstNode.callGraph.get(qidx);
                            			targets.set(domM.indexOf(m2));
                            		}
                            	}
                            }
                        }
                    } else
                        assert (op instanceof InvokeStatic);
                } else if (op instanceof Getstatic) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Field f = Getstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                    if (summ != null) {
                    	summ.dstNode.reachT.set(domT.indexOf(c));
                    }
                } else if (op instanceof Putstatic) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Field f = Putstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                    if (summ != null) {
                    	summ.dstNode.reachT.set(domT.indexOf(c));
                    }
                } else if (op instanceof New) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Class c = (jq_Class) New.getType(q).getType();
                    visitClass(c);
                    if (summ != null) {
                    	summ.dstNode.reachT.set(domT.indexOf(c));
                    }
                } else if (op instanceof NewArray) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Array a = (jq_Array) NewArray.getType(q).getType();
                    visitClass(a);
                    if (summ != null) {
                    	summ.dstNode.reachT.set(domT.indexOf(a));
                    }
                }
            }
        }
        
        if (summ != null) {
        	ArraySet<Summary> savedSumms = savedSummaries.get(m);
        	if (savedSumms == null) {
        		savedSumms = new ArraySet<Summary>();
        		savedSummaries.put(m, savedSumms);
        	}
        	savedSumms.add(summ);
        }
    }

    private void prepareClass(jq_Reference r) {
        if (classes.add(r)) {
//            r.prepare();
            if (DEBUG) System.out.println("\tAdding class: " + r);
            if (r instanceof jq_Array)
                return;
            jq_Class c = (jq_Class) r;
            jq_Class d = c.getSuperclass();
            if (d == null)
                assert (c == javaLangObject);
            else
                prepareClass(d);
            for (jq_Class i : c.getDeclaredInterfaces())
                prepareClass(i);
        }
    }

    private void visitClass(jq_Reference r) {
        prepareClass(r);
        if (r instanceof jq_Array)
            return;
        jq_Class c = (jq_Class) r;
        visitClinits(c);
    }

    private void visitClinits(jq_Class c) {
        if (classesVisitedForClinit.add(c)) {
            jq_ClassInitializer m = c.getClassInitializer();
            // m is null for classes without class initializer method
            if (m != null) {
                visitMethod(m);
                surfaceMethods.add(m);
                if (genSummaries && !partialSummaries) rootMethods.add(m);
            }
            jq_Class d = c.getSuperclass();
            if (d != null)
                visitClinits(d);
            for (jq_Class i : c.getDeclaredInterfaces())
                visitClinits(i);
        }
    }
    
    private boolean checkAndApply(jq_Method m, Summary se) {
/*    	//check
    	for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		BitSet bitsubT = iter.value();
    		Set<jq_Reference> subT = new ArraySet<jq_Reference>();
    		for (int qidx = bitsubT.nextSetBit(0); qidx >= 0; qidx = bitsubT.nextSetBit(qidx+1)){
    			subT.add((jq_Reference) domT.get(qidx));
    		}
    		Set<jq_Reference> subs = subTypes.get(domT.get(iter.key()));
    		if (subs == null) return false;
    		if (!(subT.equals(subs))) return false;
    	}

    	//apply
    	for (int qidx = se.dstNode.reachT.nextSetBit(0); qidx >= 0; qidx = se.dstNode.reachT.nextSetBit(qidx+1)){
    		visitClass((jq_Reference) domT.get(qidx));
		}
    	
    	for (int qidx = se.dstNode.reachM.nextSetBit(0); qidx >= 0; qidx = se.dstNode.reachM.nextSetBit(qidx+1)){
    		jq_Method reachM = (jq_Method) domM.get(qidx);
    		methods.add(reachM);
    	}
    	
    	if (trackCG) {
    		for (TIntObjectIterator<BitSet> iter = se.dstNode.callGraph.iterator(); iter.hasNext();) {
    			iter.advance();
    			Set<jq_Method> targets = callGraph.get(domI.get(iter.key()));
    			if (targets == null) {
    				targets = new HashSet<jq_Method>();
    				callGraph.put(domI.get(iter.key()), targets);
    			}
    			BitSet bitTrgts = iter.value();
    			for (int qidx = bitTrgts.nextSetBit(0); qidx >= 0; qidx = bitTrgts.nextSetBit(qidx+1)){
    				targets.add((jq_Method) domM.get(qidx));
    			}
    		}
    	}
*/
    	//check
    	for (jq_Reference superT : se.srcNode.subTypesL.keySet()) {
    		Set<jq_Reference> summaryCond = se.srcNode.subTypesL.get(superT);
    		Set<jq_Reference> subs = subTypes.get(superT);
    		if (subs == null && summaryCond.size() != 0) {
/*    			System.out.println("Mismatch: " + m);
    			System.out.println("Supertype: " + superT);
    			System.out.println("Summary condition: " + se.srcNode.subTypesL.get(superT).size());
    			for (jq_Reference t : se.srcNode.subTypesL.get(superT)) {
    				System.out.print(t+"###");
    			}
    			System.out.println();
    			System.out.println("App condition: null");    			
*/    			return false;
    		} else if (subs != null && !(summaryCond.equals(subs))) {
/*    			System.out.println("Mismatch: " + m);
    			System.out.println("Supertype: " + superT);
    			System.out.println("Summary condition: " + se.srcNode.subTypesL.get(superT).size());
    			for (jq_Reference t : se.srcNode.subTypesL.get(superT)) {
    				System.out.print(t+"###");
    			}
    			System.out.println();
    			System.out.println("App condition: " + subs.size());
    			for (jq_Reference t : subs) {
    				System.out.print(t+"###");
    			}
    			System.out.println();    			
*/    			return false;
    		}
    	}

    	
    	// check
 /*   	for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		BitSet summaryCond = iter.value();
    		BitSet subs = subTypesBitset.get(iter.key());
    		if (subs == null && !summaryCond.isEmpty()) {
    			return false;
    		} else if (subs != null && !(summaryCond.equals(subs))) {
    			return false;
    		}
    	}
*/    	
    	//apply
    	for (jq_Reference t : se.dstNode.reachTL){
    		visitClass(t);
		}
    	
    	for (jq_Method reachM : se.dstNode.reachML){
    		if (partialSummaries) {
    			visitMethod(reachM);
    		} else {
    			methods.add(reachM);
    		}
    	}
    	
    	if (trackCG) {
    		for (Quad q : se.dstNode.callGraphL.keySet()) {
    			Set<jq_Method> targets = callGraph.get(q);
    			if (targets == null) {
    				targets = new HashSet<jq_Method>();
    				callGraph.put(q, targets);
    			}
    			targets.addAll(se.dstNode.callGraphL.get(q));
    		}
    	}
    	
    	return true;
    }
}
