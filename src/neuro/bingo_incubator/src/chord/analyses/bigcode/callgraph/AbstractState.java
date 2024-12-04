package chord.analyses.bigcode.callgraph;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.util.ArraySet;
import chord.util.Utils;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class AbstractState {
	
	public static TObjectIntHashMap<String> strToMNdx;
	public static TObjectIntHashMap<String> strToINdx;
	public static TObjectIntHashMap<String> strToTNdx;
	public static HashMap<Integer, jq_Method> mNdxToM;
	public static HashMap<Integer, Quad> iNdxToI;
	public static HashMap<Integer, jq_Type> tNdxToT;
	public static HashMap<String, int[]> mark;
	public static HashMap<Integer, Integer> mNdxToMNdx;
	public static HashMap<Integer, Integer> iNdxToINdx;
	public static HashMap<Integer, Integer> tNdxToTNdx;
	public static ArrayList<String> mNdxToStrTrainApp;
	public static ArrayList<String> iNdxToStrTrainApp;
	public static ArrayList<String> tNdxToStrTrainApp;
	public boolean badValue;
	private int appAccessed;
	private int callbkPres;
	
	DomT domT = (DomT) ClassicProject.g().getTrgt("T");
	DomM domM = (DomM) ClassicProject.g().getTrgt("M");
	DomI domI = (DomI) ClassicProject.g().getTrgt("I");
	
	public BitSet reachT;
	public BitSet allocReachT;
	public BitSet reachM;
//	public Map<Quad, BitSet> callGraph;
//	public Map<jq_Reference, BitSet> subTypes;
	public TIntObjectHashMap<BitSet> callGraph;
	public TIntObjectHashMap<BitSet> subTypes;
	
	public HashSet<jq_Reference> reachTL;
	public HashSet<jq_Reference> allocReachTL;
	public HashSet<jq_Method> reachML;
	public HashMap<Quad, HashSet<jq_Method>> callGraphL;
	public HashMap<jq_Reference, HashSet<jq_Reference>> subTypesL;

	public AbstractState() {
		this.reachT = new BitSet();
		this.allocReachT = new BitSet();
		this.reachM = new BitSet();
		this.callGraph = new TIntObjectHashMap<BitSet>();
		this.subTypes = new TIntObjectHashMap<BitSet>();
		
		this.reachTL = new HashSet<jq_Reference>();
		this.allocReachTL = new HashSet<jq_Reference>();
		this.reachML = new HashSet<jq_Method>();
		this.callGraphL = new HashMap<Quad, HashSet<jq_Method>>();
		this.subTypesL = new HashMap<jq_Reference, HashSet<jq_Reference>>();
		
		badValue = false;
		appAccessed = -1;
		callbkPres = -1;
	}

	public AbstractState(String str, boolean isBitSet) {
		assert (mNdxToStrTrainApp != null);
		assert (iNdxToStrTrainApp != null);
		assert (tNdxToStrTrainApp != null);
		badValue = false;
		
		this.reachT = new BitSet();
		this.allocReachT = new BitSet();
		this.reachM = new BitSet();
		this.callGraph = new TIntObjectHashMap<BitSet>();
		this.subTypes = new TIntObjectHashMap<BitSet>();
		
		this.reachTL = new HashSet<jq_Reference>();
		this.allocReachTL = new HashSet<jq_Reference>();
		this.reachML = new HashSet<jq_Method>();
		this.callGraphL = new HashMap<Quad, HashSet<jq_Method>>();
		this.subTypesL = new HashMap<jq_Reference, HashSet<jq_Reference>>();
		
		if (str.equals("")) return;
		String[] envParts;

		if (isBitSet) {
			//parse reachT
			envParts = str.split(":ALLOCREACHT:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// reachT data
				String[] reachTStr = envParts[0].split("###");
				for (int i = 0; i < reachTStr.length; i++) {
					int ndx = Integer.parseInt(reachTStr[i]);
					Integer tNdx = tNdxToTNdx.get(ndx);
					if (tNdx != null)
						reachT.set(tNdx);
					else {
						badValue = true;
						break;
					}
				}
			}

			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":REACHM:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// allocReachT data
				String[] allocReachTStr = envParts[0].split("###");
				for (int i = 0; i < allocReachTStr.length; i++) {
					int ndx = Integer.parseInt(allocReachTStr[i]);
					Integer tNdx = tNdxToTNdx.get(ndx);
					if (tNdx != null) {
						allocReachT.set(tNdx);
					} else {
						badValue = true;
						break;
					}
				}
			}

			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":CALLGRAPH:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// reachM data
				String[] reachMStr = envParts[0].split("###");
				for (int i = 0; i < reachMStr.length; i++) {
					int ndx = Integer.parseInt(reachMStr[i]);
					Integer mNdx = mNdxToMNdx.get(ndx);
					if (mNdx != null)
						reachM.set(mNdx);
					else {
						badValue = true;
						break;
					}
				}
			}

			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":SUBTYPES:");
			//		assert(envParts.length > 0);
			if (envParts.length > 0 && !envParts[0].equals("")) {
				// callgraph data
				String[] cgParts = envParts[0].split("%%%");
				for (int i = 0; i < cgParts.length; i++) {
					String[] targets = cgParts[i].split("###");
					//The first element of targets is the invoke quad
					int invkNdx = Integer.parseInt(targets[0]);
					Integer iNdx = iNdxToINdx.get(invkNdx);
					if (iNdx != null) {
						BitSet tgtM = new BitSet();
						for (int j = 1; j < targets.length; j++) {
							int ndx = Integer.parseInt(targets[j]);
							Integer mNdx = mNdxToMNdx.get(ndx);
							if (mNdx != null) 
								tgtM.set(mNdx);
							else {
								badValue = true;
								break;
							}
						}
						if (!badValue)
							callGraph.put(iNdx, tgtM);
					} else {
						badValue = true;
						break;
					}
					if (badValue) break;
				}
			}

			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			if (!envParts[1].equals("")) {
				// subtypes data
				String[] subParts = envParts[1].split("%%%");
				for (int i = 0; i < subParts.length; i++) {
					String[] subTs = subParts[i].split("###");
					//The first element of subTs is the super type
					int superNdx = Integer.parseInt(subTs[0]);
					Integer superTNdx = tNdxToTNdx.get(superNdx);
					if (superTNdx != null) {
						BitSet subT = new BitSet();
						for (int j = 1; j < subTs.length; j++) {
							int ndx = Integer.parseInt(subTs[j]);
							Integer subTNdx = tNdxToTNdx.get(ndx);
							if (subTNdx != null) 
								subT.set(subTNdx);
							else {
								badValue = true;
								break;
							}
						}
						if (!badValue)
							subTypes.put(superTNdx, subT);
					} else {
						badValue = true;
						break;
					}
					if (badValue) break;
				}
			}
			
		} else { // Don't use bitsets
			//parse reachT
			envParts = str.split(":ALLOCREACHT:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// reachT data
				String[] reachTStr = envParts[0].split("###");
				for (int i = 0; i < reachTStr.length; i++) {
					int ndx = Integer.parseInt(reachTStr[i]);
					jq_Type addT = tNdxToT.get(ndx);
					if (addT != null)
						reachTL.add((jq_Reference) addT);
					else {
						badValue = true;
						break;
					}
				}
			}

			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":REACHM:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// allocReachT data
				String[] allocReachTStr = envParts[0].split("###");
				for (int i = 0; i < allocReachTStr.length; i++) {
					int ndx = Integer.parseInt(allocReachTStr[i]);
					jq_Type addT = tNdxToT.get(ndx);
					if (addT != null)
						allocReachTL.add((jq_Reference) addT);
					else {
						badValue = true;
						break;
					}
				}
			}
			
			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":CALLGRAPH:");
			assert(envParts.length > 0);
			if (!envParts[0].equals("")) {
				// reachM data
				String[] reachMStr = envParts[0].split("###");
				for (int i = 0; i < reachMStr.length; i++) {
					int ndx = Integer.parseInt(reachMStr[i]);
					jq_Method addM = mNdxToM.get(ndx);
					if (addM != null)
						reachML.add(addM);
					else {
						badValue = true;
						break;
					}
				}
			}
			
			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			envParts = envParts[1].split(":SUBTYPES:");
