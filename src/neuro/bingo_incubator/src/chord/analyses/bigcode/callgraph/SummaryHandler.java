package chord.analyses.bigcode.callgraph;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;

public class SummaryHandler {

	public String summaryDir;
	public boolean genSummaries;
	public boolean consumeSummaries;
	public boolean dumpToFile;
	
	
	private TObjectIntHashMap<String> strToMNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToINdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToTNdx = new TObjectIntHashMap<String>();
	
	private HashMap<Integer, jq_Method> mNdxToM = new HashMap<Integer, jq_Method>();
	private HashMap<Integer, Quad> iNdxToI = new HashMap<Integer, Quad>();
	private HashMap<Integer, jq_Type> tNdxToT = new HashMap<Integer, jq_Type>();
	
	private HashMap<Integer, Integer> mNdxToMNdx = new HashMap<Integer, Integer>();
	private HashMap<Integer, Integer> iNdxToINdx = new HashMap<Integer, Integer>();
	private HashMap<Integer, Integer> tNdxToTNdx = new HashMap<Integer, Integer>();
	
	private HashMap<String, int[]> mark = new HashMap<String, int[]>();
	private String libraryPrefix = "(java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|com\\.oracle\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.|org\\.jcp\\.|joeq\\.).*";
	private String appPrefix = "boofcv\\.examples\\..*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	private DomM domM;
	private DomI domI;
	private DomT domT;
	
	public Map<Summary, ArraySet<jq_Method>> savedSummaries;
	public Map<jq_Method, ArraySet<Summary>> loadedSummaries;
    public boolean simulateSuperPerf;
    public boolean superPerfIgnoreAppCallbk;
    public boolean useLibPrefix;
    
    public Map<Pair<jq_Method, Summary>, Set<String>> loadedSummOrig = new HashMap<Pair<jq_Method, Summary>, Set<String>>();
    public Map<Pair<jq_Method, Summary>, Set<String>> loadedSummID = new HashMap<Pair<jq_Method, Summary>, Set<String>>();
    PrintWriter summWrite;

    public void init()
    {
    	domM = (DomM) ClassicProject.g().getTrgt("M");
    	domI = (DomI) ClassicProject.g().getTrgt("I");
    	domT = (DomT) ClassicProject.g().getTrgt("T");
    	
    	if (genSummaries)
    		initSEDump();
    	else if (consumeSummaries)
    		initSELoad();
    	else 
    		markDom();
    }
    
