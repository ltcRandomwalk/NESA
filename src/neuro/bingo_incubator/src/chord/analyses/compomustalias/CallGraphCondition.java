package chord.analyses.compomustalias;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.util.tuple.object.Pair;

public class CallGraphCondition {
	public static Map<jq_Method, Set<Pair<Quad,jq_Method>>> IMReachableFromM;
	public static DomI domI;
	public static DomM domM;
	public static HashMap<String, int[]> mark;
	public static ArrayList<String> iNdxToStrTrainApp;
	public static ArrayList<String> mNdxToStrTrainApp;
	public static TObjectIntHashMap<String> strToMNdx;
	public static TObjectIntHashMap<String> strToINdx;
	private static String sep = "###";
	
	public boolean hasAppCallbk;
	public String cStr;
	public boolean badValue;
	public HashSet<Pair<Quad, jq_Method>> reachableIMs;
	
	public CallGraphCondition() {
		cStr = null;
		hasAppCallbk = false;
		badValue = false;
		reachableIMs = new HashSet<Pair<Quad, jq_Method>>();
	}
		
	public void createCondnStr(jq_Method m) {
		StringBuilder condStr = new StringBuilder();
		Set<Pair<Quad,jq_Method>> calleeSet = null;
		if (IMReachableFromM.containsKey(m))
			calleeSet = IMReachableFromM.get(m);
		if (!(calleeSet == null)) {
    		for (Pair<Quad,jq_Method> p : calleeSet) {
    			int iNdx = domI.indexOf(p.val0);
    			int mNdx = domM.indexOf(p.val1);
    			condStr.append(iNdx);
    			condStr.append(sep);
    			condStr.append(mNdx);
    			condStr.append(sep);
    			if (!mBelongsToLib(mNdx)) {
    				hasAppCallbk = true;
    			}
    		}
		} else {
			condStr.append("null");
		}
		cStr = condStr.toString();
	}
	
	public void parse(String s) {
		String[] cgParts;
		cgParts = s.split(sep);
		if (!cgParts[0].equals("null")) {
			for (int i = 0; i < cgParts.length; i += 2) {
				String iStr = null;
				int iIdx = Integer.parseInt(cgParts[i]);
				if (iIdx >= 0 && iIdx < iNdxToStrTrainApp.size())
					iStr = iNdxToStrTrainApp.get(iIdx);
				
				Quad q = null;
				if (iStr != null && strToINdx.containsKey(iStr))
					q = domI.get(strToINdx.get(iStr));
				else {
					badValue = true;
					break;
				}
				
				String mStr = null;
				int mIdx = Integer.parseInt(cgParts[i+1]);
				if (mIdx >= 0 && mIdx < mNdxToStrTrainApp.size())
					mStr = mNdxToStrTrainApp.get(mIdx);
				
				jq_Method m = null;
				if (mStr != null && strToMNdx.containsKey(mStr))
					m = domM.get(strToMNdx.get(mStr));
				else {
					badValue = true;
					break;
				}
				reachableIMs.add(new Pair<Quad, jq_Method>(q, m));
			}
		}
	}
	
	public boolean isCGMatching(jq_Method m) {
		Set<Pair<Quad,jq_Method>> calleeSet = new HashSet<Pair<Quad,jq_Method>>();
		if (IMReachableFromM.containsKey(m))
			calleeSet = IMReachableFromM.get(m);
		if (reachableIMs != null && reachableIMs.equals(calleeSet))
			return true;
		return false;
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
}
