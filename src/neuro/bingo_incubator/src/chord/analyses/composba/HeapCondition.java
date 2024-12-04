package chord.analyses.composba;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;
import chord.analyses.field.DomF;
import chord.project.ClassicProject;
import chord.util.tuple.object.Pair;
import gnu.trove.map.hash.TObjectIntHashMap;


public class HeapCondition {
	public static RelTupleIndex hfhIndex;
	public static RelTupleIndex fhIndex;
	public static TObjectIntHashMap<String> strToHNdx;
	public static TObjectIntHashMap<String> strToFNdx;
	public static ArrayList<String> hNdxToStrTrainApp;
	public static ArrayList<String> fNdxToStrTrainApp;
	public static Map<jq_Field, BitSet> FHMap;
	public static Map<Pair<Integer, jq_Field>, BitSet> HFHMap;
	public static DomF domF;
	public Map<Pair<Integer, jq_Field>, BitSet> seHFHMap = new HashMap<Pair<Integer,jq_Field>, BitSet>();
	public Map<jq_Field, BitSet> seFHMap = new HashMap<jq_Field, BitSet>();
	private boolean badValue;
	private String hpStr;
	
	public HeapCondition() {
	}

	/******
	public HeapCondition(String hpStr) {
		seHFHMap = new HashMap<Pair<Integer,jq_Field>, BitSet>();
		String[] parts = hpStr.split(":FHDAT:");
		// hfh data is in parts[0] and fh data is in parts[1]
		if (parts.length > 0 && !parts[0].equals("")) {
			String[] hfhElems = parts[0].split("%%%HFH");
			for (int i = 0; i < hfhElems.length; i++) {
				//process each tuple of the hfh relation
				String[] tupleParts = hfhElems[i].split("###");
				if (tupleParts.length == 4) {
					int[] relElem = new int[3];
					relElem[0] = strToHNdx.get(tupleParts[1]);
					relElem[1] = strToFNdx.get(tupleParts[2]);
					relElem[2] = strToHNdx.get(tupleParts[3]);
					Pair<Integer, jq_Field> p = new Pair<Integer, jq_Field>(relElem[0], domF.get(relElem[1]));							
					BitSet pointsTo = seHFHMap.get(p);
					if (pointsTo == null) {
						pointsTo = new BitSet();
						seHFHMap.put(p, pointsTo);
					}
					pointsTo.set(relElem[2]);
				}
			}
		}
		if (parts.length == 2) {
			seFHMap = new HashMap<jq_Field, BitSet>();
			String[] fhElems = parts[1].split("%%%FH");
			for (int i = 0; i < fhElems.length; i++) {
				//process each tuple of the fh relation
				String[] tupleParts = fhElems[i].split("###");
				if (tupleParts.length == 3) {
					int[] relElem = new int[2];
					relElem[0] = strToFNdx.get(tupleParts[1]);
					relElem[1] = strToHNdx.get(tupleParts[2]);
					jq_Field f = domF.get(relElem[0]);
					BitSet pointsTo = seFHMap.get(f);
					if (pointsTo == null) {
						pointsTo = new BitSet();
						seFHMap.put(f, pointsTo);
					}
					pointsTo.set(relElem[1]);
				}
			}
		}
	}
	*****/
	
