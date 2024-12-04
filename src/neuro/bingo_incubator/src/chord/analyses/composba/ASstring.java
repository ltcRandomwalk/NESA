package chord.analyses.composba;

import java.util.HashMap;
import java.util.HashSet;

public class ASstring {
	
	public HashMap<String, HashSet<String>> envLocal;
	public HashSet<String> returnVarEnv;
	
	public ASstring(String str) {
		returnVarEnv = new HashSet<String>();
		envLocal = new HashMap<String, HashSet<String>>();
		if (!str.equals("")) {
			String[] envParts;
			envParts = str.split("LOCAL_ENV");
			if (envParts.length > 0 && !envParts[0].equals("")) {
				// ret env data
				String[] heapParts = envParts[0].split("###");
				for (int i = 0; i < heapParts.length; i++)
					returnVarEnv.add(heapParts[i]);
			}
			if (envParts.length == 2 && !envParts[1].equals("")) {
				// local var data
				String[] lclVarParts = envParts[1].split("%%%");
				for (int i = 0; i < lclVarParts.length; i++) {
					String[] heapParts = lclVarParts[i].split("###");
					//The first element of heapParts is the register corresponding to a local variable
					String regName = heapParts[0];
					HashSet<String> regPointsTo = new HashSet<String>();
					for (int j = 1; j < heapParts.length; j++)
						regPointsTo.add(heapParts[j]);
					envLocal.put(regName, regPointsTo);
				}
			}
		}
	}

	public int hashCode() {
		return envLocal.hashCode();
	}

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof ASstring) {
			ASstring that = (ASstring) obj;
			return envLocal.equals(that.envLocal) && returnVarEnv.equals(that.returnVarEnv);
		}
		return false;
	}
	
	public String toString() {
		String ret = "";
		int sz = returnVarEnv.size();
		int idx = 0;
		for (String s : returnVarEnv){
			ret += s;
			if (idx < sz - 1) ret += "###";
			idx++;
		}
		
		ret += "LOCAL_ENV";
		
		int numReg = envLocal.keySet().size();
		idx = 0;
		for (String v : envLocal.keySet()){
			ret += v + "###";
			HashSet<String> regEnv = envLocal.get(v);
			int regEnvSz = regEnv.size();
			int idx1 = 0;
			for (String s1 : regEnv){
				ret += s1;
				if (idx1 < regEnvSz - 1) ret += "###";
				idx1++;
			}
			if (idx < numReg - 1) ret += "%%%";
			idx++;
		}	
		return ret;
	}
}


