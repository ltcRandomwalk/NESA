package chord.analyses.superopt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;


public class Util {
	
	private static ArrayList<ArrayList<Object[]>> pristineInAppRels = new ArrayList<ArrayList<Object[]>>();
	
	private static ArrayList<ArrayList<Object[]>> minInLibRels = new ArrayList<ArrayList<Object[]>>();
	private static ArrayList<ArrayList<Object[]>> maxInLibRels = new ArrayList<ArrayList<Object[]>>();
	
	private static ArrayList<ArrayList<Object[]>> maxInLibRelsAC = new ArrayList<ArrayList<Object[]>>();
	
	private static ArrayList<ArrayList<Object[]>> maxInLibRelsMCMC = new ArrayList<ArrayList<Object[]>>();
	private static HashMap<String, ArrayList<Object>> domRels = new HashMap<String, ArrayList<Object>>();
	
	private static boolean[] currSample;
	private static int currRelsSz = -1;
	private static double currTheta = 0.0;
	private static int prevExpectedValue = 0;
	private static int expectedValueCnt = 0;
	private static double prevAcceptedRewriteCost = 0;
	
	private static final String DEL_TUPLES_FILE_SC = "deleted_tuples_SC";
	private static final String ADD_TUPLES_FILE_SC = "added_tuples_SC";
	private static final PrintWriter delOut = OutDirUtils.newPrintWriter(DEL_TUPLES_FILE_SC);
	private static final PrintWriter addOut = OutDirUtils.newPrintWriter(ADD_TUPLES_FILE_SC);
	
	private static final String DEL_TUPLES_FILE_AC = "deleted_tuples_AC";
	private static final PrintWriter delOutAC = OutDirUtils.newPrintWriter(DEL_TUPLES_FILE_AC);
	
	private static final String DEL_TUPLES_FILE_MCMC = "deleted_tuples_MCMC";
	private static final PrintWriter delOutMCMC = OutDirUtils.newPrintWriter(DEL_TUPLES_FILE_MCMC);
	private static final String MUT_TUPLES_REJECT_MCMC = "mut_tuples_reject_MCMC";
	private static final PrintWriter mutRejMCMC = OutDirUtils.newPrintWriter(MUT_TUPLES_REJECT_MCMC);
	private static final String MUT_TUPLES_ACCEPT_MCMC = "mut_tuples_accept_MCMC";
	private static final PrintWriter mutAccMCMC = OutDirUtils.newPrintWriter(MUT_TUPLES_ACCEPT_MCMC);
	
	static Random rand = new Random(System.currentTimeMillis());
	
	public static int randInt (int min, int max) {
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;
		//System.out.println("rand: min, max,rand:" + min + "  " + max + "  " + randomNum);
		return randomNum;
	}
	
	public static ArrayList<String> getRelNames (String fname) {
		ArrayList<String> relsToModify = new ArrayList<String>();
		File mFile = new File(fname);
		if (mFile.exists()) {
			try {
				Scanner sc = new Scanner(mFile);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					relsToModify.add(line);
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		return relsToModify;
	}
	
	public static void loadPristineRelsToLists (ArrayList<String> inRels, boolean dumpRels, boolean doSC) {
		for (String relName : inRels) {
			String pRelName = "asave_" + relName;
			ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pRelName);
			prel.load();
			if (dumpRels) {
				prel.print();
				prel.load();
			}
			ArrayList<Object[]> relElems = new ArrayList<Object[]>();
			for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
			pristineInAppRels.add(relElems);
		}
		for (String relName : inRels) {
			String pRelName = "lsave_" + relName;
			ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pRelName);
			prel.load();
			if (dumpRels) {
				prel.print();
				prel.load();
			}
			
			ArrayList<Object[]> relElems = new ArrayList<Object[]>();
			if (doSC) {
				relElems = new ArrayList<Object[]>();
				for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
				maxInLibRels.add(relElems);	
				relElems = new ArrayList<Object[]>();
				minInLibRels.add(relElems);
			} else {
				relElems = new ArrayList<Object[]>();
				for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
				maxInLibRelsAC.add(relElems);
			}
		}
	}
	
