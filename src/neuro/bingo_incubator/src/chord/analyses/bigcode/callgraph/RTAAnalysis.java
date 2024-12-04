package chord.analyses.bigcode.callgraph;

import gnu.trove.iterator.TIntObjectIterator;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Main.HostedVM;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.program.MethodElem;
import chord.program.Program;
import chord.program.Reflect;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.IndexSet;
import chord.util.Timer;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.program.reflect.CastBasedStaticReflect;
import chord.program.reflect.DynamicReflectResolver;
import chord.program.reflect.StaticReflectResolver;

/*
 * chord.compoRTA.handleReflection=[false|true] handle reflection or not
 * chord.compoRTA.summaryDir
 * chord.compoRTA.simulateSuperPerf
 * chord.compoRTA.superPerfIgnoreAppCallbk
 * chord.compoRTA.useLibPrefix
 * chord.compoRTA.degrade
 * chord.compoRTA.stats
 *                             
 */
@Chord(name = "rta-java",
	consumes = {"T", "M", "I", "MI"}
)
public class RTAAnalysis extends JavaAnalysis { 
    private static final String MAIN_CLASS_NOT_DEFINED =
        "ERROR: RTA: Property chord.main.class must be set to specify the main class of program to be analyzed.";
    private static final String MAIN_METHOD_NOT_FOUND =
        "ERROR: RTA: Could not find main class '%s' or main method in that class.";
    private static final String METHOD_NOT_FOUND_IN_SUBTYPE =
        "WARN: RTA: Expected instance method %s in class %s implementing/extending interface/class %s.";

    public static final boolean DEBUG = false;

    private String reflectKind; // [none|static|static_cast|dynamic]

    /////////////////////////

    /*
     * Data structures used only if reflectKind == dynamic
     */

    private List<Pair<String, List<String>>> dynamicResolvedClsForNameSites;
    private List<Pair<String, List<String>>> dynamicResolvedObjNewInstSites;
    private List<Pair<String, List<String>>> dynamicResolvedConNewInstSites;
    private List<Pair<String, List<String>>> dynamicResolvedAryNewInstSites;

    /////////////////////////

    /*
     * Data structures used only if reflectKind == static
     */

    // Intra-procedural analysis for inferring the class loaded by calls to
    // {@code Class.forName(s)} and the class of objects allocated by calls to
    // {@code v.newInstance()}.  The analysis achieves this by
    // intra-procedurally tracking the flow of string constants to {@code s}
    // and the flow of class constants to {@code v}.
    private StaticReflectResolver staticReflectResolver;

    // Methods in which forName/newInstance sites have already been analyzed
    private Set<jq_Method> staticReflectResolved;

    //constructors invoked implicitly via reflection
    private LinkedHashSet<jq_Method> reflectiveCtors;

    /////////////////////////

    /*
     * Transient data structures reset after every iteration.
     */

    // Set of all classes whose clinits and super class/interface clinits
    // have been processed so far in current interation; this set is kept to
    // avoid repeatedly visiting super classes/interfaces within an
    // iteration (which incurs a huge runtime penalty) only to find that all
    // their clinits have already been processed in that iteration.
    private Set<jq_Class> classesVisitedForClinit;

    // Set of all methods deemed reachable so far in current iteration.
    public ArraySet<jq_Method> methods;
    
 // Set of non-abstract methods deemed reachable so far.
    public ArraySet<jq_Method> reachableMethods;
    
    // Set of all root methods discovered so far.
    public ArraySet<jq_Method> rootMethods;
    
 // callgraph constructed so far in current iteration
    public Map<Quad, Set<jq_Method>> callGraph;
    
    // subtyping relation constructed so far
    public Map<jq_Reference, Set<jq_Reference>> shadowSubTypes;

    /////////////////////////

    /*
     * Persistent data structures not reset after iterations.
     */

    private Reflect reflect;

    // set of all classes deemed reachable so far
    public Set<jq_Reference> classes;
    
    // subtyping relation constructed so far
    public Map<jq_Reference, Set<jq_Reference>> subTypes;

    // set of all (concrete) classes deemed instantiated so far either
    // by a reachable new/newarray statement or due to reflection
    public Set<jq_Reference> reachableAllocClasses;
    