	public HeapCondition(String hpStr) {
		assert (fNdxToStrTrainApp != null);
		assert (hNdxToStrTrainApp != null);
		this.hpStr = hpStr;
		seHFHMap = new HashMap<Pair<Integer,jq_Field>, BitSet>();
		badValue = false;
		String[] parts = hpStr.split(":FHDAT:");
		// hfh data is in parts[0] and fh data is in parts[1]
		if (parts.length > 0 && !parts[0].equals("")) {
			String[] hfhElems = parts[0].split("%%%");
			for (int i = 0; i < hfhElems.length; i++) {
				//process tuples of the hfh relation with a given hf
				String[] tupleParts = hfhElems[i].split("###");
				if (tupleParts.length >= 4) {
					int h1idx = -1;
					int ndx = Integer.parseInt(tupleParts[1]);
					String s = null;
					if (ndx >= 0 && ndx < hNdxToStrTrainApp.size())
						s = hNdxToStrTrainApp.get(ndx);
					else {
						badValue = true;
						break;
					}
					if (strToHNdx.containsKey(s))
						h1idx = strToHNdx.get(s);
					else {
						badValue = true;
						break;
					}
					int fidx = -1;
					ndx = Integer.parseInt(tupleParts[2]);
					s = null;
					if (ndx >= 0 && ndx < fNdxToStrTrainApp.size())
						s = fNdxToStrTrainApp.get(ndx);
					else {
						badValue = true;
						break;
					}
					if (strToFNdx.containsKey(s))
						fidx = strToFNdx.get(s);
					else {
						badValue = true;
						break;
					}
					Pair<Integer, jq_Field> p = new Pair<Integer, jq_Field>(h1idx, domF.get(fidx));							
					BitSet pointsTo = seHFHMap.get(p);
					if (pointsTo == null) {
						pointsTo = new BitSet();
						seHFHMap.put(p, pointsTo);
					}
					for (int j = 0; j < tupleParts.length - 3; j++) {
						int h2idx = -1;
						ndx = Integer.parseInt(tupleParts[j+3]);
						s = null;
						if (ndx >= 0 && ndx < hNdxToStrTrainApp.size())
							s = hNdxToStrTrainApp.get(ndx);
						else {
							badValue = true;
							break;
						}
						if (strToHNdx.containsKey(s)) {
							h2idx = strToHNdx.get(s);
							pointsTo.set(h2idx);
						} else {
							badValue = true;
							break;
						}
					}
					if (badValue == true) break;
				} else {
					badValue = true;
					break;
				}
			}
		}
		if (parts.length == 2 && badValue == false && !parts[1].equals("")) {
			seFHMap = new HashMap<jq_Field, BitSet>();
			String[] fhElems = parts[1].split("%%%");
			for (int i = 0; i < fhElems.length; i++) {
				//process each tuple of the fh relation
				String[] tupleParts = fhElems[i].split("###");
				if (tupleParts.length >= 3) {
					int fidx = -1;
					int ndx = Integer.parseInt(tupleParts[1]);
					String s = null;
					if (ndx >= 0 && ndx < fNdxToStrTrainApp.size())
						s = fNdxToStrTrainApp.get(ndx);
					else {
						badValue = true;
						break;
					}
					if (strToFNdx.containsKey(s))
						fidx = strToFNdx.get(s);
					else {
						badValue = true;
						break;
					}
					jq_Field f = domF.get(fidx);
					BitSet pointsTo = seFHMap.get(f);
					if (pointsTo == null) {
						pointsTo = new BitSet();
						seFHMap.put(f, pointsTo);
					}
					for (int j = 0; j < tupleParts.length - 2; j++) {
						int hidx = -1;
						ndx = Integer.parseInt(tupleParts[j+2]);
						s = null;
						if (ndx >= 0 && ndx < hNdxToStrTrainApp.size())
							s = hNdxToStrTrainApp.get(ndx);
						else {
							badValue = true;
							break;
						}
						if (strToHNdx.containsKey(s)) {
							hidx = strToHNdx.get(s);
							pointsTo.set(hidx);
						} else {
							badValue = true;
							break;
						}
					}
					if (badValue == true) break;
				} else {
					badValue = true;
					break;
				}
			}
		}
	}
	
	public String toString() {
		return hpStr;
	}
	
	public static void init() {
		domF = (DomF) ClassicProject.g().getTrgt("F");
	}
	
	public boolean isBadValue() {
		return badValue;
	}
	
	public boolean validate() {
		for (Pair<Integer, jq_Field> pkey : seHFHMap.keySet()) {
			BitSet seBS = seHFHMap.get(pkey);
			BitSet appBS = HFHMap.get(pkey);
			if (appBS == null) return false;
			if (!appBS.equals(seBS)) return false;
		}
		for (jq_Field fkey : seFHMap.keySet()) {
			BitSet seBS = seFHMap.get(fkey);
			BitSet appBS = FHMap.get(fkey);
			if (appBS == null) return false;
			if (!appBS.equals(seBS)) return false;
		}
		return true;
	}
	
	public boolean equals (HeapCondition other) {
		return (this.seHFHMap.equals(other.seHFHMap) &&
		         this.seFHMap.equals(other.seFHMap));
	}
}
