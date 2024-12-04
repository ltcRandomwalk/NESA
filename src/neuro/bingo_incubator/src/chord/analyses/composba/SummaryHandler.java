package chord.analyses.composba;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class SummaryHandler {

	public String summaryDir;
	public boolean genSummaries;
	public boolean consumeSummaries;
	public boolean fullCG;
	public boolean dumpToFile;
	public boolean dumpPEs;
	
	
	private TObjectIntHashMap<String> strToHNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToVNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToMNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToFNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToINdx = new TObjectIntHashMap<String>();
	private RelTupleIndex fhIndex = new RelTupleIndex();
	private RelTupleIndex hfhIndex = new RelTupleIndex();
	private HashMap<String, int[]> mark = new HashMap<String, int[]>();
	private Map<jq_Method, Set<BitEdge<Quad>>> frontierSummEdges = new HashMap<jq_Method, Set<BitEdge<Quad>>>();
	private String libraryPrefix = "(java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
	private String appPrefix = "boofcv\\.examples\\..*";
	private Map<Pair<Quad, BitAbstractState>, ArraySet<jq_Method>> targetsMapReflModified = new HashMap<Pair<Quad, BitAbstractState>, ArraySet<jq_Method>>();
	private DomM pdomM;
	
	public HashMap<Pair<jq_Method, BitEdge<Quad>>, HeapAccessData> seToHeapAccessMap;
	public HashMap<Pair<jq_Method, BitEdge<Quad>>, HeapAccessDataBkwd> seToHeapAccessMapBkwd;
	public Map<jq_Method, ArraySet<BitEdge<Quad>>> savedSummEdges;
	public Map<jq_Method, Set<Pair<BitEdge<Quad>, HeapCondition>>> uniqSummEdges;
	public Map<Inst, ArraySet<BitEdge<Quad>>> pathEdges;
    public Map<jq_Method, ArraySet<BitEdge<Quad>>> summEdges;
    public Map<Pair<Quad, BitEdge<Quad>>, ArraySet<jq_Method>> targetsMapRefl;
    public Map<Pair<jq_Method, BitEdge<Quad>>, HeapCondition> heapCondition;
    public Map<Pair<jq_Method, BitEdge<Quad>>, Set<String>> loadedSummOrig = new HashMap<Pair<jq_Method, BitEdge<Quad>>, Set<String>>();
    public Map<Pair<jq_Method, BitEdge<Quad>>, Set<String>> loadedSummID = new HashMap<Pair<jq_Method, BitEdge<Quad>>, Set<String>>();
    public boolean simulateSuperPerf;
    public boolean superPerfIgnoreAppCallbk;
    public boolean useLibPrefix;
    public boolean stats;
    PrintWriter summWrite;
    
    public void init()
    {
    	if (genSummaries)
    		initSEDump();
    	else if (consumeSummaries)
    		initSELoad();
    	else 
    		markDom();
    	pdomM = (DomM) ClassicProject.g().getTrgt("M");
    }
     
    public void dumpAllSEs() {
		
		PrintWriter methListPW = null;
		methListPW = initFileDump();
		int totalSEs = 0;
		int longFileCnt = 0;
			
		for (jq_Method m : summEdges.keySet()) {
			Set<BitEdge<Quad>> seSet = summEdges.get(m);
            if (seSet != null) {
            	int sz = seSet.size();
            	String[] seStr = new String[sz];
            	totalSEs += sz;
            	int ndx = 0;
                for (BitEdge<Quad> se : seSet) {
                	seStr[ndx++] = se.toString();
                }
                boolean fileTooLong = dumpMethodSEsToFile(m, sz, "", seStr, null, methListPW);
                if (fileTooLong) longFileCnt++; 
            }
        }
		methListPW.close();
		System.out.println("CompoSBA: Number of files with very long names: " + longFileCnt);
		System.out.println("CompoSBA Stats: Total SEs           : " + totalSEs);
	}
    
    public PrintWriter initFrontierMatchedSEDump() {
		File sumDir = new File(summaryDir + "_frontier");
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("Summary dir " + summaryDir + "_frontier" + " does not exist - unable to create it.");
		     }        
		}
		PrintWriter methListPW;
		try {
			methListPW = new PrintWriter(summaryDir + "_frontier" + File.separator + "methodList.txt");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		return methListPW;    	
    }
    
    public void dumpFrontierMatchedSEs (PrintWriter methListPW) {
    	int fileTooLong = 0;
    	for (jq_Method m : frontierSummEdges.keySet()) {
			String methFName = strTransform(m.toString());
	        PrintWriter summPW = null;
	        DomM domM = (DomM) ClassicProject.g().getTrgt("M");
	        
	        methListPW.println(methFName + " " + domM.toUniqueString(m));
	        try {
				summPW = new PrintWriter(new File(summaryDir + "_frontier" + File.separator + methFName));
			} catch (FileNotFoundException e) {
				fileTooLong++;
			} catch (SecurityException s) {
				throw new RuntimeException(s);
			}
	        if (summPW != null) {
		        for (BitEdge<Quad> se : frontierSummEdges.get(m)) {
		        	HeapCondition hpc = heapCondition.get(new Pair<jq_Method, BitEdge<Quad>> (m, se));
		        	String hpstr = ""; // = hpc.toString();
	        		summPW.println(se.toParsableString(null) + ":HFHDAT:" + hpstr);
		        }
		        summPW.close();
	        }
    	}
    	System.out.println ("CompoSBA: dumpFrontierMatchedSEs: fileTooLong count: " + fileTooLong);
    	methListPW.close();
        return;    	
    }
    
    public boolean preloaded(jq_Method m, BitEdge<Quad> seCurr) {
    	Set<BitEdge<Quad>> seSet = savedSummEdges.get(m);
        if (seSet != null) {
            for (BitEdge<Quad> se : seSet) {
            	if (se.equals(seCurr)) {
            		Set<BitEdge<Quad>> fseSet;
            		if (frontierSummEdges.containsKey(m)) {
            			fseSet = frontierSummEdges.get(m);
            		} else {
            			fseSet = new HashSet<BitEdge<Quad>>();
            			frontierSummEdges.put(m,  fseSet);
            		}
            		fseSet.add(se);
            		return true;
            	}
            }
        }
        return false;
    }
    
    
    private void createModifiedTargetsMap() {
    	for (Pair<Quad, BitEdge<Quad>> pr : targetsMapRefl.keySet()) {
    		ArraySet<jq_Method> tgts = targetsMapRefl.get(pr);
    		BitAbstractState src = pr.val1.srcNode;
    		targetsMapReflModified.put(new Pair<Quad, BitAbstractState> (pr.val0, src), tgts);
    	}
    	return;
    }
    
    public boolean hasAppCallback(jq_Method m, DomM domM, BitEdge<Quad> se) {
    	ControlFlowGraph cfg = m.getCFG();
    	for (BasicBlock bb : cfg.reversePostOrder()) {
    		for (int i = 0; i < bb.size(); i++) {
    			Quad q = bb.getQuad(i);
    			if (q.getOperator() instanceof Invoke) {
    				Set<jq_Method> targets = targetsMapReflModified.get(new Pair<Quad, BitAbstractState> (q, se.srcNode));
    				if (targets == null) return false;
    				for (jq_Method tgtm : targets) {
    					int idx = domM.indexOf(tgtm);
    					if (!mBelongsToLib(idx)) {
    						return true;
    					}
    				}
    			}
    		}
    	}
    	return false;
    }
    
    public void dumpRecomputeStats() {
		int totalRecomputedSEs = 0;
		int libRecomputedSEs = 0;
		int preLoadedLibSEs = 0;
		int libSEsWithAppAS = 0;
		int libSEsWithAppCallbk = 0;
		int libSEsNotFoundInPreLoaded = 0;
	
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		createModifiedTargetsMap();
		
		for (jq_Method m : summEdges.keySet()) {
			int idx = domM.indexOf(m);
			Set<BitEdge<Quad>> seSet = summEdges.get(m);
			if (mBelongsToLib(idx)) {
	            if (seSet != null) {
	                for (BitEdge<Quad> se : seSet) {
	                	if (!preloaded (m, se)) {
	                		libRecomputedSEs++;
	                		totalRecomputedSEs++;
	                		if (!hasAppCallback(m, domM, se)) {
	                			if (se.isAppAccessed(null))
	                				libSEsWithAppAS++;
	                			else
	                				libSEsNotFoundInPreLoaded++;
	                		} else 
	                			libSEsWithAppCallbk++;
	                	} else
	                		preLoadedLibSEs++;
	                }
	            }
			} else {
				totalRecomputedSEs += seSet.size();
			}
        }
		
		PrintWriter methListPW = initFrontierMatchedSEDump();
		dumpFrontierMatchedSEs (methListPW);
		
		System.out.println("CompoSBA Summary Reuse Stats: Total recomputed SEs                               : " + totalRecomputedSEs);
		System.out.println("CompoSBA Summary Reuse Stats: Preloaded lib SEs                                  : " + preLoadedLibSEs);
		System.out.println("CompoSBA Summary Reuse Stats: Lib recomputed SEs                                 : " + libRecomputedSEs);
		System.out.println("CompoSBA Summary Reuse Stats: Lib recomupted SEs with app callbk                 : " + libSEsWithAppCallbk);
		System.out.println("CompoSBA Summary Reuse Stats: Lib recomputed SEs with abstract state touching app: " + libSEsWithAppAS);
		System.out.println("CompoSBA Summary Reuse Stats: Lib recomputed SEs (no callbk and not touching app): " + libSEsNotFoundInPreLoaded);
    	return;
    }
    
	public void dumpLibSEsFiltered() {
		
		PrintWriter methListPW = null;
		if (dumpToFile) {
			methListPW = initFileDump();
			// Note: the order in which the four methods below are called matters
			dumpHNdxToStr(methListPW);
			dumpFNdxToStr(methListPW);
			dumpINdxToStr(methListPW);
			dumpMNdxToStr(methListPW);
		}
		
		int longFileCnt = 0;
		int totalFuncs = 0;
		int libFuncs = 0;
		int totalSEs = 0;
		int libSEs = 0;
		int dumpedLibFuncs = 0;
		int dumpedLibSEs = 0;
		
		int libSEsWithAppAS = 0;
		int libSEsWithoutAppAS = 0;
		int libSEsWithAppHeap = 0;
		int libSEsWithoutAppHeap = 0;
		int libSEsWithAppCallbk = 0;
		int libSEsWithoutAppCallbk = 0;
		int nullHpt = 0;
		int callbkPresAppAbsHpPres = 0;
		int callbkPresAppAbsHpAbs = 0;
		
		int numMethodsWithAppCallbkFullCG = 0;
		int libSEsWithAppCallbkFullCG = 0;
		
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		
		for (jq_Method m : summEdges.keySet()) {
			int idx = domM.indexOf(m);
			Set<BitEdge<Quad>> seSet = summEdges.get(m);
			totalFuncs++;
			totalSEs += seSet.size();
			if (mBelongsToLib(idx)) {
				libFuncs++;
	            libSEs += seSet.size();
	            CallGraphCondition cgc = new CallGraphCondition();
	            if (fullCG) {
					cgc.createCondnStr(m);
					if (cgc.hasAppCallbk && 
						(!simulateSuperPerf || (simulateSuperPerf && !superPerfIgnoreAppCallbk))) {
						numMethodsWithAppCallbkFullCG++;
						libSEsWithAppCallbkFullCG += seSet.size();
						continue;
					}
	            }
	            if (seSet != null) {
	            	int sz = seSet.size();
	            	String[] seStr = new String[sz];
	            	String[] hpStr = new String[sz];
	            	int actual = 0;

	                for (BitEdge<Quad> se : seSet) {
	                	boolean seCondn;
	                	HeapAccessDataBkwd hp = seToHeapAccessMapBkwd.get(new Pair<jq_Method, BitEdge<Quad>>(m, se));
	                	if (simulateSuperPerf) {
	                		if (hp != null) {
	                			if (hp.appCallbkPres && !superPerfIgnoreAppCallbk) {
	                				// do nothing
	                			} else {
	                				seStr[actual] = se.toParsableString(hp.vSet);
		                			hpStr[actual] = hp.toParsableString();
		                			actual++;
	                			}
	                		} else {
	                			HeapAccessDataBkwd hp_temp = new HeapAccessDataBkwd();
	                			seStr[actual] = se.toParsableString(null);
	                			hpStr[actual] = hp_temp.toParsableString();
	                			actual++;
	                		}
	                	} else if (hp != null) { // not simulating superperfect
		                	seCondn = !se.isAppAccessed(hp.vSet); 
		                	
		                	if (seCondn) {
		                		libSEsWithoutAppAS++;
		                		String str = "";
		                		str = hp.toParsableString();
		                		boolean hpCondn = hp.appCallbkPres == false && hp.appAccessPres == false;
		                		if (hpCondn) {
		                			seStr[actual] = se.toParsableString(hp.vSet);
		                			hpStr[actual] = str;
			                		assert (!se.dstNode.isAppAccessedInRetEnv());
			                		actual++;
		                		} else {
									if (hp.appCallbkPres == true) {
										if (hp.appAccessPres)
											callbkPresAppAbsHpPres++;
										else {
											callbkPresAppAbsHpAbs++;
										}
									}
								}
		                	} else { // seCond == false
		                		libSEsWithAppAS++;
		                	}
		                	
		                	if (hp.appCallbkPres) {
	                			libSEsWithAppCallbk++;		
	                		} else
	                			libSEsWithoutAppCallbk++;
	                		if (hp.appAccessPres)
	                			libSEsWithAppHeap++;
	                		else
	                			libSEsWithoutAppHeap++;
	                	} else { // hp == null
	                		nullHpt++;
	                	}
	                }
	                
	                if (actual > 0) {
	                	dumpedLibFuncs++;
	                	dumpedLibSEs += actual;
	                	if (dumpToFile) {
	                		boolean fileTooLong = dumpMethodSEsToFile(m, actual, cgc.cStr, seStr, hpStr, methListPW);
	                		if (fileTooLong) longFileCnt++; 
	                	} 
	                }
	            }
			}
        }
		methListPW.close();
		System.out.println("CompoSBA: Number of files with very long names         : " + longFileCnt);
		System.out.println("CompoSBA Stats: Total functions                        : " + totalFuncs);
		System.out.println("CompoSBA Stats: Total SEs                              : " + totalSEs);
		System.out.println("CompoSBA Stats: Lib functions                          : " + libFuncs);
		System.out.println("CompoSBA Stats: Lib SEs                                : " + libSEs);
		if (fullCG) {
			System.out.println("CompoSBA Stats: Num Lib funcs with app callbk (FullCG) : " + numMethodsWithAppCallbkFullCG);
			System.out.println("CompoSBA Stats: LibSEs with app callback (FullCG)      : " + libSEsWithAppCallbkFullCG);
		}
		System.out.println("CompoSBA Stats: Dumped Lib functions                   : " + dumpedLibFuncs);
		System.out.println("CompoSBA Stats: Dumped Lib SEs                         : " + dumpedLibSEs);
		
		System.out.println("CompoSBA Stats: LibSEs with abstract state touching app      : " + libSEsWithAppAS);
		System.out.println("CompoSBA Stats: LibSEs with abstract state not touching app  : " + libSEsWithoutAppAS);
		System.out.println("CompoSBA Stats: LibSEs with heap access touching app         : " + libSEsWithAppHeap);
		System.out.println("CompoSBA Stats: LibSEs with heap access not touching app     : " + libSEsWithoutAppHeap);
		System.out.println("CompoSBA Stats: LibSEs with app callback                     : " + libSEsWithAppCallbk);
		System.out.println("CompoSBA Stats: LibSEs with no app callback                  : " + libSEsWithoutAppCallbk);
		System.out.println("CompoSBA Stats: LibSEs with null hpt                         : " + nullHpt);
		System.out.println("CompoSBA Stats: Callbk pres app access absent hp access pres : " + callbkPresAppAbsHpPres);
		System.out.println("CompoSBA Stats: Callbk pres app access absent hp access abs  : " + callbkPresAppAbsHpAbs);
	}
	
	
	public void loadSavedSEs() {
    	Timer timerLoad = new Timer("Summary Loading");
    	timerLoad.init();
    	int loadSECnt = 0;
    	boolean fromDisk = dumpToFile;
    	
    
    	if (fromDisk) {
    		if (summaryDir.equals("")) return;
    		String appListFile = summaryDir + File.separator + "appList.txt";
    		File mFile = new File(appListFile);
    		if (mFile.exists()) {
    			try {
    				summWrite = OutDirUtils.newPrintWriter("db_summ_ids");
    				Scanner sc = new Scanner(mFile);
    				while (sc.hasNext()) {
    					String line = sc.nextLine().trim();
    					String[] parts = line.split(":");
    					// parts[0] contains the location of methodList.txt (with full path)
    					//parts[1] is the full-path-dir relative to which methodList.txt records the method files.
    					// Ex: /home/..../tempDir/summary_lusearch/methodList.txt:/home/.../tempDir/summary_lusearch/dummy
    					// The above means that the files listed in methodList.txt are in dummy dir.
    					ArrayList<String> methodSumms = null;
    		    		methodSumms = getMethodsWithAvailableSEs(parts[0].trim(), parts[1].trim());
    		    		if (methodSumms != null) loadSECnt += loadSEsFromMethods(methodSumms, parts[1].trim());
    				}
    				sc.close();
    				summWrite.close();
    				
    			} catch (FileNotFoundException e) {
    				throw new RuntimeException(e);
    			}
    		}	
    	}
    	
    	// loaded summ stats
    	if (stats) {
    		int total = 0;
			int trivialReturn = 0;
			int vCond = 0;
			int vCondWild = 0;
			int FHCond = 0;
			int HFHCond = 0;
			int IMCond = 0;
			for (jq_Method m : uniqSummEdges.keySet()) {
	    		Set<Pair<BitEdge<Quad>, HeapCondition>> seSet = uniqSummEdges.get(m);
	    		boolean isTrivialReturn = m.getReturnType().isPrimitiveType();
	    		int argCnt = 0;
	    		for (jq_Type t : m.getParamTypes()) {
	    			if (!t.isPrimitiveType()) argCnt++;
	    		}
	    		int IMCondSize = 0;
	    		if (CallGraphCondition.IMReachableFromM.get(m) != null)
	    			IMCondSize = CallGraphCondition.IMReachableFromM.get(m).size();
	            if (seSet != null) {
	                for (Pair<BitEdge<Quad>, HeapCondition> se : seSet) {
	                	total++;
	                	if (isTrivialReturn) trivialReturn++;
	                    HeapCondition hc = se.val1;
	                    for (jq_Field f :hc.seFHMap.keySet()) {
	                    	FHCond += hc.seFHMap.get(f).cardinality();
	                    }
	                    for (Pair<Integer, jq_Field> p : hc.seHFHMap.keySet()) {
	                    	HFHCond += hc.seHFHMap.get(p).cardinality();
	                    }
	                    int wildcardArg = 0;
	                    for (Register v : se.val0.srcNode.envLocal.getKeySet()) {
	                    	if (se.val0.srcNode.envLocal.get(v) == BitAbstractState.markerBitSet) wildcardArg++;
	                    }
	                    vCond += argCnt;
	                    vCondWild += wildcardArg;
	                    IMCond += IMCondSize;
	                }
	            }
	    	}
			System.out.println("CompoSBA: Total loaded summaries: " + total);
			System.out.println("CompoSBA: Trivial loaded summaries: " + trivialReturn);
			System.out.println("CompoSBA: Total vCond size: " + vCond + ", Average: " + ((double)vCond)/total);
			System.out.println("CompoSBA: Total vCondWildcard size: " + vCondWild + ", Average: " + ((double)vCondWild)/total);
			System.out.println("CompoSBA: Total IMCond size: " + IMCond + ", Average: " + ((double)IMCond)/total);
			System.out.println("CompoSBA: Total FHCond size: " + FHCond + ", Average: " + ((double)FHCond)/total);
			System.out.println("CompoSBA: Total HFHCond size: " + HFHCond + ", Average: " + ((double)HFHCond)/total);
			
			uniqSummEdges.clear();
			uniqSummEdges = null;
    	}
    	
		timerLoad.done();
		long timeToLoad = timerLoad.getInclusiveTime();
		
		// Set the following to null since summary loading is over and they are no longer required.
		HeapCondition.hNdxToStrTrainApp = null;
		HeapCondition.fNdxToStrTrainApp = null;
		BitAbstractState.hNdxToStrTrainApp = null;
		
		System.out.println("AllocEnvCFA: SUMMARY LOAD TIME: "+ Timer.getTimeStr(timeToLoad));
		System.out.println("AllocEnvCFA: LOADED " + loadSECnt + " summary edges");
	}
	
	
	private PrintWriter initFileDump() {
		File sumDir = new File(summaryDir);
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("Summary dir " + summaryDir + " does not exist - unable to create it.");
		     }        
		}
		PrintWriter appListPW;
		try {
			appListPW = new PrintWriter(new File(summaryDir + File.separator + "appList.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		appListPW.println(summaryDir + File.separator + "methodList.txt:" + summaryDir);
		appListPW.close();
		
		PrintWriter methListPW;
		FileWriter fw;
		try {
			fw = new FileWriter(summaryDir + File.separator + "methodList.txt", false);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
		methListPW = new PrintWriter(fw);
		return methListPW;
	}
	
	private boolean dumpMethodSEsToFile(jq_Method m, int len, String cgcStr, String[] seStr, String[] hpStr, PrintWriter methListPW) {
		boolean fileTooLong = false;
		String methFName = strTransform(m.toString());
        PrintWriter summPW = null;
        String fname;
        DomM domM = (DomM) ClassicProject.g().getTrgt("M");
        
        fname = methFName;
        methListPW.println(fname + " " + domM.toUniqueString(m));
        try {
			summPW = new PrintWriter(new File(summaryDir + File.separator + fname));
		} catch (FileNotFoundException e) {
			fileTooLong = true;
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
        if (summPW != null) {
        	if (cgcStr != null)
        		summPW.println("CG_CONDITION:" + cgcStr);
	        for (int i = 0; i < len; i++) {
	        	if (hpStr != null)
	        		summPW.println(seStr[i] + hpStr[i]);
	        	else
	        		summPW.println(seStr[i]);
	        }
	        summPW.close();
        }
        return fileTooLong;
	}
	
	private String strTransform(String mName) {
		String mFName = new String();
		mFName = mName;
		mFName = mFName.replaceAll("/", ".");
		mFName = mFName.replaceAll(";", ",");
		mFName = mFName.replaceAll("\\(", "[");
		mFName = mFName.replaceAll("\\)", "]");
		mFName = mFName.replaceAll("\\$", "#");
		mFName = mFName.replaceAll(">", "]");
		mFName = mFName.replaceAll("<", "[");
        return mFName;
	}
	
	private void populateRelTupleIndex(ProgramRel r, RelTupleIndex rti) {
		for (int[] args : r.getAryNIntTuples())
			rti.getIndex(new Tuple(r, args));
	}
	
	private void initSEDump() {
		markDom();
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		BitAbstractState.domV = domV;
		BitAbstractState.mark = mark;
		
		ProgramRel relFH = (ProgramRel) ClassicProject.g().getTrgt("FH");
		relFH.load();
		fhIndex = new RelTupleIndex();
		populateRelTupleIndex(relFH, fhIndex);
		relFH.close();
		
		ProgramRel relHFH = (ProgramRel) ClassicProject.g().getTrgt("HFH");
		relHFH.load();
		hfhIndex = new RelTupleIndex();
		populateRelTupleIndex(relHFH, hfhIndex); 
		relHFH.close();
		
		HeapAccessData.hfhIndex = hfhIndex;
		HeapAccessData.fhIndex = fhIndex;
		
		InfiCFAHeapAccessQuadVisitor.HFHIndex = hfhIndex;
		InfiCFAHeapAccessQuadVisitor.FHIndex = fhIndex;
		InfiCFAHeapAccessQuadVisitor.mark = mark;

		HeapAccessDataBkwd.mark = mark;
		HeapAccessDataBkwd.init();
		InfiCFAHeapAccessQuadVisitorBkwd.mark = mark;
		
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		CallGraphCondition.domI = domI;
		CallGraphCondition.domM = domM;
		CallGraphCondition.mark = mark;
	}
	
	private void dumpHNdxToStr(PrintWriter methListPW) {
		PrintWriter hNdxToStrPW;
		try {
			hNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "hNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		for (int i = 0; i < domH.size(); i++) {
			hNdxToStrPW.println(domH.toUniqueString(i));
		}
		hNdxToStrPW.close();
		methListPW.println("HNDXTOSTR:hNdxToStr.txt");
	}
	
	private void dumpFNdxToStr(PrintWriter methListPW) {
		PrintWriter fNdxToStrPW;
		try {
			fNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "fNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		for (int i = 0; i < domF.size(); i++) {
			fNdxToStrPW.println(domF.toUniqueString(i));
		}
		fNdxToStrPW.close();
		methListPW.println("FNDXTOSTR:fNdxToStr.txt");
	}
	
	private void dumpINdxToStr(PrintWriter methListPW) {
		PrintWriter iNdxToStrPW;
		try {
			iNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "iNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		for (int i = 0; i < domI.size(); i++) {
			iNdxToStrPW.println(domI.toUniqueString(i));
		}
		iNdxToStrPW.close();
		methListPW.println("INDXTOSTR:iNdxToStr.txt");
	}
	
	private void dumpMNdxToStr(PrintWriter methListPW) {
		PrintWriter mNdxToStrPW;
		try {
			mNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "mNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		for (int i = 0; i < domM.size(); i++) {
			mNdxToStrPW.println(domM.toUniqueString(i));
		}
		mNdxToStrPW.close();
		methListPW.println("MNDXTOSTR:mNdxToStr.txt");
	}
	
	private void loadHNdxToStrTrainApp (String fname) {
		ArrayList<String> hNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					hNdxToStrTrainApp.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		HeapCondition.hNdxToStrTrainApp = hNdxToStrTrainApp;
		BitAbstractState.hNdxToStrTrainApp = hNdxToStrTrainApp;
	}
	
	private void loadFNdxToStrTrainApp (String fname) {
		ArrayList<String> fNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					fNdxToStrTrainApp.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		HeapCondition.fNdxToStrTrainApp = fNdxToStrTrainApp;
	}
	
	private void loadINdxToStrTrainApp (String fname) {
		ArrayList<String> iNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					iNdxToStrTrainApp.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		CallGraphCondition.iNdxToStrTrainApp = iNdxToStrTrainApp;
	}
	
	private void loadMNdxToStrTrainApp (String fname) {
		ArrayList<String> mNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					mNdxToStrTrainApp.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		CallGraphCondition.mNdxToStrTrainApp = mNdxToStrTrainApp;
	}
	
	private void initSELoad() {
		markDom();
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		BitAbstractState.domV = domV;
		BitAbstractState.mark = mark;

		for (int i = 0; i < domV.size(); i++) { 
			strToVNdx.put(domV.toUniqueString(i), i);
		}
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		for (int i = 0; i < domH.size(); i++) {
			strToHNdx.put(domH.toUniqueString(i), i);
		}
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		for (int i = 0; i < domM.size(); i++) {
			strToMNdx.put(domM.toUniqueString(i), i);
		}
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		for (int i = 0; i < domF.size(); i++) {
			strToFNdx.put(domF.toUniqueString(i), i);
		}
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		for (int i = 0; i < domI.size(); i++) {
			strToINdx.put(domI.toUniqueString(i), i);
		}
		BitAbstractState.domV = domV;
		BitAbstractState.strToHNdx = strToHNdx;
		BitAbstractState.strToVNdx = strToVNdx;
		HeapCondition.strToHNdx = strToHNdx;
		HeapCondition.strToFNdx = strToFNdx;
		HeapCondition.init();
		CallGraphCondition.strToINdx = strToINdx;
		CallGraphCondition.strToMNdx = strToMNdx;
		CallGraphCondition.domI = domI;
		CallGraphCondition.domM = domM;
		CallGraphCondition.mark = mark;
		
		uniqSummEdges = new HashMap<jq_Method,Set<Pair<BitEdge<Quad>, HeapCondition>>>();
	}
	
	
	private ArrayList<String> getMethodsWithAvailableSEs(String methFile, String dirName) {
		ArrayList<String> methodSumms = new ArrayList<String>();
		File mFile = new File(methFile);
		if (mFile.exists()) {
			int lineCnt = 0;
			System.out.println("LOADING SUMMARIES FROM: " + methFile);
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (lineCnt == 0) {
						// parse HNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("HNDXTOSTR")) {
							System.out.println("Illformed hNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's hNdxToStr file and set it in HeapCondition
						String hNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("hNdxToStrFName: " + hNdxToStrFName);
						loadHNdxToStrTrainApp(hNdxToStrFName);
					} else if (lineCnt == 1) {
						// parse FNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("FNDXTOSTR")) {
							System.out.println("Illformed fNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's fNdxToStr file and set it in HeapCondition
						String fNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("fNdxToStrFName: " + fNdxToStrFName);
						loadFNdxToStrTrainApp(fNdxToStrFName);
					} else if (lineCnt == 2) {
						// parse INDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("INDXTOSTR")) {
							System.out.println("Illformed iNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's iNdxToStr file and set it in CallGraphCondition
						String iNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("iNdxToStrFName: " + iNdxToStrFName);
						loadINdxToStrTrainApp(iNdxToStrFName);
					} else if (lineCnt == 3) {
						// parse MNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("MNDXTOSTR")) {
							System.out.println("Illformed mNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's mNdxToStr file and set it in CallGraphCondition
						String mNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("mNdxToStrFName: " + mNdxToStrFName);
						loadMNdxToStrTrainApp(mNdxToStrFName);
					} else {
						methodSumms.add(line);
					}
					lineCnt++;
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		return methodSumms;
	}
	
	
	private int loadSEsFromMethods(ArrayList<String> methodSumms, String mSumDir) {
		String[] pathSplit = mSumDir.split("/");
		String appName = pathSplit[pathSplit.length-1];
		int loadSECnt = 0;
		int duplicateCnt = 0;
		int seValidationFailCnt = 0;
		int cgMatchFailCnt = 0;
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");

		int idxInMethFile = -1;
		for (String summaryFile: methodSumms) {
			idxInMethFile++;
			String[] parts = summaryFile.split(" ");
			if (strToMNdx.containsKey(parts[1])) {
				int ndx = strToMNdx.get(parts[1]);
				jq_Method m = domM.get(ndx);
				
				//System.out.println("LOADING SUMMARIES FROM: " + parts[0]);
				try {
					File f = new File(mSumDir + File.separator + parts[0]);
					if (f.exists()) {
						Scanner sc = new Scanner(f);
						int idxInSummFile = -1;
						while (sc.hasNext()) {
							idxInSummFile++;
							String line = sc.nextLine().trim();
							if (line.startsWith("CG_CONDITION:")) {
								String[] cgParts = line.split("CG_CONDITION:");
								CallGraphCondition cgc = new CallGraphCondition();
								cgc.parse(cgParts[1]);
								if (!cgc.badValue && cgc.isCGMatching(m)) {
									continue;
								} else {
									// The call graph condition did not parse well or did not match - discontinue loading 
									// the summaries for this method and go to next method.
									cgMatchFailCnt++;
									break;
								}
							}
							String[] seAndHpParts = line.split(":HFHDAT:");
							BitEdge<Quad> se = new BitEdge<Quad>(seAndHpParts[0]);
							HeapCondition hp = new HeapCondition(seAndHpParts[1]);
							if (!se.isBadValue() && !hp.isBadValue()) {
								ArraySet<BitEdge<Quad>> seOfM;
								if (savedSummEdges.containsKey(m))
									seOfM = savedSummEdges.get(m);
								else {
									seOfM = new ArraySet<BitEdge<Quad>>();
									savedSummEdges.put(m, seOfM);
								}
								boolean retval = seOfM.add(se);
								boolean isSummloaded = false;
								boolean isSummUniq = false;
								String seID = appName+":"+idxInMethFile+":"+idxInSummFile;
								Pair<jq_Method,BitEdge<Quad>> pr = new Pair<jq_Method,BitEdge<Quad>>(m, se);
								
								if (retval) {
									heapCondition.put(pr, hp);
									loadSECnt++;
									isSummloaded = true;
									isSummUniq = true;
								} else {
									duplicateCnt++;
									if (hp.equals(heapCondition.get(pr))) {
										isSummloaded = true;
									} else {
										isSummUniq = true; 
									}
								}
								if (isSummUniq) {
									summWrite.println(seID);
									if (stats) {
										Set<Pair<BitEdge<Quad>, HeapCondition>> uniqSEs = uniqSummEdges.get(m);
										if (uniqSEs == null) {
											uniqSEs = new HashSet<Pair<BitEdge<Quad>, HeapCondition>>();
											uniqSummEdges.put(m, uniqSEs);
										}
										uniqSEs.add(new Pair<BitEdge<Quad>, HeapCondition>(se,hp));
									}
								}
								if (isSummloaded) {
									Set<String> summOrig = loadedSummOrig.get(pr);
									if (summOrig == null) {
										summOrig = new HashSet<String>();
										loadedSummOrig.put(pr, summOrig);
									}
									summOrig.add(appName);
									
									Set<String> summIDs = loadedSummID.get(pr);
									if (summIDs == null) {
										summIDs = new HashSet<String>();
										loadedSummID.put(pr, summIDs);
									}
									summIDs.add(seID);
								}
							} else {
								//System.out.println("se badValue: " + se.isBadValue() + "   hp badValue: " + hp.isBadValue());
								seValidationFailCnt++;
							}
						}
						sc.close();
					}
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		}
		System.out.println("CompoSBA: duplicate count: " + duplicateCnt);
		System.out.println("CompoSBA: se validation fail count: " + seValidationFailCnt);
		System.out.println("CompoSBA: call graph match fail count: " + cgMatchFailCnt);
		return loadSECnt;
	}
	
	
	private void markDom()
	{
		List<Dom> domArray = new ArrayList<Dom>();
		domArray.add((DomH) ClassicProject.g().getTrgt("H"));
		domArray.add((DomM) ClassicProject.g().getTrgt("M"));
		domArray.add((DomF) ClassicProject.g().getTrgt("F"));
		
		for(Dom dom : domArray) {
			if (!mark.containsKey(dom.getName())) {
				int[] markVec = new int[dom.size()];
				markDomain(dom, markVec);
				mark.put(dom.getName(), markVec);
			}
		}	
	}
	
	private void markDomain(Dom dom, int[] vec) {
		if(dom.getName().equals("H")) {
			DomH domH = (DomH)dom;
			vec[0] = 2;  // Library
			for (int i = 1; i < domH.size(); i++) {
				Quad q = (Quad) domH.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
				if (isLib)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("M")){
			DomM domM = (DomM)dom;
			vec[0] = 4;
			for (int i = 1; i < domM.size(); i++) { 
				jq_Class cl = domM.get(i).getDeclaringClass();
				String clName = cl.toString();
				boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
				if (isLib)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("F")){
			DomF domF = (DomF)dom;
			vec[0] = 2;
			for (int i = 1; i < domF.size(); i++) {
				jq_Class cl = domF.get(i).getDeclaringClass();
				String clName = cl.toString();
				boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
				if (isLib)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("I")){
			DomI domI = (DomI)dom;
			for (int i = 0; i < domI.size(); i++) {
				Quad q = (Quad) domI.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
				if (isLib)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
	}
	
	private boolean mBelongsToLib(int i){
		int type = 0;
		if (mark.containsKey("M")) {
			type |= ((int[])mark.get("M"))[i];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}

	public boolean mBelongsToLib(jq_Method m) {
		int idx = pdomM.indexOf(m);
		return mBelongsToLib(idx);
	}
	
	public boolean iBelongsToLib(Inst i) {
		jq_Method m = i.getMethod();
		int idx = pdomM.indexOf(m);
		return mBelongsToLib(idx);
	}
}