	public static void deleteRelElem (ArrayList<ProgramRel> rels, ArrayList<Integer> relStartNdx, 
			                           int instToDelete) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (instToDelete < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && instToDelete >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
			
		ProgramRel currRel = rels.get(ndx);
		currRel.zero();
		
		ArrayList<Object[]> appRelData = pristineInAppRels.get(ndx);
		for (int j = 0; j < appRelData.size(); j++) addToRel(currRel, appRelData.get(j));
		
		ArrayList<Object[]> maxRelData = maxInLibRels.get(ndx);
		int delNdx = instToDelete - relStartNdx.get(ndx);
		
		System.out.println("ndx: " + ndx + "      delNdx: " + delNdx);
		for (int j = 0; j < maxRelData.size(); j++) {
			if (j != delNdx)
				addToRel(currRel, maxRelData.get(j));
		}
		currRel.save();	
		
		if (delNdx == 0 && ndx > 0) {
			currRel = rels.get(ndx - 1);
			currRel.zero();
			appRelData = pristineInAppRels.get(ndx - 1);
			for (int j = 0; j < appRelData.size(); j++) addToRel(currRel, appRelData.get(j));
			maxRelData = maxInLibRels.get(ndx - 1);
			for (int j = 0; j < maxRelData.size(); j++) addToRel(currRel, maxRelData.get(j));
			currRel.save();	
		}
	}
	
	
	public static void saveFinalRel (ArrayList<ProgramRel> rels) {
		int ndx = rels.size() - 1;
		ProgramRel currRel = rels.get(ndx);
		currRel.zero();
		ArrayList<Object[]> appRelData = pristineInAppRels.get(ndx);
		for (int j = 0; j < appRelData.size(); j++) addToRel(currRel, appRelData.get(j));
		ArrayList<Object[]> maxRelData = maxInLibRels.get(ndx);
		for (int j = 0; j < maxRelData.size(); j++) addToRel(currRel, maxRelData.get(j));
		currRel.save();	
	}
	
	
	public static ArrayList<Integer> removeFromMax (ArrayList<String> relNames, ArrayList<Integer> relStartNdx, 
			                          int instToDelete) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (instToDelete < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && instToDelete >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
		
		ArrayList<Object[]> maxRelData = maxInLibRels.get(ndx);
		int delNdx = instToDelete - relStartNdx.get(ndx);
		Object[] delElem = maxRelData.remove(delNdx);
		
		ArrayList<Integer> newStartNdx = new ArrayList<Integer>();
		for (int i = 0; i <= ndx; i++)
			newStartNdx.add(relStartNdx.get(i));
		for (int i = ndx + 1; i < relStartNdx.size(); i++)
			newStartNdx.add(relStartNdx.get(i) - 1);

		delOut.print(relNames.get(ndx) + ": ");
		for (int k = 0; k < delElem.length; k++)
			if (delElem[k] == null)
				delOut.print("(null)   ");
			else
				delOut.print(delElem[k].toString() + "   ");
		delOut.println();
		return newStartNdx;
	}
	
	
	public static void closeLogs () {
		delOut.close();
		addOut.close();
		delOutAC.close();
		delOutMCMC.close();
		mutRejMCMC.close();
		mutAccMCMC.close();
	}
	
	
	public static void flushLogs () {
		delOut.flush();
		addOut.flush();
		delOutAC.flush();
		delOutMCMC.flush();
		mutRejMCMC.flush();
		mutAccMCMC.flush();
	}
	
	
	public static ArrayList<String> getDelRels (ArrayList<String> inRels) {
		ArrayList<String> delRels = new ArrayList<String>();
		for (String s: inRels) {
			if (!s.equals("MputInstFldInst") 
				&& !s.equals("MputStatFldInst") 
				&& !s.equals("MmethRet")
				&& !s.equals("MIinvkArg")
				&& !s.equals("MIinvkArg0")) {
				delRels.add(s);
			}
		}
		return delRels;
	}
	
	
	public static void addToMin (ArrayList<String> relNames, ArrayList<Integer> relStartNdx, int instToAdd) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (instToAdd < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && instToAdd >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
		
		ArrayList<Object[]> minRelData = minInLibRels.get(ndx);
		int addNdx = instToAdd - relStartNdx.get(ndx);
		
		ArrayList<Object[]> pristineRelData = maxInLibRels.get(ndx);
		Object[] toAdd = pristineRelData.get(addNdx);
		minRelData.add(toAdd);
		
		addOut.print(relNames.get(ndx) + ": ");
		for (int k = 0; k < toAdd.length; k++)
			if (toAdd[k] == null)
				addOut.print("(null)   ");
			else
				addOut.print(toAdd[k].toString() + "   ");
		addOut.println();
	}
	
	
	public static boolean minSmallerThanMax() {
		int minSz = 0;
		int maxSz = 0;
		for (int i = 0; i < minInLibRels.size(); i++) minSz += minInLibRels.get(i).size();
		for (int i = 0; i < maxInLibRels.size(); i++) maxSz += maxInLibRels.get(i).size();
		return (minSz < maxSz);
	}
	
	
	public static void addToRel(ProgramRel rel, Object[]tuple) {
		if (tuple.length == 1)
			rel.add(tuple[0]);
		else if (tuple.length == 2)
			rel.add(tuple[0], tuple[1]);
		else if (tuple.length == 3)
			rel.add(tuple[0], tuple[1], tuple[2]);
		else if (tuple.length == 4)
			rel.add(tuple[0], tuple[1], tuple[2], tuple[3]);
		else if (tuple.length == 5)
			rel.add(tuple[0], tuple[1], tuple[2], tuple[3], tuple[4]);
		else if (tuple.length == 6)
			rel.add(tuple[0], tuple[1], tuple[2], tuple[3], tuple[4], tuple[5]);
	}
	
	
	public static int setMaxLibRelSzSC () {
		int maxSz = 0;
		for (int i = 0; i < maxInLibRels.size(); i++) maxSz += maxInLibRels.get(i).size();
		currRelsSz = maxSz; 
		return currRelsSz;
	}

	
	public static int setMaxLibRelSz () {
		int maxSz = 0;
		for (int i = 0; i < maxInLibRelsAC.size(); i++) maxSz += maxInLibRelsAC.get(i).size();
		currRelsSz = maxSz; 
		return currRelsSz;
	}
	
