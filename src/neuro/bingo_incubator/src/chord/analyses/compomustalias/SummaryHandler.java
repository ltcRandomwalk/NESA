package chord.analyses.compomustalias;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
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
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;

public class SummaryHandler {

	public String summaryDir;
	public boolean genSummaries;
	public boolean consumeSummaries;
	public boolean dumpToFile;
	public boolean dumpPEs;
	public boolean simulateSuperPerf;
	public boolean superPerfIgnoreAppCallbk;
	
	private HashMap<String, int[]> mark = new HashMap<String, int[]>();
	private String libraryPrefix = "(java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
	private TObjectIntHashMap<String> strToVNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToFNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToMNdx = new TObjectIntHashMap<String>();
	private TObjectIntHashMap<String> strToINdx = new TObjectIntHashMap<String>();
	private int validationFailedCnt = 0;
	private TObjectIntHashMap<jq_Method> validatedAndLoaded = new TObjectIntHashMap<jq_Method>();
	
	public Map<jq_Method, ArraySet<MustAliasBUEdge>> savedSummEdges;
	public Map<Inst, Set<MustAliasBUEdge>> pathEdges;
    public Map<jq_Method, ArraySet<MustAliasBUEdge>> summEdges;
    public Map<jq_Method, Set<Pair<Quad,jq_Method>>> savedReachedFromMIM;
    public Map<jq_Method, Set<Pair<Quad,jq_Method>>> reachedFromMIM;

    public void init()
    {
    	if (genSummaries)
    		initSEDump();
    	if (consumeSummaries)
    		initSELoad();
    }
        
	public void dumpLibSEsFiltered() {
		int totalFuncs = 0;
		int totalSEs = 0;
		int libFuncs = 0;
		int libSEs = 0;
		int funcsWithAppCallbk = 0;
		int libSEsWithAppCallbk = 0;
		int longFileCnt = 0;
		int longFileSEs = 0;
		
		PrintWriter methListPW = null;
		if (dumpToFile) {
			methListPW = initFileDump();
			// Note: the order in which the four methods below are called matters
			dumpINdxToStr(methListPW);
			dumpMNdxToStr(methListPW);
			dumpFNdxToStr(methListPW);
			dumpVNdxToStr(methListPW);
		}
		
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		
		for (jq_Method m : summEdges.keySet()) {
			int idx = domM.indexOf(m);
			ArraySet<MustAliasBUEdge> seSet = summEdges.get(m);
			totalFuncs++;
			totalSEs += seSet.size();
			if (mBelongsToLib(idx)) {
				libFuncs++;
	            libSEs += seSet.size();
	            CallGraphCondition cgc = new CallGraphCondition();
				cgc.createCondnStr(m);
				if (cgc.hasAppCallbk) {
					funcsWithAppCallbk++;
					libSEsWithAppCallbk += seSet.size();
					continue;
				}
	            if (seSet != null) {
	            	int sz = seSet.size();
	            	String[] seStr = new String[sz];
	            	int ndx = 0;
	                if (dumpToFile) {
	                	for (MustAliasBUEdge se : seSet)
	                		seStr[ndx++] = se.toParsableString();
                		boolean fileTooLong = dumpMethodSEsToFile(m, ndx, seStr, cgc.cStr, methListPW);
                		if (fileTooLong) { longFileCnt++; longFileSEs += sz; } 
                	} else {
                		savedSummEdges.put(m, seSet);
		                savedReachedFromMIM.put(m, reachedFromMIM.get(m));
                	}
	            }
			} 
        }
		if (dumpToFile) methListPW.close();
		System.out.println("CompoMustAlias: Total Funcs: " + totalFuncs);
		System.out.println("CompoMustAlias: Total SEs: " + totalSEs);
		System.out.println("CompoMustAlias: Lib Funcs: " + libFuncs);
		System.out.println("CompoMustAlias: Lib SEs: " + libSEs);
		System.out.println("CompoMustAlias: Lib Funcs with app callbacks: " + funcsWithAppCallbk);
		System.out.println("CompoMustAlias: SEs for funcs with app callbk: " + libSEsWithAppCallbk);
		System.out.println("CompoMustAlias: long file count: " + longFileCnt);
		System.out.println("CompoMustAlias: Num SEs missed because of long file: " + longFileSEs);
	}
	
