package chord.analyses.composba;

import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;
import chord.analyses.field.DomF;
import chord.analyses.var.DomV;
import chord.project.ClassicProject;
import chord.util.tuple.object.Pair;


public class HeapAccessDataBkwd {
	public static HashMap<String, int[]> mark;
	public static Map<jq_Field, BitSet> FHMap;
	public static Map<Pair<Integer, jq_Field>, BitSet> HFHMap;
	public static ArrayList<Pair<Integer, jq_Field>> intToHF;
	public static DomF domF;
	public static String sep1;
	public static String sep2;
	public BitSet hfSet;
	public BitSet fSet;
	public TIntHashSet vSet;
	public BitSet hfSetC;
	public BitSet fSetC;
	public TIntHashSet vSetC;
	public boolean appAccessPres;
	public boolean appCallbkPres;
	
	public HeapAccessDataBkwd() {
		hfSet = new BitSet();
		fSet = new BitSet();
		vSet = new TIntHashSet();
		hfSetC = new BitSet();
		fSetC = new BitSet();
		vSetC = new TIntHashSet();
		appAccessPres = false;
		appCallbkPres = false;
	}

	public HeapAccessDataBkwd(HeapAccessDataBkwd cp) {
		hfSet = (BitSet) cp.hfSet.clone();
		fSet = (BitSet) cp.fSet.clone();
		vSet = new TIntHashSet();
		vSet.addAll(cp.vSet);
		hfSetC = (BitSet) cp.hfSetC.clone();
		fSetC = (BitSet) cp.fSetC.clone();
		vSetC = new TIntHashSet();
		vSetC.addAll(cp.vSetC);
		appAccessPres = cp.appAccessPres;
		appCallbkPres = cp.appCallbkPres;
	}
	
	public static void init() {
		domF = (DomF) ClassicProject.g().getTrgt("F");
        sep1 = "###";
        sep2 = "%%%";
	}
	
	public void addToHFSet(int i) {
		hfSet.set(i);
	}
	
	public void addToFSet(int i) {
		fSet.set(i);
	}
	
	public void addToVSet(int i) {
		vSet.add(i);
	}
	
	public void addToHFSetC(int i) {
		hfSetC.set(i);
	}
	
	public void addToFSetC(int i) {
		fSetC.set(i);
	}
	
	public void addToVSetC(int i) {
		vSetC.add(i);
	}
	
	public void merge (HeapAccessDataBkwd hp) {
		if (hp == null) return;
		
		fSet.or(hp.fSet);
		hfSet.or(hp.hfSet);
		vSet.addAll(hp.vSet);
		
		fSetC.or(hp.fSetC);
		hfSetC.or(hp.hfSetC);
		vSetC.addAll(hp.vSetC);
		
		if (hp.appCallbkPres == true && this.appCallbkPres == false) {
			this.appCallbkPres = true;
		}
		return;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(":VDAT:");
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		for (int v : vSet.toArray()) {
			sb.append(domV.get(v).toString() + ",");
		}
		sb.append(toParsableString());
		if (appAccessPres) 
			sb.append("appAccessPres=true");
		else
			sb.append("appAccessPres=false");
		if (appCallbkPres) 
			sb.append("appCallbkPres=true");
		else
			sb.append("appCallbkPres=false");
		return sb.toString();
	}
	
	
	public String toParsableString() {
		StringBuilder sb = new StringBuilder();
		sb.append(":HFHDAT:");
		if (!hfSet.isEmpty()) {
			for (int hf = hfSet.nextSetBit(0); hf >= 0; hf = hfSet.nextSetBit(hf+1)) {
				Pair<Integer, jq_Field> pr = intToHF.get(hf);
				BitSet bs = HFHMap.get(pr);
				if (!bs.isEmpty()) {
					int fidx = domF.indexOf(pr.val1);
					sb.append("HFH");
		            sb.append(sep1);
		            //sb.append(hNdxToStr.get(pr.val0.intValue()));
		            sb.append(pr.val0.intValue());
		            sb.append(sep1);
		            //sb.append(fNdxToStr.get(fidx)); 
		            sb.append(fidx); 
		            sb.append(sep1);
					for (int h = bs.nextSetBit(0); h >= 0; h = bs.nextSetBit(h+1)) {
		                //sb.append(hNdxToStr.get(h));
		                sb.append(h);
		                sb.append(sep1);
						if (!hfhBelongsToLib(pr.val0.intValue(), fidx, h)) appAccessPres = true;
					}
					sb.append(sep2);
				}
			}
		}
		sb.append(":FHDAT:");
		for (int f = fSet.nextSetBit(0); f >= 0; f = fSet.nextSetBit(f+1) ) {
			jq_Field fld = domF.get(f);
			BitSet bs = FHMap.get(fld);
			if (bs != null) {
				sb.append("FH");
                sb.append(sep1);
                //sb.append(fNdxToStr.get(f));
                sb.append(f);
                sb.append(sep1);
				for (int h = bs.nextSetBit(0); h >= 0; h = bs.nextSetBit(h+1)) {
	                //sb.append(hNdxToStr.get(h));
	                sb.append(h);
	                sb.append(sep1);
					if (!fhBelongsToLib(f, h)) appAccessPres = true;
				}
				sb.append(sep2);
			}
		}
		return sb.toString();
	}
	
	public boolean equals (HeapAccessDataBkwd other) {
		return ( this.appAccessPres == other.appAccessPres &&
		         this.appCallbkPres == other.appCallbkPres &&
		         this.vSet.equals(other.vSet) &&
		         this.fSet.equals(other.fSet)  &&
		         this.hfSet.equals(other.hfSet) &&
		         this.vSetC.equals(other.vSetC) &&
		         this.fSetC.equals(other.fSetC)  &&
		         this.hfSetC.equals(other.hfSetC)
		      );
	}
	
	 private boolean hfhBelongsToLib(int idx1, int idx2, int idx3) {
         int type = 0;
         type |= ((int[])mark.get("H"))[idx1];
         type |= ((int[])mark.get("F"))[idx2];
         type |= ((int[])mark.get("H"))[idx3];
         if (type <= 0) return false;         // Unknown
         else if (type <= 1) return false;    // Don't care
         else if (type <= 3) return true;     // Library
         else if (type <= 5) return false;    // Application
         else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently
                                              // marking as app.
         return false;
	 }
	 
	 private boolean fhBelongsToLib(int idx1, int idx2) {
         int type = 0;
         type |= ((int[])mark.get("F"))[idx1];
         type |= ((int[])mark.get("H"))[idx2];
         if (type <= 0) return false;         // Unknown
         else if (type <= 1) return false;    // Don't care
         else if (type <= 3) return true;     // Library
         else if (type <= 5) return false;    // Application
         else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently
                                              // marking as app.
         return false;
	 }
}