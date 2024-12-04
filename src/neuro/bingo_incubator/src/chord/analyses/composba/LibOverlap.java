package chord.analyses.composba;

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

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.composba.alloc.AllocEnvCFAAnalysis;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

/*
 * This is an implementation of compositional SBA.
 * SBA analysis is performed on a set of training apps and the summaries learnt from them are stored.
 * When an application under test is to be analysed, the relevant stored summaries are reused to reduce redundant computation.
 *                                          
 * chord.liboverlap.summaryDir[default ""]  string representing a directory where summaries are stored/read-from.
 * chord.liboverlap.flow [default ""]  string that specifies flow in the compositional analysis
 *                                   pairwise   : compute pairwise overlap
 *                                   match      : get matching statistics
 *                                   
 * chord.liboverlap.testapp [default""]   : app to match
 * chord.liboverlap.applist [default""] : comma-separated list of apps to compare pairwise or to match with test app
 * chord.liboverlap.dump [default false] : dump overlap SEs
 */

@Chord(name = "liboverlap")
public class LibOverlap extends JavaAnalysis {
	private String appList = "";
	private String testApp = "";
	private String summaryDir = "";
	private PrintWriter overlapPW;
	private boolean dumpOverlap = false;
	
	public void run() {	
		int flowKind = 0;	
		String s = System.getProperty("chord.liboverlap.flow");
		if (s.equals("pairwise"))
            flowKind = 1;
		else if (s.equals("match"))
            flowKind = 2;
        else
            throw new RuntimeException("Bad value for flow property: " + s);
		appList = System.getProperty("chord.liboverlap.applist");
		testApp = System.getProperty("chord.liboverlap.testapp");
		summaryDir = System.getProperty("chord.liboverlap.summaryDir");
		dumpOverlap = Boolean.getBoolean("chord.liboverlap.dump");
		
		if (flowKind == 1) {
			try {
				overlapPW = new PrintWriter(summaryDir + File.separator + "overlap_data.csv");
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
			String[] apps = appList.split(",");
			overlapPW.print(" ");
			for (String i : apps) {
				overlapPW.print(i + "  ");
			}
			overlapPW.println("");
			for (String i : apps) {
				overlapPW.print(i);
				ArrayList<String> methodSummsI = null;
				HashMap<String, HashSet<EdgeString>> summEdges = null;
				HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>> seToHeapDataMap = new HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>>();
	    		methodSummsI = getMethodsWithAvailableSEs(i);
	    		if (methodSummsI != null) summEdges = loadSEsFromMethods(methodSummsI, i, seToHeapDataMap);
				for (String j : apps) {
					//j = j + "_frontier";
					overlapPW.print(" ");
					if (!i.equals(j)) {
						ArrayList<String> methodSummsJ = null;
			    		methodSummsJ = getMethodsWithAvailableSEs(j);
			    		if (methodSummsJ != null) checkSEsFromMethods(summEdges, methodSummsJ, j, seToHeapDataMap, true /* overlap data to csv file */);
					} else {
						overlapPW.print(" ");
					}
				}
				overlapPW.println("");
			}
			overlapPW.close();
		}
		
		else if (flowKind == 2) {
			String[] apps = appList.split(",");
			for (String i : apps) {
				ArrayList<String> methodSummsI = null;
				HashMap<String, HashSet<EdgeString>> summEdges = null;
				HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>> seToHeapDataMap = new HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>>();
	    		methodSummsI = getMethodsWithAvailableSEs(i);
	    		if (methodSummsI != null) summEdges = loadSEsFromMethods(methodSummsI, i, seToHeapDataMap);
				ArrayList<String> methodSummsJ = null;
	    		methodSummsJ = getMethodsWithAvailableSEs(testApp);
	    		if (methodSummsJ != null) checkSEsFromMethods(summEdges, methodSummsJ, testApp, seToHeapDataMap, false /* overlap data to log file */);
			}
		}
	}
	
	private ArrayList<String> getMethodsWithAvailableSEs(String i) {
		if (summaryDir.equals("")) return null;
		ArrayList<String> methodSumms = new ArrayList<String>();
		String methFile = summaryDir + File.separator + "summary_" + i + File.separator + "methodList.txt";
		File mFile = new File(methFile);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					methodSumms.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		return methodSumms;
	}
	
	
	private HashMap<String, HashSet<EdgeString>> loadSEsFromMethods(ArrayList<String> methodSumms, String i,
			                                                        HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>> seToHeapDataMap) {
		int loadSECnt = 0;
		int totalSECnt = 0;
		HashMap<String, HashSet<EdgeString>> summEdges = new HashMap<String, HashSet<EdgeString>>();
		for (String summaryFile: methodSumms) {
			String[] parts = summaryFile.split(" ");
			String methName = parts[1];
			try {
				File f = new File(summaryDir + File.separator + "summary_" + i + File.separator + parts[0]);
				if (f.exists()) {
					Scanner sc = new Scanner(f);
					while (sc.hasNext()) {
						String line = sc.nextLine().trim();
						String[] seAndHpParts = line.split(":HFHDAT:");
						EdgeString se = new EdgeString(seAndHpParts[0]);
						HeapDataString hp = new HeapDataString(seAndHpParts[1]);
						HashSet<EdgeString> seOfM;
						if (summEdges.containsKey(methName))
							seOfM = summEdges.get(methName);
						else {
							seOfM = new HashSet<EdgeString>();
							summEdges.put(methName, seOfM);
						}
						totalSECnt++;
						boolean retval = seOfM.add(se);
						if (retval) 
							loadSECnt++;
						Pair<String, EdgeString> pr = new Pair<String, EdgeString> (methName, se);
						HashSet<HeapDataString> hpSet;
						if (seToHeapDataMap.containsKey(pr))
							hpSet = seToHeapDataMap.get(pr);
						else {
							hpSet = new HashSet<HeapDataString>();
							seToHeapDataMap.put(pr, hpSet);
						}
						hpSet.add(hp);
					}
					sc.close();
				}
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		System.out.println("\n\nLibOverlap: Training App: " + i + " Loaded: " + loadSECnt + "  Total: " + totalSECnt);
		
		/****
		for (String methName : summEdges.keySet()) {
			System.out.println("METHOD: " + methName);
			HashSet<EdgeString> seSet = summEdges.get(methName);
			System.out.println (seSet);
		}
		****/
		return summEdges;
	}
	
	private PrintWriter initDumpOverlap(String i) {
		String sumDirName = summaryDir + File.separator + "summary_" + i + "_overlap";
		File sumDir = new File(sumDirName);
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("Summary dir " + sumDirName + " does not exist - unable to create it.");
		     }        
		}
		PrintWriter methListPW;
		try {
			methListPW = new PrintWriter(sumDirName + File.separator + "methodList.txt");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		return methListPW;    	
	}
	
	 private void dumpOverlapSE (PrintWriter methListPW, String methName, ArrayList<String> toWrite, String appName) {
    
        PrintWriter summPW = null;
        String methFName = strTransform(methName);
        methListPW.println(methFName + " " + methName);
        try {
			summPW = new PrintWriter(new File(summaryDir + File.separator + "summary_" + appName + "_overlap" + File.separator + methFName));
		} catch (FileNotFoundException e) {
			throw new RuntimeException (e);
		}
        if (summPW != null) {
	        for (String line : toWrite) {
        		summPW.println(line);
	        }
	        summPW.close();
	    }
        return;    	
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
	 
	private void checkSEsFromMethods(HashMap<String, HashSet<EdgeString>> summEdges, ArrayList<String> methodSumms, String i,
			                         HashMap<Pair<String, EdgeString>, HashSet<HeapDataString>> seToHeapDataMap, boolean toCsv) {
		// Check the SEs of test app i against the training app data in summEdges.
		int present = 0;
		int missing = 0;
		int total = 0;
		PrintWriter methListPW = null;
		if (dumpOverlap) methListPW = initDumpOverlap(i);
		for (String summaryFile: methodSumms) {
			String[] parts = summaryFile.split(" ");
			String methName = parts[1];
			HashSet<EdgeString> seOfM = null;
			ArrayList<String> buf = new ArrayList<String>();
			if (summEdges.containsKey(methName))
				seOfM = summEdges.get(methName);
			try {
				File f = new File(summaryDir + File.separator + "summary_" + i + File.separator + parts[0]);
				if (f.exists()) {
					Scanner sc = new Scanner(f);
					while (sc.hasNext()) {
						total++;
						String line = sc.nextLine().trim();
						String[] seAndHpParts = line.split(":HFHDAT:");
						if (seOfM != null) {
							EdgeString se = new EdgeString(seAndHpParts[0]);
							boolean found = false;
							if (seOfM.contains(se)) {
								HeapDataString hp = new HeapDataString(seAndHpParts[1]);
								HashSet<HeapDataString> hpRefSet = seToHeapDataMap.get(new Pair<String, EdgeString>(methName, se));
								for (HeapDataString hpRef : hpRefSet) {
									if (hpRef.equals(hp)) {
										found = true;
										if (dumpOverlap) buf.add(line);
										break;
									}
								}
							}
							if (found)
								present++;
							else
								missing++;
						} else 
							missing++;
					}
					sc.close();
					if (dumpOverlap && !buf.isEmpty()) dumpOverlapSE (methListPW, parts[1], buf, i);
				}
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		if (dumpOverlap) methListPW.close();
		System.out.println("LibOverlap: Test App: " + i + " Total SEs: " + total);
		System.out.println("LibOverlap: Present: " + present + "  Missing: " + missing);
		if (toCsv)
			overlapPW.print(present + " " + missing);
		return;
	}
}