	public void initSEDump() {
		markDom();
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		CallGraphCondition.domI = domI;
		CallGraphCondition.domM = domM;
		CallGraphCondition.mark = mark;
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
	
	
	private boolean dumpMethodSEsToFile(jq_Method m, int len, String[] seStr, String condStr, PrintWriter methListPW) {
		boolean fileTooLong = false;
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		String methFName = strTransform(m.toString());
        PrintWriter summPW = null;
        String fname;
        
        fname = methFName;
        methListPW.println(fname + " " + domM.toUniqueString(m));
        try {
			summPW = new PrintWriter(new File(summaryDir + File.separator + fname));
		} catch (FileNotFoundException e) {
			fileTooLong = true;
		}catch (SecurityException s) {
			throw new RuntimeException(s);
		}
        if (summPW != null) {
        	if (condStr != null) summPW.println("CG_CONDITION:" + condStr);
	        for (int i = 0; i < len; i++)
	        	summPW.println(seStr[i]);
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
	
	private void dumpVNdxToStr(PrintWriter methListPW) {
		PrintWriter vNdxToStrPW;
		try {
			vNdxToStrPW = new PrintWriter(new File(summaryDir + File.separator + "vNdxToStr.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (SecurityException s) {
			throw new RuntimeException(s);
		}
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		for (int i = 0; i < domV.size(); i++) {
			vNdxToStrPW.println(domV.toUniqueString(i));
		}
		vNdxToStrPW.close();
		methListPW.println("VNDXTOSTR:vNdxToStr.txt");
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
		FieldBitSet.fNdxToStrTrainApp = fNdxToStrTrainApp;
		Variable.fNdxToStrTrainApp = fNdxToStrTrainApp;
	}
	
	private void loadVNdxToStrTrainApp (String fname) {
		ArrayList<String> vNdxToStrTrainApp = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					vNdxToStrTrainApp.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		MustAliasBUEdge.vNdxToStrTrainApp = vNdxToStrTrainApp;
		Variable.vNdxToStrTrainApp = vNdxToStrTrainApp;
	}
	
	private void initSELoad() {
		markDom();
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		for (int i = 0; i < domV.size(); i++) { 
			strToVNdx.put(domV.toUniqueString(i), i);
		}
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		for (int i = 0; i < domF.size(); i++) {
			strToFNdx.put(domF.toUniqueString(i), i);
		}
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		for (int i = 0; i < domM.size(); i++) {
			strToMNdx.put(domM.toUniqueString(i), i);
		}
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		for (int i = 0; i < domI.size(); i++) {
			strToINdx.put(domI.toUniqueString(i), i);
		}
		MustAliasBUEdge.strToVNdx = strToVNdx;
		Variable.strToVNdx = strToVNdx;
		Variable.strToFNdx = strToFNdx;
		FieldBitSet.strToFNdx = strToFNdx;
		CallGraphCondition.strToINdx = strToINdx;
		CallGraphCondition.strToMNdx = strToMNdx;
		CallGraphCondition.domI = domI;
		CallGraphCondition.domM = domM;
	}
	
	
	public void loadSavedSEs() {
    	Timer timerLoad = new Timer("Summary Loading");
    	timerLoad.init();
    	int loadSECnt = 0;
    	boolean fromDisk = dumpToFile;
    	
    	if (fromDisk) {
    		savedReachedFromMIM = new HashMap<jq_Method, Set<Pair<Quad,jq_Method>>>();
    		if (summaryDir.equals("")) return;
    		String appListFile = summaryDir + File.separator + "appList.txt";
    		File mFile = new File(appListFile);
    		if (mFile.exists()) {
    			try {
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
    				
    			} catch (FileNotFoundException e) {
    				throw new RuntimeException(e);
    			}
    		}		
    	}
    	loadSummaries();	
		timerLoad.done();
		long timeToLoad = timerLoad.getInclusiveTime();
		// Set the following to null since summary loading is over and they are no longer required.
		// Moreover, they are specific to a training app.
		MustAliasBUEdge.vNdxToStrTrainApp = null;
		Variable.vNdxToStrTrainApp = null;
		Variable.fNdxToStrTrainApp = null;
		FieldBitSet.fNdxToStrTrainApp = null;
		
		System.out.println("CompoMustAlias: SUMMARY LOAD TIME: "+ Timer.getTimeStr(timeToLoad));
		System.out.println("CompoMustAlias: LOADED " + loadSECnt + " summary edges");
		System.out.println("CompoMustAlias: Summaries for which validation (CG match between test and training data) failed: " + validationFailedCnt);
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
					} else if (lineCnt == 1) {
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
					} else if (lineCnt == 2) {
						// parse FNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("FNDXTOSTR")) {
							System.out.println("Illformed fNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's fNdxToStr file and set it in Variable and FieldBitSet
						String fNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("fNdxToStrFName: " + fNdxToStrFName);
						loadFNdxToStrTrainApp(fNdxToStrFName);
					} else if (lineCnt == 3) {
						// parse VNDXTOSTR:<filename>
						String[] parts = line.split(":");
						if (!parts[0].equals("VNDXTOSTR")) {
							System.out.println("Illformed vNdxToStr in MethodList file: " + methFile);
							System.exit(1);
						}
						// read in the training app's vNdxToStr file and set it in Variable and MustAliasBUEdge
						String vNdxToStrFName = dirName + File.separator + parts[1];
						System.out.println("vNdxToStrFName: " + vNdxToStrFName);
						loadVNdxToStrTrainApp(vNdxToStrFName);
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
		int loadSECnt = 0;
		int duplicateCnt = 0;
		int cgValidationFailCnt = 0;
		int seValidationFailCnt = 0;
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");

		for (String summaryFile: methodSumms) {
			String[] parts = summaryFile.split(" ");
			if (parts.length < 2) continue;
			//System.out.println("LOADING SUMMARIES FROM: " + parts[0]);
			if (strToMNdx.containsKey(parts[1])) {
				int ndx = strToMNdx.get(parts[1]);
				jq_Method m = domM.get(ndx);
				
				try {
					File f = new File(mSumDir + File.separator + parts[0]);
					if (f.exists()) {
						Scanner sc = new Scanner(f);
						while (sc.hasNext()) {
							String line = sc.nextLine().trim();
							if (line.startsWith("CG_CONDITION:")) {
								String[] cgParts = line.split("CG_CONDITION:");
								CallGraphCondition cgc = new CallGraphCondition();
								cgc.parse(cgParts[1]);
								if (!cgc.badValue) {
									savedReachedFromMIM.put(m, cgc.reachableIMs);
									continue;
								} else {
									// The call graph condition did not parse well or did not match - discontinue loading 
									// the summaries for this method and go to next method.
									cgValidationFailCnt++;
									break;
								}
							}
							// All other lines are MustAliasBUEdges
							MustAliasBUEdge se = new MustAliasBUEdge(line);
							if (!se.badValue) {
								ArraySet<MustAliasBUEdge> seOfM;
								if (savedSummEdges.containsKey(m))
									seOfM = savedSummEdges.get(m);
								else {
									seOfM = new ArraySet<MustAliasBUEdge>();
									savedSummEdges.put(m, seOfM);
								}
								boolean retval = seOfM.add(se);
								if (retval)
									loadSECnt++;
								else
									duplicateCnt++;
							} else {
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
		System.out.println("CompoMustAlias: duplicate count: " + duplicateCnt);
		System.out.println("CompoMustAlias: CG validation fail (I/M not available in test app) count: " + cgValidationFailCnt);
		System.out.println("CompoMustAlias: SE validation fail (V/F not available in test app) count: " + seValidationFailCnt);
		return loadSECnt;
	}
	

	public void loadSEs(jq_Method m) {
		int done = 0;
		if (validatedAndLoaded.contains(m))
			done = validatedAndLoaded.get(m);
		// if we have a pre-loaded summary for m and it has been either validated or invalidated, return.
		// Continue to check only if we don't know whether we have a summary or not.
		if (done > 0 || done < 0) return;
		
		Set<Pair<Quad,jq_Method>> crntCallees = reachedFromMIM.get(m);
		Set<Pair<Quad,jq_Method>> savedCallees = savedReachedFromMIM.get(m);
		
		if (savedCallees == null || savedCallees.isEmpty()) {
			if (crntCallees != null && !crntCallees.isEmpty()) {
				if (savedCallees != null) validationFailedCnt++; // Don't count as "validationFailed" for all those methods for which 
				                                                 // we are not even trying to pre-load (ex: app methods)
				validatedAndLoaded.put(m, -1);
				return;
			}
		} else if (crntCallees == null || crntCallees.isEmpty()) {
			// we know that savedCallees is not null and is not empty
			validationFailedCnt++;
			validatedAndLoaded.put(m, -1);
			return;
		} else {
			if (!(savedCallees.containsAll(crntCallees)) || !(crntCallees.containsAll(savedCallees))) {
				validationFailedCnt++;
				validatedAndLoaded.put(m, -1);
				return;
			}
		}
		ArraySet<MustAliasBUEdge> seOfM;
		if (savedSummEdges.containsKey(m)) {
			seOfM = savedSummEdges.get(m);
			summEdges.put(m, seOfM);
			validatedAndLoaded.put(m, 1);
		    if (savedCallees != null) {	
				for (Pair<Quad, jq_Method> p : savedCallees) {
					jq_Method callee = p.val1;
					ArraySet<MustAliasBUEdge> seSet;
					if (savedSummEdges.containsKey(callee)) {
						seSet = savedSummEdges.get(callee);
						summEdges.put(callee, seSet);
						validatedAndLoaded.put(callee, 1);
					}
				}
		    }
		}
	}
	
	
	public int getValidationFailedCnt() {
		return validationFailedCnt;
	}
	
	
	public void loadSummaries() {
		System.out.println("savedSummEdges size after loading from disk: " + savedSummEdges.size());
		for (jq_Method m : savedSummEdges.keySet()) {
			Set<Pair<Quad,jq_Method>> savedCallees = savedReachedFromMIM.get(m);
			Set<Pair<Quad,jq_Method>> callees = reachedFromMIM.get(m);
			if (savedCallees == null && callees == null)
				summEdges.put(m, savedSummEdges.get(m));
			else if (savedCallees == null && callees != null) {
				//System.out.println("\nMETHOD ValFail1: " + m);
				validationFailedCnt++;
			} else if (savedCallees != null && callees == null) {
				if (!savedCallees.isEmpty()) {
					//System.out.println("\nMETHOD ValFail2: " + m);
					validationFailedCnt++;
				} else {
					summEdges.put(m, savedSummEdges.get(m));
				}
			} else if (savedCallees.containsAll(callees) && callees.containsAll(savedCallees)) {
				summEdges.put(m, savedSummEdges.get(m));
			} else {
				//System.out.println("\nMETHOD ValFail3: " + m);
				validationFailedCnt++;
			}
		}
		System.out.println("SummEdges size after CG validation: " + summEdges.size());
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
				if (clName.matches(libraryPrefix))
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
				if (clName.matches(libraryPrefix))
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
				if (clName.matches(libraryPrefix))
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
				if (clName.matches(libraryPrefix))
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
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		return mBelongsToLib(domM.indexOf(m));
	}
}