//			assert(envParts.length > 0);
			if (envParts.length > 0 && !envParts[0].equals("")) {
				// callgraph data
				String[] cgParts = envParts[0].split("%%%");
				for (int i = 0; i < cgParts.length; i++) {
					String[] targets = cgParts[i].split("###");
					//The first element of targets is the invoke quad
					int invkNdx = Integer.parseInt(targets[0]);
					Quad invk = iNdxToI.get(invkNdx);
					if (invk != null) {
						HashSet<jq_Method> tgtM = new HashSet<jq_Method>();
						for (int j = 1; j < targets.length; j++) {
							int ndx = Integer.parseInt(targets[j]);
							jq_Method addM = mNdxToM.get(ndx);
							if (addM != null)
								tgtM.add(addM);
							else {
								badValue = true;
								break;
							}
						} 
						if (!badValue)
							callGraphL.put(invk, tgtM);
					} else {
						badValue = true;
						break;
					}
					if (badValue) break;
				}
			}
			
			if (!(!badValue && envParts.length == 2 && !envParts[1].equals(""))) return;
			if (!envParts[1].equals("")) {
				// subtypes data
				String[] subParts = envParts[1].split("%%%");
				for (int i = 0; i < subParts.length; i++) {
					String[] subTs = subParts[i].split("###");
					//The first element of subTs is the super type
					int superNdx = Integer.parseInt(subTs[0]);
					jq_Type superT = tNdxToT.get(superNdx);
					if (superT != null) {
						HashSet<jq_Reference> subT = new HashSet<jq_Reference>();
						for (int j = 1; j < subTs.length; j++) {
							int ndx = Integer.parseInt(subTs[j]);
							jq_Type addT = tNdxToT.get(ndx);
							if (addT != null) 
								subT.add((jq_Reference) addT);
							else {
								badValue = true;
								break;
							}
						}
						if (!badValue)
							subTypesL.put((jq_Reference) superT, subT);
					} else {
						badValue = true;
						break;
					}
					if (badValue) break;
				}
			}
		}
		appAccessed = -1;
		callbkPres = -1;
		
	}

	public boolean isEmpty() {
		return (reachT.isEmpty() && allocReachT.isEmpty() &&
			reachM.isEmpty() && callGraph.isEmpty() && subTypes.isEmpty() &&
			reachTL.isEmpty() && allocReachTL.isEmpty() &&
			reachML.isEmpty() && callGraphL.isEmpty() && subTypesL.isEmpty());
	}
	
	@Override
	public int hashCode() {
		return reachT.hashCode() + allocReachT.hashCode() +
			reachM.hashCode() + callGraph.hashCode() + subTypes.hashCode() +
			reachTL.hashCode() + allocReachTL.hashCode() +
			reachML.hashCode() + callGraphL.hashCode() + subTypesL.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof AbstractState) {
			AbstractState that = (AbstractState) obj;
			return Utils.areEqual(this.reachT, that.reachT) &&
				Utils.areEqual(this.allocReachT, that.allocReachT) &&
				Utils.areEqual(this.reachM, that.reachM) &&
				Utils.areEqual(this.callGraph, that.callGraph) &&
				Utils.areEqual(this.subTypes, that.subTypes) &&
				Utils.areEqual(this.reachTL, that.reachTL) &&
				Utils.areEqual(this.allocReachTL, that.allocReachTL) &&
				Utils.areEqual(this.reachML, that.reachML) &&
				Utils.areEqual(this.callGraphL, that.callGraphL) &&
				Utils.areEqual(this.subTypesL, that.subTypesL);
		}
		return false;
	}
	
	private boolean mergeBitSets(BitSet b1, BitSet b2) {
		BitSet b3 = (BitSet) b1.clone();
		b1.or(b2);
		return (!(b3.equals(b1)));
	}
	
	public boolean merge(AbstractState that) {
		if (that == null) return false;
		boolean modified = false;
		modified |= mergeBitSets(reachT, that.reachT);
		modified |= mergeBitSets(allocReachT,that.allocReachT);
		modified |= mergeBitSets(reachM, that.reachM);
		
		TIntObjectIterator<BitSet> iter = that.callGraph.iterator();
		while (iter.hasNext()) {
			iter.advance();
			int invk = iter.key();
			if (!callGraph.containsKey(invk)) {
				modified = true;
				BitSet targets = new BitSet();
				targets.or(iter.value());
				callGraph.put(invk, targets);
			} else {
				BitSet targets = callGraph.get(invk);
				modified |= mergeBitSets(targets, iter.value());
			}
		}
		
		iter = that.subTypes.iterator();
		while (iter.hasNext()) {
			iter.advance();
			int superT = iter.key();
			if (!subTypes.containsKey(superT)) {
				modified = true;
				BitSet subT = new BitSet();
				subT.or(iter.value());
				subTypes.put(superT, subT);
			} else {
				BitSet subT = subTypes.get(superT);
				modified |= mergeBitSets(subT, iter.value());
			}
		}
		return modified;
	}
	