    // worklist for methods seen so far in current iteration but whose
    // CFGs haven't been processed yet
    private List<jq_Method> methodWorklist;

    // handle to the representation of class java.lang.Object
    private jq_Class javaLangObject;

    // flag indicating that another iteration is needed; it is set if
    // set reachableAllocClasses grows in the current iteration
    private boolean repeat = true;
    
    
    // Input properties
	private SummaryHandler summHandler;
	private String summaryDir = "";
	private boolean genSummaries = false;
	private boolean consumeSummaries = false;
	private boolean dumpToFile = false;
	private boolean degrade = false;
	private boolean stats = false;
	private boolean simulateSuperPerf = false;
	private int elementsProcessed = 0;
	
	private boolean isInit = false;
	
	public Map<jq_Method, ArraySet<Summary>> savedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	public Map<Summary, ArraySet<jq_Method>> finalSummaries = new HashMap<Summary, ArraySet<jq_Method>>();
	public Map<jq_Method, ArraySet<Summary>> loadedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	public Map<jq_Method, ArraySet<Summary>> usedSummaries = new HashMap<jq_Method, ArraySet<Summary>>();
	private Map<jq_Method, Set<Quad>> MIMap = new HashMap<jq_Method, Set<Quad>>();
	
	DomT domT;
	DomM domM;
	DomI domI;
	
	private int usedSummariesCnt = 0;
	private Set<String> ignoreMethods;
    
	public RTAAnalysis(){}
	
	public RTAAnalysis(boolean isBaseline, boolean sumgen) {
		this.genSummaries = sumgen;
		this.consumeSummaries = (!isBaseline && !sumgen);
		this.dumpToFile = !(isBaseline && !sumgen);
		this.reflectKind = Config.reflectKind;
		init();
		System.out.println("Initialization done");
	}
	
