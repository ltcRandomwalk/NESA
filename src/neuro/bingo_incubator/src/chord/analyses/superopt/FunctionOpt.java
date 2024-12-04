package chord.analyses.superopt;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

public class FunctionOpt {
	private static HashMap<String, ArrayList<Object[]>> pristineInAppRels = new HashMap<String, ArrayList<Object[]>>();
	private static HashMap<jq_Method, HashMap<String, ArrayList<Object[]>>> maxInLibRels = 
			                 new HashMap<jq_Method, HashMap<String, ArrayList<Object[]>>>();
	private static HashMap<String, ArrayList<Object[]>> tempOrigRels = new HashMap<String, ArrayList<Object[]>>();
	private static ArrayList<String> dstFromRels = new ArrayList<String>();
	private static ArrayList<String> srcFromRels = new ArrayList<String>();
	private static HashMap<jq_Method, ArrayList<Pair<Register, Register>>> varTypeChk =
            new HashMap<jq_Method, ArrayList<Pair<Register, Register>>>();
	
	private static final String MOD_TUPLES_FILE_MCMCF = "modified_tuples_MCMCF";
	private static final PrintWriter modOut = OutDirUtils.newPrintWriter(MOD_TUPLES_FILE_MCMCF);
	private static ArrayList<String> modifications = new ArrayList<String>();
	private static String filtPrefix = "";
	
	
	public static void setFiltPrefix (String s) {
		filtPrefix = s;
	}
	
	
	public static void closeLogs () {
		modOut.close();
	}
	
	public static void flushLogs () {
		modOut.flush();
	}
	
	
	public static void dumpLibMethods() {
		for (jq_Method m : maxInLibRels.keySet()) {
			System.out.println("******************************************************");
			System.out.println("METHOD: " + m.toString());
			System.out.println("--------------------------------------");
			HashMap<String, ArrayList<Object[]>> rels = maxInLibRels.get(m);
			for ( String relName: rels.keySet()) {
				ArrayList<Object[]> rel = rels.get(relName);
				for (Object[] tuples : rel) {
					System.out.print(relName + ": ");
					for (int k = 1; k < tuples.length; k++) {
						if (tuples[k] == null)
							System.out.print("(null)   ");
						else
							System.out.print(tuples[k].toString() + "   ");
					}
					System.out.println();
				}
			}
		}
	}
	
	
	public static void inititalizeForFuncOpt (ArrayList<String> inRels) {
		for (String relName : inRels) {
			String pRelName = "asave_" + relName;
			ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pRelName);
			prel.load();
			ArrayList<Object[]> relElems = new ArrayList<Object[]>();
			for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
			pristineInAppRels.put(relName, relElems);
			prel.close();
		}
	
		for (String relName : inRels) {
			ProgramRel librel = (ProgramRel) ClassicProject.g().getTrgt("lsave_" + relName);
			librel.load();

			for (Object[] tuple : librel.getAryNValTuples()) {
				jq_Method m = (jq_Method)tuple[0];
				HashMap<String, ArrayList<Object[]>> relsPerMethod = null;
				if (maxInLibRels.containsKey(m))
					relsPerMethod = maxInLibRels.get(m);
				else {
					relsPerMethod = new HashMap<String, ArrayList<Object[]>>();
					maxInLibRels.put(m, relsPerMethod);
				}
				ArrayList<Object[]> relElems = null;
				if (relsPerMethod.containsKey(relName))
					relElems = relsPerMethod.get(relName);
				else {
					relElems = new ArrayList<Object[]>();
					relsPerMethod.put(relName, relElems);
				}
				relElems.add(tuple);
			}
			librel.close();
		}
	
		dstFromRels.add("MobjVarAsgnInst");
		dstFromRels.add("MobjValAsgnInst");
		dstFromRels.add("MgetInstFldInst");
		dstFromRels.add("MgetStatFldInst");
		
