package chord.analyses.composba;

import chord.project.analyses.provenance.Tuple;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;


public class HeapAccessData {
	public static RelTupleIndex hfhIndex;
	public static RelTupleIndex fhIndex;
	public TIntHashSet hfhNdx;
	public TIntHashSet fhNdx;
	public boolean appAccessPres;
	public boolean appCallbkPres;
	
	public HeapAccessData() {
		hfhNdx = new TIntHashSet();
		fhNdx = new TIntHashSet();
		appAccessPres = false;
		appCallbkPres = false;
	}

	public HeapAccessData(TIntHashSet hfhSet, TIntHashSet fhSet) {
		hfhNdx = new TIntHashSet(hfhSet);
		fhNdx = new TIntHashSet(fhSet);
		appAccessPres = false;
		appCallbkPres = false;
	}
		
	public void addToHFHNdx(int i) {
		hfhNdx.add(i);
	}
	
	public void addToFHNdx(int i) {
		fhNdx.add(i);
	}
	
	public boolean merge (HeapAccessData hp) {
		boolean modified = false;
		if (hp == null) return modified;
		modified = modified || fhNdx.addAll(hp.fhNdx);
		modified = modified || hfhNdx.addAll(hp.hfhNdx);
		if (hp.appAccessPres == true && this.appAccessPres == false) {
			this.appAccessPres = true;
			modified = true;
		}
		if (hp.appCallbkPres == true && this.appCallbkPres == false) {
			this.appCallbkPres = true;
			modified = true;
		}
		return modified;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String[] hfhElem = new String[hfhNdx.size()];
		String[] fhElem = new String[fhNdx.size()];

		sb.append(":HFHDAT:");
		int cnt = 0;
		for (TIntIterator iter = hfhNdx.iterator(); iter.hasNext(); ) {
			int x = iter.next();
			Tuple t = hfhIndex.getTuple(x);
			hfhElem[cnt++] = t.toSummaryString("###");
		}
		if (hfhElem.length > 0) sb.append("%%%");
		for (int i = 0; i < hfhElem.length; i++) {
			sb.append(hfhElem[i]);
			if (i < hfhElem.length - 1) sb.append("%%%");
		}
		
		sb.append(":FHDAT:");
		cnt = 0;
		for (TIntIterator iter = fhNdx.iterator(); iter.hasNext(); ) {
			int x = iter.next();
			Tuple t = fhIndex.getTuple(x);
			fhElem[cnt++] = t.toSummaryString("###");
		}
		if (fhElem.length > 0) sb.append("%%%");
		for (int i = 0; i < fhElem.length; i++) {
			sb.append(fhElem[i]);
			if (i < fhElem.length - 1) sb.append("%%%");
		}
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
		String[] hfhElem = new String[hfhNdx.size()];
		String[] fhElem = new String[fhNdx.size()];

		sb.append(":HFHDAT:");
		int cnt = 0;
		for (TIntIterator iter = hfhNdx.iterator(); iter.hasNext(); ) {
			int x = iter.next();
			Tuple t = hfhIndex.getTuple(x);
			hfhElem[cnt++] = t.toSummaryString("###");
		}
		if (hfhElem.length > 0) sb.append("%%%");
		for (int i = 0; i < hfhElem.length; i++) {
			sb.append(hfhElem[i]);
			if (i < hfhElem.length - 1) sb.append("%%%");
		}
		
		sb.append(":FHDAT:");
		cnt = 0;
		for (TIntIterator iter = fhNdx.iterator(); iter.hasNext(); ) {
			int x = iter.next();
			Tuple t = fhIndex.getTuple(x);
			fhElem[cnt++] = t.toSummaryString("###");
		}
		if (fhElem.length > 0) sb.append("%%%");
		for (int i = 0; i < fhElem.length; i++) {
			sb.append(fhElem[i]);
			if (i < fhElem.length - 1) sb.append("%%%");
		}
		
		return sb.toString();
	}
	
	public boolean equals (HeapAccessData other) {
		return ( this.appAccessPres == other.appAccessPres &&
		         this.appCallbkPres == other.appCallbkPres &&
		         this.fhNdx.equals(other.fhNdx)  &&
		         this.hfhNdx.equals(other.hfhNdx));
	}
}