	private void init() {
		summaryDir = System.getProperty("chord.compoRTA.summaryDir");
		System.out.println("summaryDir: " + summaryDir);
		degrade = Utils.buildBoolProperty("chord.compoRTA.degrade", false);
		System.out.println("degrade: " + degrade);
		if (degrade) assert(consumeSummaries && !genSummaries);
		stats = Utils.buildBoolProperty("chord.compoRTA.stats", false);
		System.out.println("stats: " + stats);
		
		domT = (DomT) ClassicProject.g().getTrgt("T");
		domM = (DomM) ClassicProject.g().getTrgt("M");
		domI = (DomI) ClassicProject.g().getTrgt("I");
		
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
		IndexSet<jq_Reference> classes = Program.g().getClasses();
		for (jq_Reference t : classes) {
			jq_Reference ref = (jq_Reference) t;
			Set<jq_Reference> subs = subTypes.get(ref);
			if (subs == null) {
				subs = new ArraySet<jq_Reference>();
				subTypes.put(ref, subs);
			}
		}
		
		shadowSubTypes = new HashMap<jq_Reference, Set<jq_Reference>>();
		for (jq_Reference t : classes) {
			jq_Reference ref = (jq_Reference) t;
			Set<jq_Reference> subs = shadowSubTypes.get(ref);
			if (subs == null) {
				subs = new ArraySet<jq_Reference>();
				shadowSubTypes.put(ref, subs);
			}
		}
		
		{
			MIMap = new HashMap<jq_Method, Set<Quad>>();
			ProgramRel relMI = (ProgramRel) ClassicProject.g().getTrgt("MI");
			relMI.load();

			Iterable<Pair<jq_Method, Quad>> tuples = relMI.getAry2ValTuples();
			for (Pair<jq_Method, Quad> t : tuples){
				Set<Quad> quads = MIMap.get(t.val0);
				if (quads == null) {
					quads = new ArraySet<Quad>();
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
		
		simulateSuperPerf = Utils.buildBoolProperty("chord.compoRTA.simulateSuperPerf", false);
		System.out.println("simulateSuperPerf: " + simulateSuperPerf);
		summHandler.simulateSuperPerf = simulateSuperPerf;
		boolean superPerfIgnoreAppCallbk = Utils.buildBoolProperty("chord.compoRTA.superPerfIgnoreAppCallbk", false);
		assert !superPerfIgnoreAppCallbk : "SuperPerfIgnoreAppCallbk not implemented for RTA";
		System.out.println("superPerfIgnoreAppCallbk: " + superPerfIgnoreAppCallbk);
		summHandler.superPerfIgnoreAppCallbk = superPerfIgnoreAppCallbk;
		boolean useLibPrefix = Utils.buildBoolProperty("chord.compoRTA.useLibPrefix", true);
		System.out.println("useLibPrefix: " + useLibPrefix);
		summHandler.useLibPrefix = useLibPrefix;
		
		summHandler.init();
		
		if (consumeSummaries) {
			loadedSummaries.clear();
        	summHandler.loadSavedSEs();
		}
	}

    public void run() {
    	if (!isInit) init();
        classes = new ArraySet<jq_Reference>();
        classesVisitedForClinit = new HashSet<jq_Class>();
        reachableAllocClasses = new ArraySet<jq_Reference>();
        methods = new ArraySet<jq_Method>();
        reachableMethods = new ArraySet<jq_Method>();
        rootMethods = new ArraySet<jq_Method>();
        callGraph = new HashMap<Quad, Set<jq_Method>>();
        methodWorklist = new ArrayList<jq_Method>();
    
        if (Config.verbose >= 1) System.out.println("ENTER: RTA");
        Timer timer = new Timer();
        timer.init();
        Timer rtatimer = new Timer();
        rtatimer.init();
        if (reflectKind.equals("static")) {
        	assert false:"reflection is not handled";
//            staticReflectResolver = new StaticReflectResolver();
//            staticReflectResolved = new HashSet<jq_Method>();
//            reflectiveCtors = new LinkedHashSet<jq_Method>();
        } else if (reflectKind.equals("static_cast")) {
        	assert false:"reflection is not handled";
//            staticReflectResolved = new HashSet<jq_Method>();
//            reflectiveCtors = new LinkedHashSet<jq_Method>();
//            staticReflectResolver = new CastBasedStaticReflect(reachableAllocClasses, staticReflectResolved);
        } else if (reflectKind.equals("dynamic")) {
        	assert false:"reflection is not handled";
//            DynamicReflectResolver dynamicReflectResolver = new DynamicReflectResolver();
//            dynamicReflectResolver.run();
//            dynamicResolvedClsForNameSites = dynamicReflectResolver.getResolvedClsForNameSites();
//            dynamicResolvedObjNewInstSites = dynamicReflectResolver.getResolvedObjNewInstSites();
//            dynamicResolvedConNewInstSites = dynamicReflectResolver.getResolvedConNewInstSites();
//            dynamicResolvedAryNewInstSites = dynamicReflectResolver.getResolvedAryNewInstSites();
//            reflectiveCtors = new LinkedHashSet<jq_Method>();
        }
         
//        reflect = new Reflect();
//        HostedVM.initialize();
        javaLangObject = PrimordialClassLoader.getJavaLangObject();
        String mainClassName = Config.mainClassName;
        if (mainClassName == null)
            Messages.fatal(MAIN_CLASS_NOT_DEFINED);
        jq_Class mainClass = (jq_Class) jq_Type.parseType(mainClassName);
        if (mainClass == null)
            Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        prepareClass(mainClass);
        jq_Method mainMethod = (jq_Method) mainClass.getDeclaredMember(
            new jq_NameAndDesc("main", "([Ljava/lang/String;)V"));
        if (mainMethod == null)
            Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        
        for (int i = 0; repeat; i++) {
            if (Config.verbose >= 1) { 
            	System.out.println("Iteration: " + i + ", Num instructions processed: " + elementsProcessed);
            	
            }
            repeat = false;
            classesVisitedForClinit.clear();
            methods.clear();
            rootMethods.clear();
            reachableMethods.clear();
            callGraph.clear();
            savedSummaries.clear();
                        
            visitClinits(mainClass);
            visitMethod(mainMethod);
            rootMethods.add(mainMethod);
            
            if (reflectiveCtors != null)
                for (jq_Method m: reflectiveCtors) {
                    visitMethod(m);
                }
            
            while (!methodWorklist.isEmpty()) {
                int n = methodWorklist.size();
         //       jq_Method m = methodWorklist.remove(n - 1);
                jq_Method m = methodWorklist.remove(0);
                if (DEBUG) System.out.println("Processing CFG of " + m);
                processMethod(m);
            }

            if (staticReflectResolver != null) {
                staticReflectResolver.startedNewIter();
            }
            
            if (genSummaries) { computeFinalSummaries(); }
            
            for (jq_Reference t : shadowSubTypes.keySet()) {
            	Set<jq_Reference> shadowT = shadowSubTypes.get(t);
            	Set<jq_Reference> origT = subTypes.get(t);
            	origT.addAll(shadowT);
            	shadowT.clear();
            }

            
            System.out.println("CompoRTA: Reachable classes: " + classes.size());
            System.out.println("CompoRTA: Reachable alloc classes: " + reachableAllocClasses.size());
            System.out.println("CompoRTA: Reachable methods: " + methods.size());
            System.out.println("CompoRTA: Reachable root methods: " + rootMethods.size());
            System.out.println("CompoRTA: Reachable non-abstract methods: " + reachableMethods.size());
        }
        rtatimer.done();
        System.out.println("RTA TIME: " + rtatimer.getInclusiveTimeStr());
        
        if (genSummaries) {
			Timer dump = new Timer();
			dump.init();
			summHandler.dumpLibSEsFiltered();
			dump.done();
			long dumpTime = dump.getInclusiveTime();
			System.out.println("SUMMARY DUMPING TIME: " + dumpTime);
			System.out.println("SUMMARY DUMPING TIME: " + Timer.getTimeStr(dumpTime));
		}
        
        System.out.println("CompoRTA: Instructions processed: " + elementsProcessed);
        System.out.println("CompoRTA: Reachable classes: " + classes.size());
        System.out.println("CompoRTA: Reachable alloc classes: " + reachableAllocClasses.size());
        System.out.println("CompoRTA: Reachable methods: " + methods.size());
        
        System.out.println("CompoRTA: Used summaries cnt: " + usedSummariesCnt);
        
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
	            		for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext(); ) {
	            			iter.advance();
	                    	subCond += iter.value().cardinality();
	                    }
        			}
        		}
        	}
        	System.out.println("CompoSBA: Total used summaries: " + total);
//        	System.out.println("CompoSBA: Total tCond size: " + tCond + ", Average: " + ((double)tCond)/total);
        	System.out.println("CompoSBA: Total subCond size: " + subCond + ", Average: " + ((double)subCond)/total);
        }
        
        timer.done();
        if (Config.verbose >= 1) {
            System.out.println("LEAVE: RTA");
            System.out.println("Time: " + timer.getInclusiveTimeStr());
        }
        staticReflectResolver = null; // no longer in use; stop referencing it
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
    				break;
    			}
    			for (jq_Method target : cg.getSuccs(m)) {
					ArraySet<Summary> summs1 = savedSummaries.get(target);
	    			assert summs1.size() == 1 : "Should not have multiple summaries per method";
	    			Summary s1 = summs1.get(0);
	    			if (!simulateSuperPerf && s1.isAppAccessed()) {
	    				s0.setAppAccessed();
	    				appAccessPres = true;
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
    			// Post process summaries to reduce the size of the condition
    		//	s0.srcNode.reachT.andNot(s0.dstNode.allocReachT);
    			for ( TIntObjectIterator<BitSet> iter = s0.srcNode.subTypes.iterator(); iter.hasNext(); ) {
    				iter.advance();
    				BitSet subT = iter.value();
    				subT.andNot(s0.dstNode.allocReachT);
    			}
    			
    			ArraySet<jq_Method> validMethods = finalSummaries.get(s0);
    			if (validMethods == null) {
    				validMethods = new ArraySet<jq_Method>();
    				finalSummaries.put(s0, validMethods);
    			}
    			validMethods.addAll(scci);
    	}
    }

    /**
     * Called whenever RTA sees a method.
     * Adds to worklist if it hasn't previously been seen on this iteration.
     * @param m
     */
    protected void visitMethod(jq_Method m) {
        if (methods.add(m)) {
            if (DEBUG) System.out.println("\tAdding method: " + m);
            if (!m.isAbstract()) {
            	reachableMethods.add(m);
            	if (m.toString().contains("<init>") || m.toString().contains("<clinit>"))
                    methodWorklist.add(0, m);
            	else
            		methodWorklist.add(m);
            }
        }
    }

    private void processMethod(jq_Method m) {
    	if (consumeSummaries) {
    		boolean matchLoad = false;
        	final Set<Summary> seLoadSet = loadedSummaries.get(m);
        	if (seLoadSet != null) {
        		for (Summary se : seLoadSet) {
        			if (checkAndApply(m, se)) {
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
        	if (matchLoad) return;
        }
    	
    	Summary summ = genSummaries ? new Summary() : null;
    	
        if (staticReflectResolved != null && staticReflectResolved.add(m)) {
            staticReflectResolver.run(m);
            Set<Pair<Quad, jq_Reference>> resolvedClsForNameSites =
                staticReflectResolver.getResolvedClsForNameSites();
            Set<Pair<Quad, jq_Reference>> resolvedObjNewInstSites =
                staticReflectResolver.getResolvedObjNewInstSites();
            for (Pair<Quad, jq_Reference> p : resolvedClsForNameSites)
                processResolvedClsForNameSite(p.val0, p.val1);
            for (Pair<Quad, jq_Reference> p : resolvedObjNewInstSites)
                processResolvedObjNewInstSite(p.val0, p.val1);
        }
        ControlFlowGraph cfg = m.getCFG();
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
            	elementsProcessed++;
                if (DEBUG) System.out.println("Quad: " + q);
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    if (op instanceof InvokeVirtual || op instanceof InvokeInterface)
                        processVirtualInvk(m, q, summ);
                    else
                        processStaticInvk(m, q, summ);
                } else if (op instanceof Getstatic) {
                    jq_Field f = Getstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                    if (summ != null) summ.dstNode.reachT.set(domT.indexOf(c));
                } else if (op instanceof Putstatic) {
                    jq_Field f = Putstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                    if (summ != null) summ.dstNode.reachT.set(domT.indexOf(c));
                } else if (op instanceof New) {
                    jq_Class c = (jq_Class) New.getType(q).getType();
                    visitClass(c);
                    if (updateAllocClasses(c, summ))	repeat = true;                    
                    if (summ != null)	summ.dstNode.reachT.set(domT.indexOf(c));
                } else if (op instanceof NewArray) {
                    jq_Array a = (jq_Array) NewArray.getType(q).getType();
                    visitClass(a);
                    if (updateAllocClasses(a, summ))	repeat = true;
                    if (summ != null)	summ.dstNode.reachT.set(domT.indexOf(a));

/*
                } else if (op instanceof Move) {
                    Operand ro = Move.getSrc(q);
                    if (ro instanceof AConstOperand) {
                        Object c = ((AConstOperand) ro).getValue();
                        if (c instanceof Class) {
                            String s = ((Class) c).getName();
                            // s is in encoded form only if it is an array type
                            jq_Reference d = (jq_Reference) jq_Type.parseType(s);
                            if (d != null)
                                visitClass(d);
                        }
                    }
*/
                }
            }
        }
        
        if (summ != null) {
        	// Post process summary before saving it.
       // 	summ.srcNode.reachT.andNot(summ.dstNode.allocReachT);
        	for (TIntObjectIterator<BitSet> iter = summ.srcNode.subTypes.iterator(); iter.hasNext(); ) {
        		iter.advance();
        		BitSet subT = iter.value();
        		subT.andNot(summ.dstNode.allocReachT);
        	}
        	
        	ArraySet<Summary> savedSumms = savedSummaries.get(m);
        	if (savedSumms == null) {
        		savedSumms = new ArraySet<Summary>();
        		savedSummaries.put(m, savedSumms);
        	}
        	savedSumms.add(summ);
        }
    }
    
    private boolean checkAndApply(jq_Method m, Summary se) {
    	//check
//    	Set<jq_Reference> srcReachT = new ArraySet<jq_Reference>();
//    	for (int qidx = se.srcNode.reachT.nextSetBit(0); qidx >= 0; qidx = se.srcNode.reachT.nextSetBit(qidx+1)){
//    		srcReachT.add((jq_Reference) domT.get(qidx));
//		}
 //   	if (reachableAllocClasses.containsAll(srcReachT)) {
 //   		reachTChkPassedCnt++;
        	Set<jq_Reference> reachAlloc = new ArraySet<jq_Reference>();
        	for (int qidx = se.dstNode.allocReachT.nextSetBit(0); qidx >= 0; qidx = se.dstNode.allocReachT.nextSetBit(qidx+1)){
        		reachAlloc.add((jq_Reference) domT.get(qidx));
    		}
        	
    		for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext(); ) {
    			iter.advance();
    			BitSet bitsubT = iter.value();
    			Set<jq_Reference> subT = new ArraySet<jq_Reference>();
    	    	for (int qidx = bitsubT.nextSetBit(0); qidx >= 0; qidx = bitsubT.nextSetBit(qidx+1)){
    	    		subT.add((jq_Reference) domT.get(qidx));
    			}
//    			if (!(subT.equals(subTypes.get(iter.key())))) return false;
    	    	Set<jq_Reference> testSubT = subTypes.get(domT.get(iter.key()));
    	    	assert (testSubT != null);
    	    	Set<jq_Reference> testSubTCpy = new ArraySet<jq_Reference>(testSubT);
    	    	testSubTCpy.removeAll(reachAlloc);
    			if (!(subT.equals(testSubTCpy))) {
    		/*		System.out.println("Cond:");
    				for (jq_Reference t : subT) {
    					System.out.print(t + "###");
    				}
    				System.out.println();
    				System.out.println("Orig:");
    				for (jq_Reference t : origSubT) {
    					System.out.print(t + "###");
    				}
    				System.out.println();
    		*/		
    				return false;
    			}
    		}