	public static boolean getBoolWithProb (double alpha) {
		int boundary = (int) (alpha * 100000.0);
		int randVal = randInt (0, 100000);
		if (randVal <= boundary)
			return true;
		else
			return false;
	}
	
	
	public static boolean setCurrSample (double eta, boolean success) {
		currSample = new boolean[currRelsSz];
		double tgtProb = Math.exp(-1.0);
		double f_a;
		if (success) f_a = 0.0; else f_a = 1.0;
		double theta = currTheta - eta * ( 1.0 - f_a - tgtProb);
		currTheta = theta;
		double alpha;
		double temp;
		temp = Math.exp(-theta);
		alpha = 1.0 / (1.0 + temp);
		int deleteCnt = 0;
		
		int expectedValOfDelTuples = currRelsSz - (int)(currRelsSz * alpha);
		System.out.println("Expected value of number of tuples to be deleted: " + expectedValOfDelTuples);
		if (expectedValOfDelTuples == 1) {
			// Increment expectedValueCnt if you see consecutive ones
			if (prevExpectedValue == expectedValOfDelTuples)
				expectedValueCnt++;
			if (expectedValueCnt == 10) return false;
		} else 
			expectedValueCnt = 0;
		prevExpectedValue = expectedValOfDelTuples;
		
		for (int i = 0; i < currRelsSz; i++) {
			boolean include = getBoolWithProb(alpha);
			if (include)
				currSample[i] = true;
			else {
				currSample[i] = false;
				deleteCnt++;
			}
		}
		System.out.println("Trying with values: theta: " + theta + "   deleting: " + deleteCnt);
		return true;
	}
	
	
	public static void deleteRelElemAC (ArrayList<ProgramRel> rels, ArrayList<Integer> relStartNdx) {
		int ndx = -1;
		ProgramRel currRel = rels.get(0);
		ArrayList<Object[]> maxRelData = maxInLibRelsAC.get(0);
		int currStartNdx = 0;
		
		for (int i = 0; i < currRelsSz; i++) {
			while (ndx < (relStartNdx.size() - 1) && i == relStartNdx.get(ndx+1)) {
				ndx++;
				if (ndx > 0) currRel.save();
				currRel = rels.get(ndx);
				currRel.zero();
				ArrayList<Object[]> appRelData = pristineInAppRels.get(ndx);
				for (int j = 0; j < appRelData.size(); j++) addToRel(currRel, appRelData.get(j));
				maxRelData = maxInLibRelsAC.get(ndx);
				currStartNdx = relStartNdx.get(ndx);
			}	
			if (currSample[i] == true)
				addToRel(currRel, maxRelData.get(i - currStartNdx));
		}
		currRel.save();
		
	}
	
	
	public static void removeFromMaxAC (ArrayList<String> relNames, ArrayList<Integer> relStartNdx) {
		int ndx = -1;
		ArrayList<ArrayList<Object[]>> newMaxInLibRelsAC = new ArrayList<ArrayList<Object[]>>();
		ArrayList<Object[]> maxRelData = maxInLibRelsAC.get(0);
		ArrayList<Object[]> newRelData = new ArrayList<Object[]>();
		int currStartNdx = 0;
		
		for (int i = 0; i < currRelsSz; i++) {
			while (ndx < (relStartNdx.size() - 1) && i == relStartNdx.get(ndx+1)) {
				ndx++;
				if (ndx > 0) {
					newMaxInLibRelsAC.add(newRelData);
					newRelData = new ArrayList<Object[]>();
				}
			}
			maxRelData = maxInLibRelsAC.get(ndx);
			currStartNdx = relStartNdx.get(ndx);
			
			Object[] elem = maxRelData.get(i - currStartNdx);
			if (currSample[i] == true)
				newRelData.add(elem);
			else {
				delOutAC.print(relNames.get(ndx) + ": ");
				for (int k = 0; k < elem.length; k++)
					if (elem[k] == null)
						delOutAC.print("(null)   ");
					else
						delOutAC.print(elem[k].toString() + "   ");
				delOutAC.println();
			}
		}
		newMaxInLibRelsAC.add(newRelData);
		maxInLibRelsAC = newMaxInLibRelsAC;
		setMaxLibRelSz();
	}
	
	
	public static ArrayList<Integer> adjustStartNdxSC() {
		ArrayList<Integer> inRelAccSz = new ArrayList<Integer>();
		int totalRelLen = 0;
		for (int i = 0; i < maxInLibRels.size(); i++) {
			inRelAccSz.add(totalRelLen);
			totalRelLen += maxInLibRels.get(i).size();
		}
		return inRelAccSz;
	}
	
	
	public static ArrayList<Integer> adjustStartNdx() {
		ArrayList<Integer> inRelAccSz = new ArrayList<Integer>();
		int totalRelLen = 0;
		for (int i = 0; i < maxInLibRelsAC.size(); i++) {
			inRelAccSz.add(totalRelLen);
			totalRelLen += maxInLibRelsAC.get(i).size();
		}
		return inRelAccSz;
	}
	
	
	public static boolean isAboveThreshold (int scThreshold) {
		if (currRelsSz == -1) setMaxLibRelSz ();
		return (currRelsSz >= scThreshold);
	}
	
	
	public static void initializeForSC () {
		System.out.println("Starting scan coarsening with rel size: " + currRelsSz);
		for (int i = 0; i < maxInLibRelsAC.size(); i++) {
			ArrayList<Object[]> relElems = maxInLibRelsAC.get(i);
			maxInLibRels.add(relElems);	
			relElems = new ArrayList<Object[]>();
			minInLibRels.add(relElems);
		}
	}
 	
	
	public static ArrayList<Integer> inititalizeForMCMC (ArrayList<String> inRels, boolean dumpRels) {
		ArrayList<Integer> inRelAccSz = new ArrayList<Integer>();
		if (Config.reuseRels) {
			for (String relName : inRels) {
				String pRelName = "asave_" + relName;
				ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pRelName);
				prel.load();
				if (dumpRels) {
					prel.print();
					prel.load();
				}
				ArrayList<Object[]> relElems = new ArrayList<Object[]>();
				for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
				pristineInAppRels.add(relElems);
			}
		}
		