		srcFromRels.add("MobjVarAsgnInst");
		srcFromRels.add("MgetInstFldInst");
		srcFromRels.add("MputInstFldInst");
		srcFromRels.add("MputStatFldInst");
		srcFromRels.add("MIinvkArg");
		srcFromRels.add("MmethRet");
		srcFromRels.add("MputInstFldInst"); // present twice on purpose; Also, NEEDS TO BE THE LAST ENTRY
	}

	
	public static Set<jq_Method> getMethods(String methods) {
		if (methods.equals(""))
			return maxInLibRels.keySet();
		else {
			Set<jq_Method> mset = new HashSet<jq_Method>();
			String[] m = methods.split(",");
			for (int i = 0; i < m.length; i++) {
				boolean found = false;
				for (jq_Method meth: maxInLibRels.keySet()) {
					if (meth.toString().equals(m[i])) {
							mset.add(meth);
							found = true;
							break;
					}
				}
				if (!found)
					System.out.println("getMethods: Unable to find method: " + m[i]);
			}
			return mset;
		}
	}
	
	
	private static void storeOrig (String relName, jq_Method m) {
		tempOrigRels.remove(relName);
		ArrayList<Object[]> origTuples = new ArrayList<Object[]>();
		tempOrigRels.put(relName, origTuples);
		ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(filtPrefix + relName);
		prel.load();
		for (Object[] tuple : prel.getAryNValTuples()) {
			jq_Method tm = (jq_Method)tuple[0];
			if (tm.equals(m))
				origTuples.add(tuple);
		}
		prel.close();
	}
	

	private static void loadModifiedRel (String name) {
		String pname = filtPrefix + name;
		boolean excl = false;
		if (name.equals("MIinvkArg")) {
			pname = filtPrefix + "IinvkArg";
			excl = true;
		}
		if (name.equals("MIinvkArg0")) {
			pname = filtPrefix + "IinvkArg0";
			excl = true;
		}
		ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pname);
		prel.load();
		prel.zero();
		ArrayList<Object[]> tuples;
		tuples = pristineInAppRels.get(name);
		if (tuples != null) {
			for (int i = 0; i < tuples.size(); i++) {
				Object[] currTuple = tuples.get(i);
				Object[] finalTuple;
				if (excl) {
					finalTuple = new Object[currTuple.length - 1];
					for (int j = 0; j < currTuple.length - 1; j++)
						finalTuple[j] = currTuple[j + 1];
				} else
					finalTuple = currTuple;
				Util.addToRel(prel, finalTuple);
			}
		}
		for (jq_Method m : maxInLibRels.keySet()) {
			tuples = maxInLibRels.get(m).get(name);
			if (tuples != null) {
				for (int i = 0; i < tuples.size(); i++) {
					Object[] currTuple = tuples.get(i);
					Object[] finalTuple;
					if (excl) {
						finalTuple = new Object[currTuple.length - 1];
						for (int j = 0; j < currTuple.length - 1; j++)
							finalTuple[j] = currTuple[j + 1];
					} else
						finalTuple = currTuple;
					Util.addToRel(prel, finalTuple);
				}
			}
		}
		prel.save();
		
		if (name.equals("MIinvkArg") || name.equals("MIinvkArg0")) {
			prel = (ProgramRel) ClassicProject.g().getTrgt(filtPrefix + name);
			prel.load();
			prel.zero();
			tuples = pristineInAppRels.get(name);
			if (tuples != null) {
				for (int i = 0; i < tuples.size(); i++) {
					Object[] currTuple = tuples.get(i);
					Util.addToRel(prel, currTuple);
				}
			}
			for (jq_Method m : maxInLibRels.keySet()) {
				tuples = maxInLibRels.get(m).get(name);
				if (tuples != null) {
					for (int i = 0; i < tuples.size(); i++) {
						Object[] currTuple = tuples.get(i);
						Util.addToRel(prel, currTuple);
					}
				}
			}
			prel.save();
		}
	}
	
	public static void rejectChanges (jq_Method m) {
		HashMap<String, ArrayList<Object[]>> methodRels = maxInLibRels.get(m);
		for (String s: tempOrigRels.keySet()) {
			methodRels.remove(s);
			methodRels.put(s, tempOrigRels.get(s));
			loadModifiedRel(s);
		}
	}

	
	public static void makeFinalState () {
		for (String s: pristineInAppRels.keySet())
			loadModifiedRel(s);
	}
	
	
	public static void dumpOut (jq_Method m, int trial, boolean dump) {
		if (dump) {
			for (String s : modifications)
				modOut.println(trial + ": METH: " + m.toString() + ": " + s);
		}
		modifications.clear();
	}
	
	/*******
	public static boolean mcmcf (jq_Method m, ArrayList<String> inRels) {
		boolean dstRegSzZero = true;
		int numMethDstRels = 0;
		ArrayList<String> mdstFromRels = new ArrayList<String>();
		for (String s: dstFromRels) {
			ArrayList<Object[]> tuples = maxInLibRels.get(m).get(s);
			if (tuples != null && tuples.size() > 0) {
				dstRegSzZero = false;
				numMethDstRels++;
				mdstFromRels.add(s);
			}
		}
		boolean srcRegSzZero = true;
		int numMethSrcRels = 0;
		ArrayList<String> msrcFromRels = new ArrayList<String>();
		for (String s: srcFromRels) {
			ArrayList<Object[]> tuples = maxInLibRels.get(m).get(s);
			if (tuples != null && tuples.size() > 0) {
				srcRegSzZero = false;
				numMethSrcRels++;
				msrcFromRels.add(s);
			}
		}
		if (dstRegSzZero || srcRegSzZero) return false;
		
		int randDstRelNdx = Util.randInt(0, numMethDstRels - 1);
		String randDstRelName = mdstFromRels.get(randDstRelNdx);
		ArrayList<Object[]> randDstRel = maxInLibRels.get(m).get(randDstRelName);
		int randDstTuple = Util.randInt(0, randDstRel.size() - 1);
		Register candidateDstReg = getDstReg(randDstRel.get(randDstTuple), randDstRelName);
		if (randDstRelName.equals("MobjVarAsgnInst")) {
			candidateDstReg = (Register) randDstRel.get(randDstTuple)[2];
		}
		
		int randSrcRelNdx = Util.randInt(0, numMethSrcRels - 1);
		String randSrcRelName = msrcFromRels.get(randSrcRelNdx);
		ArrayList<Object[]> randSrcRel = maxInLibRels.get(m).get(randSrcRelName);
		int randSrcTuple = Util.randInt(0, randSrcRel.size() - 1);
		Object[] toMutate = randSrcRel.get(randSrcTuple);
		boolean later = (randSrcRelNdx == (numMethSrcRels - 1));
		int candidateSrcNdx = getSrcRegPos(toMutate, randSrcRelName, later);

	    //mutation
		tempOrigRels.clear();
		storeOrig(randSrcRelName, m);
		toMutate[candidateSrcNdx] = candidateDstReg;
		modifications.add(randSrcRelName + ": Replacing src" + " with " + candidateDstReg);
		loadModifiedRel(randSrcRelName);		
		if (randSrcRelName.equals("MIinvkArg"))
			checkAndFixArg0(m, toMutate);
		
		return true;
	}
	*******/
	
	
	public static boolean mcmcf (jq_Method m, ArrayList<String> inRels) {
		ArrayList<Pair<Integer,Object[]>> dsts = new ArrayList<Pair<Integer,Object[]>>();
		ArrayList<Pair<Integer,Object[]>> srcs = new ArrayList<Pair<Integer,Object[]>>();
		ArrayList<Pair<Pair<Integer,Object[]>,Pair<Integer,Object[]>>> mutationCandidates = 
				new ArrayList<Pair<Pair<Integer,Object[]>,Pair<Integer,Object[]>>>();
		
		ArrayList<String> mdstFromRels = new ArrayList<String>();
		int ndx = 0;
		for (String s: dstFromRels) {
			ArrayList<Object[]> tuples = maxInLibRels.get(m).get(s);
			if (tuples != null && tuples.size() > 0) {
				mdstFromRels.add(s);
				for (Object[] objarr: tuples) dsts.add(new Pair<Integer,Object[]>(ndx, objarr));
				ndx++;
			}
		}
		int numMethSrcRels = 0;
		ArrayList<String> msrcFromRels = new ArrayList<String>();
		ndx = 0;
		for (String s: srcFromRels) {
			ArrayList<Object[]> tuples = maxInLibRels.get(m).get(s);
			if (tuples != null && tuples.size() > 0) {
				numMethSrcRels++;
				msrcFromRels.add(s);
				for (Object[] objarr: tuples) srcs.add(new Pair<Integer,Object[]>(ndx, objarr));
				ndx++;
			}
		}
		
		ArrayList<Pair<Register,Register>> typMatchingVars = varTypeChk.get(m);
		int totalMutationCandidates = 0;
		
		for (Pair<Integer,Object[]> dst : dsts) {
			String dstRelName = mdstFromRels.get(dst.val0);
			Register candidateDstReg = getDstReg(dst.val1, dstRelName);
			if (dstRelName.equals("MobjVarAsgnInst")) {
				candidateDstReg = (Register) dst.val1[2];
			}
			for (Pair<Integer,Object[]> src : srcs) {
				String srcRelName = msrcFromRels.get(src.val0);
				boolean later = (src.val0 == (numMethSrcRels - 1));
				int candidateSrcNdx = getSrcRegPos(src.val1, srcRelName, later);
				Register candidateSrcReg = (Register)src.val1[candidateSrcNdx];
				if ( typMatchingVars.contains(new Pair<Register,Register>(candidateDstReg,candidateSrcReg)) ||
				     typMatchingVars.contains(new Pair<Register,Register>(candidateSrcReg,candidateDstReg)) 
				   ) {
					mutationCandidates.add(new Pair<Pair<Integer,Object[]>,Pair<Integer,Object[]>>(dst,src));
					totalMutationCandidates++;
				}
			}
		}
		
		if (totalMutationCandidates == 0) return false;
		
		int randMutationCandidateNdx = Util.randInt(0, totalMutationCandidates - 1);
		Pair<Pair<Integer,Object[]>,Pair<Integer,Object[]>> randMutationCandidate = 
				mutationCandidates.get(randMutationCandidateNdx);
		Object[] randDstTuple = randMutationCandidate.val0.val1;
		int randDstRelNdx = randMutationCandidate.val0.val0;
		
		String randDstRelName = mdstFromRels.get(randDstRelNdx);
		Register candidateDstReg = getDstReg(randDstTuple, randDstRelName);
		if (randDstRelName.equals("MobjVarAsgnInst")) {
			candidateDstReg = (Register) randDstTuple[2];
		}
		
		Object[] randSrcTuple = randMutationCandidate.val1.val1;
		int randSrcRelNdx = randMutationCandidate.val1.val0;
		String randSrcRelName = msrcFromRels.get(randSrcRelNdx);
		
		Object[] toMutate = randSrcTuple;
		boolean later = (randSrcRelNdx == (numMethSrcRels - 1));
		int candidateSrcNdx = getSrcRegPos(toMutate, randSrcRelName, later);

	    //mutation
		tempOrigRels.clear();
		storeOrig(randSrcRelName, m);
		toMutate[candidateSrcNdx] = candidateDstReg;
		modifications.add(randSrcRelName + ": Replacing src" + " with " + candidateDstReg);
		loadModifiedRel(randSrcRelName);		
		if (randSrcRelName.equals("MIinvkArg"))
			checkAndFixArg0(m, toMutate);
		
		return true;
	}
	
	public static ArrayList<String> getDelRels (ArrayList<String> inRels, String deletionStrategy) {
		ArrayList<String> delRels = new ArrayList<String>();
		if (deletionStrategy.equals("aggressive")) {
			for (String s: inRels) {
				if (!s.equals("MmethRet")) {
					delRels.add(s);
				}
			}
		} else {
			for (String s: inRels) {
				if (!s.equals("MIinvkArg0")
					&& !s.equals("MIinvkArg")
					&& !s.equals("MI")
					&& !s.equals("MputInstFldInst") 
					&& !s.equals("MputStatFldInst") 
					&& !s.equals("MmethRet")) {
					delRels.add(s);
				}
			}
		}
		return delRels;
	}
	
	
	public static boolean fdel (jq_Method m, ArrayList<String> delRels) {
		
		int mNumRels = 0;
		ArrayList<String> mRels = new ArrayList<String>();
		for (String s: delRels) {
			if (maxInLibRels.get(m).containsKey(s)) {
				ArrayList<Object[]> relList = maxInLibRels.get(m).get(s);
				if (relList != null && relList.size() > 0) {
					mNumRels++;
					mRels.add(s);
				}
			}
		}
		if (mNumRels == 0) return true;
		int randRelNdx = Util.randInt(0, mNumRels - 1);
		String randRelName = mRels.get(randRelNdx);
		ArrayList<Object[]> randRel = maxInLibRels.get(m).get(randRelName);
		int randTuple = Util.randInt(0, randRel.size() - 1);
		
	    //deletion
		tempOrigRels.clear();
		storeOrig(randRelName, m);
		Object[] deletedTuple = randRel.remove(randTuple);
		StringBuilder sb = new StringBuilder();
		sb.append(randRelName + ": Deleting: ");
		for (int i = 0; i < deletedTuple.length; i++) {
			if (deletedTuple[i] == null)
				sb.append("(null)   ");
			else
				sb.append(deletedTuple[i].toString() + "  ");
		}
		modifications.add(sb.toString());
		loadModifiedRel(randRelName);		
		if (randRelName.equals("MIinvkArg"))
			checkAndFixArg0ForDel(m, deletedTuple);
		
		return true;
	}
	
	
	private static void checkAndFixArg0 (jq_Method m, Object[] invkArg) {
		Integer argPos = (Integer) invkArg[2];
		if (argPos == 0) {
			storeOrig("MIinvkArg0", m);
			Quad invkInst = (Quad) invkArg[1];
			Register dst = (Register) invkArg[3];
			ArrayList<Object[]> arg0Rel = maxInLibRels.get(m).get("MIinvkArg0");
			if (arg0Rel != null) {
				for (Object[] tuple: arg0Rel) {
					Quad iinst = (Quad) tuple[1];
					if (iinst.equals(invkInst)) {
						tuple[2] = invkArg[3];
						modifications.add("MIinvkArg0" + ": Replacing src" + " with " + dst);
						break;
					}
				}
				loadModifiedRel("MIinvkArg0");
			}
		}
	}
	
	
	private static void checkAndFixArg0ForDel (jq_Method m, Object[] invkArg) {
		Integer argPos = (Integer) invkArg[2];
		if (argPos == 0) {
			Quad invkInst = (Quad) invkArg[1];
			ArrayList<Object[]> arg0Rel = maxInLibRels.get(m).get("MIinvkArg0");
			if (arg0Rel != null) {
				int delNdx = -1;
				for (int i = 0; i < arg0Rel.size(); i++) {
					Object[] tuple = arg0Rel.get(i);
					Quad iinst = (Quad) tuple[1];
					if (iinst.equals(invkInst)) {
						delNdx = i;
						break;
					}
				}
				if (delNdx > -1) {
					storeOrig("MIinvkArg0", m);
					Object[] deletedTuple = arg0Rel.remove(delNdx);
					StringBuilder sb = new StringBuilder();
					sb.append("MIinvkArg0: Deleting: ");
					for (int i = 0; i < deletedTuple.length; i++) {
						if (deletedTuple[i] == null)
							sb.append("(null)   ");
						else
							sb.append(deletedTuple[i].toString() + "  ");
					}
					modifications.add(sb.toString());
					loadModifiedRel("MIinvkArg0");
				}
			}
		}
	}
	
	
	private static Register getDstReg (Object[] tuple, String relName) {
		Register retval;
		if (relName.equals("MobjVarAsgnInst")) {
			retval = (Register) tuple[1];
		} else if (relName.equals("MobjValAsgnInst")) {
			retval = (Register) tuple[1];
		} else if (relName.equals("MgetInstFldInst")) {
			retval = (Register) tuple[1];
		} else if (relName.equals("MgetStatFldInst")) {
			retval = (Register) tuple[1];
		} else retval = null;
		return retval;
	}
	
	private static int getSrcRegPos (Object[] tuple, String relName, boolean later) {
		int retval;
		if (relName.equals("MobjVarAsgnInst")) {
			retval = 2;
		} else if (relName.equals("MgetInstFldInst")) {
			retval = 2;
		} else if (relName.equals("MputInstFldInst")) {
			// both 1 and 3 are "sources" i.e. "use" points.
			if (later)
				retval = 3; 
			else
				retval = 1;
		} else if (relName.equals("MputStatFldInst")) {
			retval = 2;
		} else if (relName.equals("MIinvkArg")) {
			retval = 3;
		} else if (relName.equals("MmethRet")) {
			retval = 2;
		} else retval = -1;
		return retval;
	}
	
	
	public static void prepForReuse (ArrayList<String> inRels) {
		assert (Config.reuseRels);
		for (String relName : inRels) {
			ProgramRel librel = (ProgramRel) ClassicProject.g().getTrgt("lsave_" + relName);
			librel.load();
			PrintWriter relOut;
			relOut = OutDirUtils.newPrintWriter(relName + "_opt.txt");
			for (int[] tuple : librel.getAryNIntTuples()) {
				for (int i = 0; i < tuple.length; i++) relOut.print(tuple[i] + " ");
				relOut.println("");
			}
			relOut.close();
			librel.close();
		}
	}
	
	
	public static HashMap<jq_Method, HashMap<String, ArrayList<Object[]>>> optimizedFuncRels = 
            new HashMap<jq_Method, HashMap<String, ArrayList<Object[]>>>();
	private static HashMap<String, ArrayList<Object[]>> savedMethodBody = null;
	
	public static void initializeForTest (ArrayList<String> inRels) {
		for (String relName : inRels) {
			ArrayList<Object[]> relElems = new ArrayList<Object[]>();
			Dom<?>[] reldoms;
			
			String pRelName = "asave_" + relName;
			ProgramRel prel = (ProgramRel) ClassicProject.g().getTrgt(pRelName);
			prel.load();
			for (Object[] tuple : prel.getAryNValTuples()) relElems.add(tuple);
			prel.close();
			reldoms = prel.getDoms();
			pristineInAppRels.put(relName, relElems);
			
			String fname = Config.outDirName + "/" + relName + "_opt.txt";
			System.out.println("initializeForTest: reading from: " + fname);
			File f = new File(fname);
			if (f.exists()) {
				List<String> l = Utils.readFileToList(fname);
				int temp1 = 0, temp2 = 0;
	            for (String s : l) {
	            	String[] parts = s.split(" ");
	            	temp1 = parts.length;
	            	temp2++;
	            	Object[] elem = new Object[parts.length];
	            	for (int j = 0; j < parts.length; j++) {
	            		int ndx = Integer.parseInt(parts[j]);
	            		elem[j] = reldoms[j].get(ndx);
	            	}
	            	jq_Method m = (jq_Method)elem[0];
	            	HashMap<String, ArrayList<Object[]>> relsPerMethod = null;
					if (optimizedFuncRels.containsKey(m))
						relsPerMethod = optimizedFuncRels.get(m);
					else {
						relsPerMethod = new HashMap<String, ArrayList<Object[]>>();
						optimizedFuncRels.put(m, relsPerMethod);
					}
					ArrayList<Object[]> relElems1 = null;
					if (relsPerMethod.containsKey(relName))
						relElems1 = relsPerMethod.get(relName);
					else {
						relElems1 = new ArrayList<Object[]>();
						relsPerMethod.put(relName, relElems1);
					}
					relElems.add(elem);
	            }
	            System.out.println("tuple length: " + temp1 + "   total num elems read:" + temp2);
			} else {
				System.out.println("File " + fname + " does not exist.");
			}
			
			ProgramRel librel = (ProgramRel) ClassicProject.g().getTrgt("lsave_" + relName);
			librel.load();
			for (Object[] tuple : librel.getAryNValTuples()) {
				jq_Method m = (jq_Method)tuple[0];
				HashMap<String, ArrayList<Object[]>> relsPerMethod = null;
				if (!maxInLibRels.containsKey(m)) {
					relsPerMethod = new HashMap<String, ArrayList<Object[]>>();
					maxInLibRels.put(m, relsPerMethod);
				} else {
					relsPerMethod = maxInLibRels.get(m);
				}
				
				ArrayList<Object[]> relElems1 = null;
				if (relsPerMethod.containsKey(relName))
					relElems1 = relsPerMethod.get(relName);
				else {
					relElems1 = new ArrayList<Object[]>();
					relsPerMethod.put(relName, relElems1);
				}
				relElems1.add(tuple);
			}
			librel.close();
		}
	}
	
	
	public static void loadForTest (ArrayList<String> inRels, jq_Method inclm, boolean sound) {
		if (inclm != null) {
			HashMap<String, ArrayList<Object[]>> methRels = optimizedFuncRels.get(inclm);
			savedMethodBody = maxInLibRels.get(inclm);
			maxInLibRels.remove(inclm);
			maxInLibRels.put(inclm, methRels);
			if (!sound) return;
		}
		
		for (String relName : inRels) {
			String pname = filtPrefix + relName;
			boolean excl = false;
			if (relName.equals("MIinvkArg")) {
				pname = filtPrefix + "IinvkArg";
				excl = true;
			}
			if (relName.equals("MIinvkArg0")) {
				pname = filtPrefix + "IinvkArg0";
				excl = true;
			}
			ProgramRel frel = (ProgramRel) ClassicProject.g().getTrgt(pname);
			frel.load();
			frel.zero();
			ArrayList<Object[]> relElems = pristineInAppRels.get(relName);
			for (Object[] tuple: relElems) {
				Object[] finalTuple;
				if (excl) {
					finalTuple = new Object[tuple.length - 1];
					for (int j = 0; j < tuple.length - 1; j++)
						finalTuple[j] = tuple[j + 1];
				} else
					finalTuple = tuple;
				Util.addToRel(frel, finalTuple);
			}
			
			for (jq_Method m : maxInLibRels.keySet()) {
				ArrayList<Object[]> tuples;
				tuples = maxInLibRels.get(m).get(relName);
				if (tuples != null) {
					for (int i = 0; i < tuples.size(); i++) {
						Object[] currTuple = tuples.get(i);
						Object[] finalTuple;
						if (excl) {
							finalTuple = new Object[currTuple.length - 1];
							for (int j = 0; j < currTuple.length - 1; j++)
								finalTuple[j] = currTuple[j + 1];
						} else
							finalTuple = currTuple;
						Util.addToRel(frel, finalTuple);
					}
				}
			}
			frel.save();
		}
	}
	
	public static void undoMethodInclusion (jq_Method inclm) {
		if (savedMethodBody == null) {
			System.out.println ("In undoMethodInclusion: null savedMethodBody");
			return;
		}
		System.out.println ("EXCLUDING OPTIMIZED METHOD: " + inclm.toString());
		maxInLibRels.remove(inclm);
		maxInLibRels.put(inclm, savedMethodBody);
	}
	
	
	public static HashMap<String, ArrayList<Object[]>> getOptMethodBody (jq_Method m) {
		HashMap<String, ArrayList<Object[]>> methRels = optimizedFuncRels.get(m);
		return methRels;
	}
	
	
	public static HashMap<String, ArrayList<Object[]>> getUnoptMethodBody (jq_Method m) {
		HashMap<String, ArrayList<Object[]>> methRels = maxInLibRels.get(m);
		return methRels;
	}
	
	public static void loadVarTypeChk () {
		ITask varType = ClassicProject.g().getTask("var-type-dlog");
		ClassicProject.g().runTask(varType);
		ProgramRel varTypRel = (ProgramRel) ClassicProject.g().getTrgt("VVfilter");
		varTypRel.load();

		for (Trio<Object, Object, Object> tuple : varTypRel.getAry3ValTuples()) {
			jq_Method m = (jq_Method)tuple.val0;
			ArrayList<Pair<Register,Register>> entriesPerMethod = null;
			if (varTypeChk.containsKey(m))
				entriesPerMethod = varTypeChk.get(m);
			else {
				entriesPerMethod = new ArrayList<Pair<Register,Register>>();
				varTypeChk.put(m, entriesPerMethod);
			}
			entriesPerMethod.add(new Pair<Register, Register>((Register)tuple.val1, (Register)tuple.val2));
		}
		varTypRel.close();
    }
}