//   	} else {
//    		return false;
//    	}

    	//apply
    	for (int qidx = se.dstNode.reachT.nextSetBit(0); qidx >= 0; qidx = se.dstNode.reachT.nextSetBit(qidx+1)){
    		visitClass((jq_Reference) domT.get(qidx));
		}

    	//	if (updateAllocClasses(reachAlloc, null)) repeat = true;
    	if (reachableAllocClasses.addAll(reachAlloc)) repeat = true;
    	
    	for (int qidx = se.dstNode.reachM.nextSetBit(0); qidx >= 0; qidx = se.dstNode.reachM.nextSetBit(qidx+1)){
    		jq_Method reachM = (jq_Method) domM.get(qidx);
    		methods.add(reachM);
    		if (!reachM.isAbstract()) reachableMethods.add(reachM);
    	}
    	
		for (TIntObjectIterator<BitSet> iter = se.dstNode.callGraph.iterator(); iter.hasNext();) {
			iter.advance();
    		Set<jq_Method> targets = callGraph.get(domI.get(iter.key()));
    		if (targets == null) {
    			targets = new ArraySet<jq_Method>();
    			callGraph.put(domI.get(iter.key()), targets);
    		}
    		BitSet bitTrgts = iter.value();
        	for (int qidx = bitTrgts.nextSetBit(0); qidx >= 0; qidx = bitTrgts.nextSetBit(qidx+1)){
        		targets.add((jq_Method) domM.get(qidx));
    		}
    	}
		
		for (TIntObjectIterator<BitSet> iter = se.dstNode.subTypes.iterator(); iter.hasNext();) {
			iter.advance();
    		Set<jq_Reference> subT = shadowSubTypes.get(domT.get(iter.key()));
    		assert (subT != null);
    		BitSet bitTypes = iter.value();
        	for (int qidx = bitTypes.nextSetBit(0); qidx >= 0; qidx = bitTypes.nextSetBit(qidx+1)){
        		subT.add((jq_Reference) domT.get(qidx));
    		}
    	}
    	return true;
    }

    // does qStr (in format bci!mName:mDesc@cName) correspond to quad q in method m?
    private static boolean matches(String qStr, jq_Method m, Quad q) {
        MethodElem me = MethodElem.parse(qStr);
        return me.mName.equals(m.getName().toString()) &&
            me.mDesc.equals(m.getDesc().toString()) &&
            me.cName.equals(m.getDeclaringClass().getName()) &&
            q.getBCI() == me.offset;
    }

    private void processVirtualInvk(jq_Method m, Quad q, Summary summ) {
        jq_Method n = Invoke.getMethod(q).getMethod();
        jq_Class c = n.getDeclaringClass();
        visitClass(c);
        visitMethod(n);
        
        if (!n.isAbstract()) {
        	Set<jq_Method> targets = callGraph.get(q);
        	if (targets == null) {
        		targets = new ArraySet<jq_Method>();
        		callGraph.put(q, targets);
        	}
        	targets.add(n);
        }
    	
        if (summ != null) {
        	summ.dstNode.reachT.set(domT.indexOf(c));
        	summ.dstNode.reachM.set(domM.indexOf(n));
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
        	int cidx = domT.indexOf(c);
        	BitSet subT = null;
        	if (!summ.srcNode.subTypes.containsKey(cidx)) {
        		subT = new BitSet();
        		summ.srcNode.subTypes.put(cidx, subT);
        	}
        }
        String cName = c.getName();
        if (cName.equals("java.lang.Class")) {
            if (dynamicResolvedObjNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("()Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedObjNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedObjNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        } else if (cName.equals("java.lang.reflect.Constructor")) {
            if (dynamicResolvedConNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("([Ljava/lang/Object;)Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedConNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedConNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        }
        
        if (ignoreMethods.contains(n.toString())) return;
        jq_NameAndDesc nd = n.getNameAndDesc();
        for (jq_Reference r : subTypes.get(c)) {
        	jq_InstanceMethod m2 = r.getVirtualMethod(nd);
            if (m2 == null) {
                Messages.log(METHOD_NOT_FOUND_IN_SUBTYPE,
                    nd.toString(), r.getName(), c.getName());
            } else {
                visitMethod(m2);
                
                if (!m2.isAbstract()) {
                	Set<jq_Method> targets = callGraph.get(q);
                	if (targets == null) {
                		targets = new ArraySet<jq_Method>();
                		callGraph.put(q, targets);
                	}
                	targets.add(m2);
                }
            	
                if (summ != null) {
         //       	summ.srcNode.reachT.set(domT.indexOf(r));
                	summ.dstNode.reachM.set(domM.indexOf(m2));
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
                	
                	int cidx = domT.indexOf(c);
                	BitSet subT = null;
                	if (!summ.srcNode.subTypes.containsKey(cidx)) {
                		subT = new BitSet();
                		summ.srcNode.subTypes.put(cidx, subT);
                	}
                	subT = summ.srcNode.subTypes.get(cidx);
                	subT.set(domT.indexOf(r));
                }
            }
        }
    }

    private void processStaticInvk(jq_Method m, Quad q, Summary summ) {
        jq_Method n = Invoke.getMethod(q).getMethod();
        jq_Class c = n.getDeclaringClass();
        visitClass(c);
        visitMethod(n);
        Set<jq_Method> targets = callGraph.get(q);
    	if (targets == null) {
    		targets = new ArraySet<jq_Method>();
    		callGraph.put(q, targets);
    	}
    	targets.add(n);
    	
        if (summ != null) {
        	summ.dstNode.reachT.set(domT.indexOf(c));
        	summ.dstNode.reachM.set(domM.indexOf(n));
        	int qidx = domI.indexOf(q);
        	BitSet bitTargets = null;        	
        	if (!summ.dstNode.callGraph.containsKey(qidx)) {
        		bitTargets = new BitSet();
    			summ.dstNode.callGraph.put(qidx, bitTargets);
    		}
        	bitTargets = summ.dstNode.callGraph.get(qidx);
        	bitTargets.set(domM.indexOf(n));
        }
        String cName = c.getName();
        if (cName.equals("java.lang.Class")) {
            if (dynamicResolvedClsForNameSites != null &&
                    n.getName().toString().equals("forName") &&
                    n.getDesc().toString().equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                for (Pair<String, List<String>> p : dynamicResolvedClsForNameSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedClsForNameSite(q, r);
                        }
                        break;
                    }
                }
            }
        } else if (cName.equals("java.lang.reflect.Array")) {
            if (dynamicResolvedAryNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedAryNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedAryNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void prepareClass(jq_Reference r) {
        if (classes.add(r)) {
//            r.prepare();
            if (DEBUG) System.out.println("\tAdding class: " + r);
            if (r instanceof jq_Array) return;
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
    
    private boolean updateAllocClasses(jq_Reference r, Summary summ) {
    	boolean addSubtype = reachableAllocClasses.add(r);
    	boolean retValue = addSubtype;
    	if (summ != null) {
    		BitSet allocClone = summ.dstNode.allocReachT;
    		summ.dstNode.allocReachT.set(domT.indexOf(r));
    		addSubtype |= (!allocClone.equals(summ.dstNode.allocReachT));
    	}
    	
    	if(addSubtype) {
    		if (r instanceof jq_Array) return true;
    		jq_Class c = (jq_Class) r;
    		assert (!c.isInterface());
            assert (!c.isAbstract());
    		for (jq_Reference ref : shadowSubTypes.keySet()) {
    			if (!(ref instanceof jq_Class)) continue;
    			jq_Class superC = (jq_Class) ref;
    			boolean isInterface = superC.isInterface();
    			boolean matches = isInterface ? c.implementsInterface(superC) : c.extendsClass(superC);
    			if (matches) {
    				Set<jq_Reference> sub = shadowSubTypes.get(ref);
    				sub.add(c);
    				if (summ != null) {
    					int refIdx = domT.indexOf(ref);
    					BitSet subT = summ.dstNode.subTypes.get(refIdx);
    					if (subT == null) {
    						subT = new BitSet();
    						summ.dstNode.subTypes.put(refIdx, subT);
    					}
    					subT.set(domT.indexOf(c));
    				}   				
    			}
    		}
    	}
    	return retValue;
    }
    
    protected void visitClass(jq_Reference r) {
        prepareClass(r);
        if (r instanceof jq_Array) return;
        jq_Class c = (jq_Class) r;
        visitClinits(c);
    }

    protected void visitClinits(jq_Class c) {
        if (classesVisitedForClinit.add(c)) {
            jq_ClassInitializer m = c.getClassInitializer();
            // m is null for classes without class initializer method
            if (m != null) {
                visitMethod(m);
                rootMethods.add(m);
            }
            jq_Class d = c.getSuperclass();
            if (d != null)
                visitClinits(d);
            for (jq_Class i : c.getDeclaredInterfaces())
                visitClinits(i);
        }
    }
    
    private static boolean isClassDefined(Quad q, jq_Reference r) {
        try {
            r.load(); // triggers NoClassDefFoundError if not found. Do this before adding to reflect.
            return true;
        } catch(NoClassDefFoundError e) {
            String qpos = q.getMethod().getDeclaringClass() + " " +  q.getMethod() + ":" + q.getLineNumber(); 
            Messages.log(qpos + " references class "+ r + " via reflection. Class not found in classpath");
            return false;
        }
    }

    /*
     * It can happen that we see Class.forName("something not in classpath").
     * Should handle this gracefully.
     */
    private void processResolvedClsForNameSite(Quad q, jq_Reference r) {
        if (isClassDefined(q, r)) {
            reflect.addResolvedClsForNameSite(q, r);
            visitClass(r);
        }
    }

    private void processResolvedObjNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;

        reflect.addResolvedObjNewInstSite(q, r);
        visitClass(r);
        if (updateAllocClasses(r, null) ||
                (staticReflectResolver != null && staticReflectResolver.needNewIter()))
            repeat = true;
        if (r instanceof jq_Class) {
            jq_Class c = (jq_Class) r;
            
        //two cases: call was Constructor.newInstance or call was Class.newInstance
        //Static reflection analysis folds these together, so we pull them apart here
            String cName =Invoke.getMethod(q).getMethod().getDeclaringClass().getName();
            if(cName.equals("java.lang.reflect.Constructor")) {
                processResolvedConNewInstSite(q, r);
            } else {
                jq_Method n = c.getInitializer(new jq_NameAndDesc("<init>", "()V"));
                if (n != null) {
                    visitMethod(n);
                    reflectiveCtors.add(n);
                }
            }
        }
    }

    private void processResolvedAryNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;
        reflect.addResolvedAryNewInstSite(q, r);
        visitClass(r);
        if (updateAllocClasses(r, null))
            repeat = true;
    }

    private void processResolvedConNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;
        reflect.addResolvedConNewInstSite(q, r);
        visitClass(r);
        if (updateAllocClasses(r, null))
            repeat = true;
        jq_Class c = (jq_Class) r;
        jq_InstanceMethod[] meths = c.getDeclaredInstanceMethods();
        // this is imprecise in that we are visiting all constrs instead of the called one
        // this is also unsound because we are not visiting constrs in superclasses
        for (int i = 0; i < meths.length; i++) {
            jq_InstanceMethod m = meths[i];
            if (m.getName().toString().equals("<init>")) {
                visitMethod(m);
                reflectiveCtors.add(m);
            }
        }
    }
}