/*	@Override
	public String toString() {
		String ret = "reachT=" + (reachT.isEmpty() ? "EMPTY" : "{");
		String[] reachTArr = new String[reachT.size()];
		int idx = 0;
		for (jq_Reference t : reachT) {
			reachTArr[idx++] = t.toString();
		}
		Arrays.sort(reachTArr);
		for (idx = 0; idx < reachTArr.length; idx++) {
			ret += reachTArr[idx] + "###";
		}
		ret += "},allocReachT=" + (allocReachT.isEmpty() ? "EMPTY, " : "");
		
		
		String[] allocReachTArr = new String[allocReachT.size()];
		idx = 0;
		for (jq_Reference t : allocReachT) {
			allocReachTArr[idx++] = t.toString();
		}
		Arrays.sort(allocReachTArr);
		for (idx = 0; idx < allocReachTArr.length; idx++) {
			ret += allocReachTArr[idx] + "###";
		}
		
		ret += "},reachM=" + (reachM.isEmpty() ? "EMPTY, " : "");
		
		String[] reachMArr = new String[reachM.size()];
		idx = 0;
		for (jq_Method m : reachM) {
			reachMArr[idx++] = m.toString();
		}
		Arrays.sort(reachMArr);
		for (idx = 0; idx < reachMArr.length; idx++) {
			ret += reachMArr[idx] + "###";
		}
		
		ret += "},callGraph=" + (callGraph.isEmpty() ? "EMPTY, " : "");
		String[] cgStrArr = new String[callGraph.keySet().size()];
		Map<String, Quad> cgStrToQuad = new HashMap<String, Quad>();
		idx = 0;
		for (Quad i : callGraph.keySet()){
			cgStrArr[idx++] = i.toString();
			cgStrToQuad.put(i.toString(), i);
		}
		Arrays.sort(cgStrArr);
		
		for (idx = 0; idx < cgStrArr.length; idx++){
			ret += "[" + cgStrArr[idx] + ",{";
			Set<jq_Method> targets = callGraph.get(cgStrToQuad.get(cgStrArr[idx]));
			String[] targetArr = new String[targets.size()];
			int idx1 = 0;
			for (jq_Method m : targets) {
				targetArr[idx1++] = m.toString();
			}
			Arrays.sort(targetArr);
			for (idx1 = 0; idx1 < targetArr.length; idx1++) {
				ret += targetArr[idx1] + "###";
			}
			ret += "}],";
		}
		
		ret += "},subTypes=" + (subTypes.isEmpty() ? "EMPTY, " : "");
		String[] subStrArr = new String[subTypes.keySet().size()];
		Map<String, jq_Reference> subStrToQuad = new HashMap<String, jq_Reference>();
		idx = 0;
		for (jq_Reference i : subTypes.keySet()){
			subStrArr[idx++] = i.toString();
			subStrToQuad.put(i.toString(), i);
		}
		Arrays.sort(subStrArr);
		
		for (idx = 0; idx < subStrArr.length; idx++){
			ret += "[" + subStrArr[idx] + ",{";
			Set<jq_Reference> subT = subTypes.get(subStrToQuad.get(subStrArr[idx]));
			String[] subTArr = new String[subT.size()];
			int idx1 = 0;
			for (jq_Reference t : subT) {
				subTArr[idx1++] = t.toString();
			}
			Arrays.sort(subTArr);
			for (idx1 = 0; idx1 < subTArr.length; idx1++) {
				ret += subTArr[idx1] + "###";
			}
			ret += "}],";
		}
		return ret;
	}
*/	
	
	public String toParsableString() {
		StringBuilder ret = new StringBuilder();
		
		boolean first = true;
		for (int qidx = reachT.nextSetBit(0); qidx >= 0; qidx = reachT.nextSetBit(qidx+1)){
			if (!first) ret.append("###");
			ret.append(qidx);
			first = false;
		}
		ret.append(":ALLOCREACHT:");
		
		first = true;
		for (int qidx = allocReachT.nextSetBit(0); qidx >= 0; qidx = allocReachT.nextSetBit(qidx+1)){
			if (!first) ret.append("###");
			ret.append(qidx);
			first = false;
			
		}
		ret.append(":REACHM:");
		
		first = true;
		for (int qidx = reachM.nextSetBit(0); qidx >= 0; qidx = reachM.nextSetBit(qidx+1)){
			if (!first) ret.append("###");
			ret.append(qidx);
			first = false;
			
		}
		ret.append(":CALLGRAPH:");

		first = true;
		TIntObjectIterator<BitSet> iter = callGraph.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (!first) ret.append("%%%");
			ret.append(iter.key() + "###");
			BitSet targets = iter.value();

			boolean first2 = true;
			for (int qidx = targets.nextSetBit(0); qidx >= 0; qidx = targets.nextSetBit(qidx+1)){
				if (!first2) ret.append("###");
				ret.append(qidx);
				first2 = false;
			}
			first = false;
		}
		
		ret.append(":SUBTYPES:");

		first = true;
		iter = subTypes.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (!first) ret.append("%%%");
			ret.append(iter.key() + "###");
			BitSet subT = iter.value();
			
			boolean first2 = true;
			for (int qidx = subT.nextSetBit(0); qidx >= 0; qidx = subT.nextSetBit(qidx+1)){
				if (!first2) ret.append("###");
				ret.append(qidx);
				first2 = false;
			}
			first = false;
		}
		return ret.toString();
	}
	
	public boolean isAppAccessed() {
		if (appAccessed == 0) 
			return false;
		else if (appAccessed == 1)
			return true;

		for (int qidx = reachT.nextSetBit(0); qidx >= 0; qidx = reachT.nextSetBit(qidx+1)){
			if (!belongsToLib(domT, qidx)) { appAccessed = 1; return true; }
		}
		
		for (int qidx = allocReachT.nextSetBit(0); qidx >= 0; qidx = allocReachT.nextSetBit(qidx+1)){
			if (!belongsToLib(domT, qidx)) { appAccessed = 1; return true; }
		}
		
		for (int qidx = reachM.nextSetBit(0); qidx >= 0; qidx = reachM.nextSetBit(qidx+1)){
			if (!belongsToLib(domM, qidx)) { appAccessed = 1; return true; }
		}
		
		TIntObjectIterator<BitSet> iter = callGraph.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (!belongsToLib(domI, iter.key())) { appAccessed = 1; return true; }
			
			BitSet targets = iter.value();
			for (int qidx = targets.nextSetBit(0); qidx >= 0; qidx = targets.nextSetBit(qidx+1)){
				if (!belongsToLib(domM, qidx)) { appAccessed = 1; return true; }
			}
		}
		
		iter = subTypes.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (!belongsToLib(domT, iter.key())) { appAccessed = 1; return true; }
			
			BitSet subT = iter.value();
			for (int qidx = subT.nextSetBit(0); qidx >= 0; qidx = subT.nextSetBit(qidx+1)){
				if (!belongsToLib(domT, qidx)) { appAccessed = 1; return true; }
			}
		}
		appAccessed = 0;
		return false;
	}
	
	public boolean IsCallbkPres() {
		if (callbkPres == 0) 
			return false;
		else if (callbkPres == 1)
			return true;
		
/*		TIntObjectIterator<BitSet> iter = callGraph.iterator();
		while (iter.hasNext()) {
			iter.advance();
			BitSet targets = iter.value();
			for (int qidx = targets.nextSetBit(0); qidx >= 0; qidx = targets.nextSetBit(qidx+1)){
				if (!belongsToLib(domM, qidx)) { callbkPres = 1; return true; }
			}
		}
*/		
		for (int qidx = reachM.nextSetBit(0); qidx >= 0; qidx = reachM.nextSetBit(qidx+1)){
			if (!belongsToLib(domM, qidx)) { callbkPres = 1; return true; }
		}
		
		callbkPres = 0;
		return false;
	}
	
	private boolean belongsToLib(Dom d, int i){
		int type = 0;
		if (mark.containsKey(d.getName())) {
			type |= ((int[])mark.get(d.getName()))[i];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
	
	public void setAppAccessed() {
		appAccessed = 1;
	}
	
	public void setCallbkPres() {
		callbkPres = 1;
	}
}