	public void dumpLibSEsFiltered() {
		
		PrintWriter methListPW = null;
		if (dumpToFile) {
			methListPW = initFileDump();
			// Note: the order in which the four methods below are called matters
			dumpMNdxToStr(methListPW);
			dumpINdxToStr(methListPW);
			dumpTNdxToStr(methListPW);
		}
		
		int totalFuncs = 0;
		int libFuncs = 0;
		int totalSEs = 0;
		int libSEs = 0;
		int dumpedLibFuncs = 0;
		int dumpedLibSEs = 0;
		
		int libSEsWithAppAS = 0;
		int libSEsWithoutAppAS = 0;
		int libSEsWithAppCallbk = 0;
		int libSEsWithoutAppCallbk = 0;
		int callbkPresAppAbs = 0;
		
		int summID = -1;
		Set<Integer> totalFuncSet = new HashSet<Integer>();
		Set<Integer> libFuncSet = new HashSet<Integer>();
		Set<jq_Method> dumpedFuncSet = new HashSet<jq_Method>();
		for (Summary s0 : savedSummaries.keySet()) {
			Set<jq_Method> applicableMeths = savedSummaries.get(s0);
			Set<jq_Method> relMeths = new ArraySet<jq_Method>();
			for (jq_Method meth : applicableMeths) {
//				totalFuncs++;
				totalSEs ++;
				int mIdx = domM.indexOf(meth);
				totalFuncSet.add(mIdx);
				if (mBelongsToLib(mIdx)) {
//					libFuncs++;
					libFuncSet.add(mIdx);
					libSEs ++;
					relMeths.add(meth);
				} else {
//					System.out.println("App function: " + sccM);
				}
			}
//			if (!relMeths.isEmpty() && relMeths.size() != applicableMeths.size()) {
//				System.out.println("SCC has both, lib and app, functions but no app access!");
//				System.out.println("RellSCC size: " + relMeths.size() + ", Scc size: " + applicableMeths.size());
//				for (jq_Method mi : applicableMeths) {
//					System.out.println(mi);
//				}
//				System.out.println(s0.toParsableString());
//			}

			
			if (!relMeths.isEmpty()) {
				summID++;
				String seStr = null;
				boolean seCondn;
				if (simulateSuperPerf) {
					if (!s0.IsCallbkPres()) {
						seStr = s0.toParsableString();
					}
				} else { // not simulating superperfect
					seCondn = !s0.isAppAccessed();
					if (seCondn) {
						assert !s0.IsCallbkPres();
						libSEsWithoutAppAS += relMeths.size();
						seStr = s0.toParsableString();
					} else { // seCond == false
						libSEsWithAppAS += relMeths.size();
					}
				}
				if (s0.IsCallbkPres()) {
					libSEsWithAppCallbk += relMeths.size();
					if (!s0.isAppAccessed()) callbkPresAppAbs += relMeths.size();
				} else {
					libSEsWithoutAppCallbk += relMeths.size();
				}              
				if (seStr != null) {
//					dumpedLibFuncs += relMeths.size();
					dumpedFuncSet.addAll(relMeths);
					dumpedLibSEs++;
					if (dumpToFile) {
						dumpMethodSEsToFile(summID, relMeths, seStr, methListPW);
					} 
				}
			}
		}
		methListPW.close();
		System.out.println("CompoSBA Stats: Total functions                        : " + totalFuncSet.size());
		System.out.println("CompoSBA Stats: Total SEs                              : " + totalSEs);
		System.out.println("CompoSBA Stats: Lib functions                          : " + libFuncSet.size());
		System.out.println("CompoSBA Stats: Lib SEs                                : " + libSEs);
		System.out.println("CompoSBA Stats: Dumped Lib functions                   : " + dumpedFuncSet.size());
		System.out.println("CompoSBA Stats: Dumped Lib SEs                         : " + dumpedLibSEs);
		
		System.out.println("CompoSBA Stats: LibSEs with abstract state touching app      : " + libSEsWithAppAS);
		System.out.println("CompoSBA Stats: LibSEs with abstract state not touching app  : " + libSEsWithoutAppAS);
		System.out.println("CompoSBA Stats: LibSEs with app callback                     : " + libSEsWithAppCallbk);
		System.out.println("CompoSBA Stats: LibSEs with no app callback                  : " + libSEsWithoutAppCallbk);
		System.out.println("CompoSBA Stats: Callbk pres app access absent                : " + callbkPresAppAbs);
		
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
    	{
    		int total = 0;
			int tCond = 0;
			int subCond = 0;
			int subCond2 = 0;
			for (jq_Method m : loadedSummaries.keySet()) {
	    		Set<Summary> seSet = loadedSummaries.get(m);
	            if (seSet != null) {
	                for (Summary se : seSet) {
	                	total++;
//	                	tCond += se.srcNode.reachT.cardinality();
	                	tCond += se.srcNode.reachTL.size();	    
//	                	subCond2 += se.srcNode.subTypes.size();
//	            		for (TIntObjectIterator<BitSet> iter = se.srcNode.subTypes.iterator(); iter.hasNext();) {
//	            			iter.advance();
//	                    	subCond += iter.value().cardinality();
//	                    }
	                	subCond2 += se.srcNode.subTypesL.size();
	            		for (jq_Reference superT : se.srcNode.subTypesL.keySet()) {
	                    	subCond += se.srcNode.subTypesL.get(superT).size();
	                    }
	                }
	            }
	    	}
			System.out.println("CompoSBA: Total loaded summaries: " + total);
			System.out.println("CompoSBA: Total tCond size: " + tCond + ", Average: " + ((double)tCond)/total);
			System.out.println("CompoSBA: Total subCond size: " + subCond + ", Average: " + ((double)subCond)/total);
			System.out.println("CompoSBA: Total subTypes keyset size: " + subCond2 + ", Average: " + ((double)subCond2)/total);
    	}
    	
		timerLoad.done();
		long timeToLoad = timerLoad.getInclusiveTime();
		
		// Set the following to null since summary loading is over and they are no longer required.
		AbstractState.mNdxToStrTrainApp = null;
		AbstractState.iNdxToStrTrainApp = null;
		AbstractState.tNdxToStrTrainApp = null;
		
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
	
	private void dumpMethodSEsToFile(int seID, Set<jq_Method> scci, String seStr, PrintWriter methListPW) {
		String methFName = "summ" + seID;
        PrintWriter summPW = null;
        String fname;
        
        fname = methFName;
        methListPW.print(fname + " ");
        boolean isFirst = true;
        for (jq_Method sccM : scci) {
        	if (!isFirst) methListPW.print("###");
        	methListPW.print(domM.toUniqueString(sccM));
        	isFirst = false;
        }
        methListPW.println();
        try {
        	summPW = new PrintWriter(new File(summaryDir + File.separator + fname));
        	summPW.println(seStr);
        	summPW.close();
		} catch (Exception s) {
			throw new RuntimeException(s);
		}
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
	
	private void initSEDump() {
		markDom();
		AbstractState.mark = mark;
	}
	
	private void dumpTNdxToStr(PrintWriter methListPW) {
		PrintWriter tNdxToStrPW;
		try {
			tNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "tNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		for (int i = 0; i < domT.size(); i++) {
			tNdxToStrPW.println(domT.toUniqueString(i));
		}
		tNdxToStrPW.close();
		methListPW.println("TNDXTOSTR:tNdxToStr.txt");
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
		for (int i = 0; i < domM.size(); i++) {
			mNdxToStrPW.println(domM.toUniqueString(i));
		}
		mNdxToStrPW.close();
		methListPW.println("MNDXTOSTR:mNdxToStr.txt");
	}
	
	
	private void loadTNdxToStrTrainApp (String fname) {
		ArrayList<String> tNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				int tIdx = 0;
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					tNdxToStrTrainApp.add(line);
					if (strToTNdx.containsKey(line)) {
						tNdxToT.put(tIdx, domT.get(strToTNdx.get(line)));
						tNdxToTNdx.put(tIdx, strToTNdx.get(line));
					}
					tIdx++;
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		AbstractState.tNdxToStrTrainApp = tNdxToStrTrainApp;
		AbstractState.tNdxToT = tNdxToT;
		AbstractState.tNdxToTNdx = tNdxToTNdx;
	}
	
	private void loadINdxToStrTrainApp (String fname) {
		ArrayList<String> iNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				int iIdx = 0;
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					iNdxToStrTrainApp.add(line);
					if (strToINdx.containsKey(line)) {
						iNdxToI.put(iIdx, domI.get(strToINdx.get(line)));
						iNdxToINdx.put(iIdx, strToINdx.get(line));
					}
					iIdx++;
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		AbstractState.iNdxToStrTrainApp = iNdxToStrTrainApp;
		AbstractState.iNdxToI = iNdxToI;
		AbstractState.iNdxToINdx = iNdxToINdx;
	}
	
	private void loadMNdxToStrTrainApp (String fname) {
		ArrayList<String> mNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				int mIdx = 0;
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					mNdxToStrTrainApp.add(line);
					if (strToMNdx.containsKey(line)) {
						mNdxToM.put(mIdx, domM.get(strToMNdx.get(line)));
						mNdxToMNdx.put(mIdx, strToMNdx.get(line));
					}
					mIdx++;
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		AbstractState.mNdxToStrTrainApp = mNdxToStrTrainApp;
		AbstractState.mNdxToM = mNdxToM;
		AbstractState.mNdxToMNdx = mNdxToMNdx;
	}
	
	private void initSELoad() {
		markDom();
		AbstractState.mark = mark;

		for (int i = 0; i < domM.size(); i++) {
			strToMNdx.put(domM.toUniqueString(i), i);
		}
		for (int i = 0; i < domI.size(); i++) {
			strToINdx.put(domI.toUniqueString(i), i);
		}
		for (int i = 0; i < domT.size(); i++) {
			strToTNdx.put(domT.toUniqueString(i), i);
		}
		AbstractState.strToMNdx = strToMNdx;
		AbstractState.strToINdx = strToINdx;
		AbstractState.strToTNdx = strToTNdx;
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
						// parse MNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("MNDXTOSTR")) {
							System.out.println("Illformed mNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's mNdxToStr file and set it in AbstractState
						String mNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("mNdxToStrFName: " + mNdxToStrFName);
						loadMNdxToStrTrainApp(mNdxToStrFName);
					} else if (lineCnt == 1) {
						// parse INDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("INDXTOSTR")) {
							System.out.println("Illformed iNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's iNdxToStr file and set it in AbstractState
						String iNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("iNdxToStrFName: " + iNdxToStrFName);
						loadINdxToStrTrainApp(iNdxToStrFName);
					} else if (lineCnt == 2) {
						// parse TNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("TNDXTOSTR")) {
							System.out.println("Illformed tNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's tNdxToStr file and set it in AbstractState
						String tNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("tNdxToStrFName: " + tNdxToStrFName);
						loadTNdxToStrTrainApp(tNdxToStrFName);
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

		for (String summaryFile: methodSumms) {
			String[] parts = summaryFile.split(" ");
			String[] methodStrs = parts[1].split("###");
			boolean loadSummary = false;
			List<jq_Method> methods = new ArrayList<jq_Method>();
			for(int i = 0; i < methodStrs.length; ++i) {
				if (strToMNdx.containsKey(methodStrs[i])) {
					loadSummary = true;
					int ndx = strToMNdx.get(methodStrs[i]);
					jq_Method m = domM.get(ndx);
					methods.add(m);
				}
			}
			
			if (loadSummary) {
				//System.out.println("LOADING SUMMARIES FROM: " + parts[0]);
				try {
					File f = new File(mSumDir + File.separator + parts[0]);
					if (f.exists()) {
						Scanner sc = new Scanner(f);
						while (sc.hasNext()) {
							String line = sc.nextLine().trim();
							Summary se = new Summary(line);
							if (!se.isBadValue()) {
								for (jq_Method m : methods) {
									ArraySet<Summary> seOfM;
									if (loadedSummaries.containsKey(m))
										seOfM = loadedSummaries.get(m);
									else {
										seOfM = new ArraySet<Summary>();
										loadedSummaries.put(m, seOfM);
									}
									boolean retval = seOfM.add(se);
									String seID = appName + parts[0];
									if (retval) {
										loadSECnt++;
										summWrite.println(seID);
									} else {
										duplicateCnt++;
									}
									
									Pair<jq_Method,Summary> pr = new Pair<jq_Method,Summary>(m, se);
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
								seValidationFailCnt += methods.size();
							}
						}
						sc.close();
					}
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		}
		System.out.println("CompoRTA: duplicate count: " + duplicateCnt);
		System.out.println("CompoRTA: se validation fail count: " + seValidationFailCnt);
		return loadSECnt;
	}
	
	
	private void markDom()
	{
		List<Dom> domArray = new ArrayList<Dom>();
		domArray.add(domM);
		domArray.add(domI);
		domArray.add(domT);
		
		for(Dom dom : domArray) {
			if (!mark.containsKey(dom.getName())) {
				int[] markVec = new int[dom.size()];
				markDomain(dom, markVec);
				mark.put(dom.getName(), markVec);
			}
		}	
	}
	
	private void markDomain(Dom dom, int[] vec) {
		if (dom.getName().equals("M")){
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
		}	else if (dom.getName().equals("I")){
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
		} else if (dom.getName().equals("T")){
			vec[0] = 2;
			DomT domT = (DomT)dom;
			for (int i = 1; i < domT.size(); i++) {
				boolean isLib = useLibPrefix ? domT.get(i).getName().matches(libraryPrefix) : !domT.get(i).getName().matches(appPrefix);
				if (isLib)
					vec[i] = 2;   // Library
				else if (domT.get(i).getName().matches(fundamentalTypePrefix))
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
		int idx = domM.indexOf(m);
		return mBelongsToLib(idx);
	}
}
