package chord.analyses.composba;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

import chord.analyses.alloc.DomH;
import chord.analyses.var.DomV;
import chord.project.ClassicProject;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class BitAbstractState {
	
	public final BitEnv<Register> envLocal;
	public final BitSet returnVarEnv;
	public static TObjectIntHashMap<String> strToHNdx;
	public static TObjectIntHashMap<String> strToVNdx;
	public static DomV domV;
	public static boolean localityOpt;
	public static HashMap<String, int[]> mark;
	public static ArrayList<String> hNdxToStrTrainApp;
	public boolean badValue;
	private int appAccessed;
	
	public static BitSet markerBitSet = new BitSet();

	public BitAbstractState(BitEnv<Register> envLocal) {
		this(envLocal, new BitSet());
		appAccessed = -1;
		badValue = false;
	}

	public BitAbstractState(BitEnv<Register> envLocal, BitSet retVarEnv) {
		assert (envLocal != null && retVarEnv != null);
		this.envLocal = envLocal;
		returnVarEnv = retVarEnv;
		appAccessed = -1;
		badValue = false;
	}

	public BitAbstractState(BitEnv<Register> envLocal, String str) {
		assert (envLocal != null);
		assert (hNdxToStrTrainApp != null);
		this.envLocal = envLocal;
		returnVarEnv = new BitSet();
		badValue = false;
		
		if (!str.equals("")) {
			String[] envParts;
			envParts = str.split("LOCAL_ENV");
			if (envParts.length > 0 && !envParts[0].equals("")) {
				// ret env data
				String[] heapParts = envParts[0].split("###");
				for (int i = 0; i < heapParts.length; i++) {
					int ndx = Integer.parseInt(heapParts[i]);
					String s = null;
					if (ndx >= 0 && ndx < hNdxToStrTrainApp.size())
						s = hNdxToStrTrainApp.get(ndx);
					else {
						badValue = true;
						break;
					}
					if (strToHNdx.containsKey(s))
						returnVarEnv.set(strToHNdx.get(s));
					else {
						badValue = true;
						break;
					}
				}
			}
			if (!badValue && envParts.length == 2 && !envParts[1].equals("")) {
				// local var data
				String[] lclVarParts = envParts[1].split("%%%");
				for (int i = 0; i < lclVarParts.length; i++) {
					String[] heapParts = lclVarParts[i].split("###");
					//The first element of heapParts is the register corresponding to a local variable
					Register v = null;
					if (strToVNdx.containsKey(heapParts[0]))
						v = domV.get(strToVNdx.get(heapParts[0]));
					else {
						badValue = true;
						break;
					}
					if (v != null) {
						BitSet vBitSet = new BitSet();
						for (int j = 1; j < heapParts.length; j++) {
							int ndx = Integer.parseInt(heapParts[j]);
							String s = null;
							if (ndx >= 0 && ndx < hNdxToStrTrainApp.size())
								s = hNdxToStrTrainApp.get(ndx);
							else if (ndx == -1) {
								vBitSet = BitAbstractState.markerBitSet;
								break;
							} else {
								//System.out.println("Bad value for tr: " + ndx + "   " + hNdxToStrTrainApp.size());
								badValue = true;
								break;
							}
							if (strToHNdx.containsKey(s)) 
								vBitSet.set(strToHNdx.get(s));
							else {
								//System.out.println("Bad value for test: " + s);
								badValue = true;
								break;
							}
						}
						if (!badValue)
							envLocal.envMap.put(v, vBitSet);
					} else {
						badValue = true;
						break;
					}
					if (badValue) break;
				}
			}
		}	
		appAccessed = -1;
	}

	public boolean isEmpty() {
		if (!returnVarEnv.isEmpty()) return false;
		if (!envLocal.isEmpty()) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return envLocal.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof BitAbstractState) {
			BitAbstractState that = (BitAbstractState) obj;
			return envLocal.equals(that.envLocal) && returnVarEnv.equals(that.returnVarEnv);
		}
		return false;
	}

	public boolean subsumes(Object obj) {
		if (this == obj) return true;
		if (obj instanceof BitAbstractState) {
			BitAbstractState that = (BitAbstractState) obj;
			BitSet temp = new BitSet();
			temp = (BitSet) returnVarEnv.clone();
			temp.or(that.returnVarEnv);
			return (temp.equals(this.returnVarEnv) && this.envLocal.subsumes(that.envLocal));
		}
		return false;
	}
	
	@Override
	public String toString() {
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		String ret = "returnVarEnv=" + (returnVarEnv.isEmpty() ? "EMPTY" : "{");
		//ret += returnVarEnv.cardinality();
		int sz = returnVarEnv.cardinality();
		String[] retEnvArr = new String[sz];
		int idx = 0;
		for (int qidx = returnVarEnv.nextSetBit(0); qidx >= 0; qidx = returnVarEnv.nextSetBit(qidx+1)){
			Quad h = (Quad) domH.get(qidx);
			retEnvArr[idx++] = h.toString();
		}
		if (sz > 1)
			Arrays.sort(retEnvArr);
		for (idx = 0; idx < sz; idx++) {
			ret += retEnvArr[idx] + "###";
		}
		ret += "},tcLocal=" + (envLocal.isEmpty() ? "EMPTY, " : "");
		
		int regSz = envLocal.envMap.keySet().size();
		String[] regStrArr = new String[regSz];
		HashMap<String, BitSet> regStrToBitSet = new HashMap<String, BitSet>();
		idx = 0;
		for (Register v : envLocal.envMap.keySet()){
			regStrToBitSet.put(v.toString(), envLocal.envMap.get(v));
			regStrArr[idx++] = v.toString();
		}
		
		if (regSz > 1)
			Arrays.sort(regStrArr);
		
		for (idx = 0; idx < regSz; idx++){
			ret += "[" + regStrArr[idx] + ",{";
			//ret += envLocal.envMap.get(v).cardinality() + "}],";
			BitSet regEnv = regStrToBitSet.get(regStrArr[idx]);
			sz = regEnv.cardinality();
			String[] regEnvArr = new String[sz];
			int idx1 = 0;
			if (regEnv == BitAbstractState.markerBitSet) 
				ret += "STAR";
			for (int qidx = regEnv.nextSetBit(0); qidx >= 0; qidx = regEnv.nextSetBit(qidx+1)){
				Quad h = (Quad) domH.get(qidx);
				regEnvArr[idx1++] = h.toString();
			}
			if (sz > 1)
				Arrays.sort(regEnvArr);
			for (idx1 = 0; idx1 < sz; idx1++) {
				ret += regEnvArr[idx1] + "###";
			}
			ret += "}],";
		}	
		return ret;
	}
	
	
	public boolean isAppAccessed(TIntHashSet vSet) {
		if (appAccessed == 0) 
			return false;
		else if (appAccessed == 1)
			return true;

		for (int qidx = returnVarEnv.nextSetBit(0); qidx >= 0; qidx = returnVarEnv.nextSetBit(qidx+1)){
			if (!hBelongsToLib(qidx)) { appAccessed = 1; return true; }
		}
		for (Register r : envLocal.envMap.keySet()){
			if(localityOpt && vSet != null && !vSet.contains(domV.indexOf(r))) continue;
			BitSet regEnv = envLocal.envMap.get(r);
			for (int qidx = regEnv.nextSetBit(0); qidx >= 0; qidx = regEnv.nextSetBit(qidx+1)){
				if (!hBelongsToLib(qidx)) { appAccessed = 1; return true; }
			}
		}	
		appAccessed = 0;
		return false;
	}
	
	public boolean isAppAccessedInRetEnv() {
		for (int qidx = returnVarEnv.nextSetBit(0); qidx >= 0; qidx = returnVarEnv.nextSetBit(qidx+1)){
			if (!hBelongsToLib(qidx)) return true;
		}
		return false;
	}
	
	public String toParsableString(TIntHashSet vSet) {
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		StringBuilder ret = new StringBuilder();
		int sz = returnVarEnv.cardinality();
		int idx = 0;
		for (int qidx = returnVarEnv.nextSetBit(0); qidx >= 0; qidx = returnVarEnv.nextSetBit(qidx+1)){
			//ret += domH.toUniqueString(qidx);
			ret.append(qidx);
			if (idx < sz - 1) ret.append("###");   // ret += "###";
			idx++;
		}
		
		//ret += "LOCAL_ENV";
		ret.append("LOCAL_ENV");
		
		boolean first = true;
		for (Register v : envLocal.envMap.keySet()){
			if (!first) {
				ret.append("%%%");  // ret += "%%%";
			}
			//ret += domV.toUniqueString(v) + "###";
			ret.append(domV.toUniqueString(v) + "###");
			if(localityOpt && vSet != null && !vSet.contains(domV.indexOf(v))) {
				ret.append("-1");
				ret.append("###");
			} else {
				BitSet regEnv = envLocal.envMap.get(v);
				int regEnvSz = regEnv.cardinality();
				int idx1 = 0;
				for (int qidx = regEnv.nextSetBit(0); qidx >= 0; qidx = regEnv.nextSetBit(qidx+1)){
					//ret += domH.toUniqueString(qidx);
					ret.append(qidx);
					if (idx1 < regEnvSz - 1) ret.append("###");  // ret += "###";
					idx1++;
				}
			}
			first = false;
		}	
		return ret.toString();
	}
	
	public String toParsableStringRetEnv() {
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		StringBuilder ret = new StringBuilder();
		int sz = returnVarEnv.cardinality();
		int idx = 0;
		for (int qidx = returnVarEnv.nextSetBit(0); qidx >= 0; qidx = returnVarEnv.nextSetBit(qidx+1)){
			//ret += domH.toUniqueString(qidx);
			ret.append(qidx);
			if (idx < sz - 1) ret.append("###");   // ret += "###";
			idx++;
		}
		//ret += "LOCAL_ENV";
		ret.append("LOCAL_ENV");
		return ret.toString();
	}
	
	private boolean hBelongsToLib(int i){
		int type = 0;
		if (mark.containsKey("H")) {
			type |= ((int[])mark.get("H"))[i];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
}