		int totalLibRelLen = 0;
		for (String relName : inRels) {
			ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(relName);
			if (Config.reuseRels) prel.load();
			if (dumpRels) {
				prel.print();
				prel.load();
			}
			ProgramRel librel = (ProgramRel) ClassicProject.g().getTrgt("lsave_" + relName);
			librel.load();
			ArrayList<Object[]> relElems = new ArrayList<Object[]>();
			relElems = new ArrayList<Object[]>();
			for (Object[] tuple : librel.getAryNValTuples()) relElems.add(tuple);
			maxInLibRelsMCMC.add(relElems);
			
			inRelAccSz.add(totalLibRelLen);
			totalLibRelLen += relElems.size();
			
			Dom<?>[] doms = prel.getDoms();
			for (int i = 0; i < doms.length; i++) {
				String domName = doms[i].getName();
				if (!domRels.containsKey(domName)) {
					ProgramRel drel = (ProgramRel) ClassicProject.g().getTrgt(domName + "_lib");
					drel.load();
					ArrayList<Object> lst = new ArrayList<Object>();
					for (int j = 0; j < doms[i].size(); j++) {
						if (drel.contains(j)) {
							Object obj = doms[i].get(j);
							lst.add(obj);
						}	
					}
					domRels.put(domName, lst);
				}
			}
		}
		return inRelAccSz;
	}
	
	
	public static Object[] getMutatedObj (ProgramRel rel) {
		Dom<?>[] doms = rel.getDoms();
		Object[] mutatedTuple = new Object[doms.length];
		for (int i = 0; i < doms.length; i++) {
			String domName = doms[i].getName();
			int max = domRels.get(domName).size() - 1;
			int rand = randInt(0, max);
			Object randObj = domRels.get(domName).get(rand);
			mutatedTuple[i] = randObj;
		}
		return mutatedTuple;
	}
	
	
	public static Object[] mutate(int toMutate, ArrayList<ProgramRel> rels, ArrayList<Integer> relStartNdx, 
			                      boolean doMutate) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (toMutate < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && toMutate >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
			
		ProgramRel currRel = rels.get(ndx);
		currRel.zero();
		
		ArrayList<Object[]> appRelData = pristineInAppRels.get(ndx);
		for (int j = 0; j < appRelData.size(); j++) addToRel(currRel, appRelData.get(j));
		
		Object[] mutatedObj = null;
		if (doMutate) mutatedObj = getMutatedObj(currRel);
		ArrayList<Object[]> maxRelData = maxInLibRelsMCMC.get(ndx);
		int delNdx = toMutate - relStartNdx.get(ndx);
		
		System.out.println("ndx: " + ndx + "      delNdx: " + delNdx);
		for (int j = 0; j < maxRelData.size(); j++) {
			if (j != delNdx)
				addToRel(currRel, maxRelData.get(j));
			else {
				if (doMutate) addToRel(currRel, mutatedObj);
			}
		}
		currRel.save();	
		return mutatedObj;
	}
	
	
	public static double calcCorrectness(ArrayList<String> outRelNames) {
		double retval = 0;
		int totalTMinusR = 0;
		for (int i = 0; i < outRelNames.size(); i++) {
			String relName = "unequal_" + outRelNames.get(i);
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
			rel.load();
			totalTMinusR += rel.size();
			rel.close();
		}
		int totalRMinusT = 0;
		for (int i = 0; i < outRelNames.size(); i++) {
			String relName = "unequal_" + outRelNames.get(i) + "1";
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
			rel.load();
			totalRMinusT += rel.size();
			rel.close();
		}
		if (totalTMinusR > totalRMinusT)
			retval = (double) totalTMinusR;
		else
			retval = (double)totalRMinusT;
		return retval;
	}
	
	
	public static double calcPerf(long timeT, long timeR) {
		double retval = 0.0;
		retval = ((double)timeR) / ((double)timeT) * 10.0;
		System.out.println("DEBUG: calcPerf: retval: " + retval);
		return (retval);
	}
	
	
	public static int getMaxLibRelsMCMCSz() {
		int maxSz = 0;
		for (int i = 0; i < maxInLibRelsMCMC.size(); i++) maxSz += maxInLibRelsMCMC.get(i).size();
		return maxSz;
	}
	
	
	public static double probRewriteAccept (double rCost) {
		double prob;
		double q = rCost - prevAcceptedRewriteCost;
		double temp;
		temp = Math.exp(-q);
		if (1.0 < temp)
			prob = 1.0;
		else
			prob = temp;
		return prob;
	}
	
	
	public static void storeAcceptedRewriteCost(double cost) {
		prevAcceptedRewriteCost = cost;
		System.out.println("prevAcceptedRewriteCost: " + prevAcceptedRewriteCost);
	}
	
	
	public static ArrayList<Integer> editMaxLibRelsMCMC(int toMutate, Object[] mutatedObj, 
			                              ArrayList<String> relNames, ArrayList<Integer> relStartNdx) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (toMutate < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && toMutate >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
		
		ArrayList<Object[]> maxRelData = maxInLibRelsMCMC.get(ndx);
		int delNdx = toMutate - relStartNdx.get(ndx);
		Object[] delElem = maxRelData.remove(delNdx);
		if (mutatedObj != null) maxRelData.add(delNdx, mutatedObj);
		
		ArrayList<Integer> newStartNdx;
		if (mutatedObj == null) {
			newStartNdx = new ArrayList<Integer>();
			for (int i = 0; i <= ndx; i++)
				newStartNdx.add(relStartNdx.get(i));
			for (int i = ndx + 1; i < relStartNdx.size(); i++)
				newStartNdx.add(relStartNdx.get(i) - 1);
		} else
			newStartNdx = relStartNdx;

		if (mutatedObj == null) {
			delOutMCMC.print(relNames.get(ndx) + ": ");
			for (int k = 0; k < delElem.length; k++)
				if (delElem[k] == null)
					delOutMCMC.print("(null)   ");
				else
					delOutMCMC.print(delElem[k].toString() + "   ");
			delOutMCMC.println();
		} else {
			mutAccMCMC.print("Replacing " + relNames.get(ndx) + ": ");
			for (int k = 0; k < delElem.length; k++)
				if (delElem[k] == null)
					mutAccMCMC.print("(null)   ");
				else
					mutAccMCMC.print(delElem[k].toString() + "   ");
			mutAccMCMC.println();
			mutAccMCMC.print("With " + relNames.get(ndx) + ": ");
			for (int k = 0; k < mutatedObj.length; k++)
				if (mutatedObj[k] == null)
					mutAccMCMC.print("(null)   ");
				else
					mutAccMCMC.print(mutatedObj[k].toString() + "   ");
			mutAccMCMC.println();
		}
		return newStartNdx;
	}
	
	
	public static void logRejectedRewrite(int toMutate, Object[] mutatedObj,
			                              ArrayList<String> relNames, ArrayList<Integer> relStartNdx) {
		int ndx = -1;
		for (int i = 1; i < relStartNdx.size(); i++) {
			if (toMutate < relStartNdx.get(i)) {
				ndx = i - 1;
				break;
			}
		}	
		if (ndx == -1 && toMutate >= relStartNdx.get(relStartNdx.size() -1))
			ndx = relStartNdx.size() - 1;
		
		mutRejMCMC.print("Rejecting " + relNames.get(ndx) + ": ");
		for (int k = 0; k < mutatedObj.length; k++)
			if (mutatedObj[k] == null)
				mutRejMCMC.print("(null)   ");
			else
				mutRejMCMC.print(mutatedObj[k].toString() + "   ");
		mutRejMCMC.println();
	}
}
