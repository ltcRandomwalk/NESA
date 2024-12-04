package chord.analyses.composba;

import java.util.HashSet;

public class HeapDataString {
	public HashSet<String> hfhSet;
	public HashSet<String> fhSet;

	
	public HeapDataString(String str) {
		hfhSet = new HashSet<String>();
		fhSet = new HashSet<String>();
		
		String[] parts = str.split(":FHDAT:");
		// hfh data is in parts[0] and fh data is in parts[1]
		if (parts.length > 0 && !parts[0].equals("")) {
			String[] hfhElems = parts[0].split("%%%HFH");
			for (int i = 0; i < hfhElems.length; i++) {
				hfhSet.add(hfhElems[i]);
			}
		}
		if (parts.length == 2) {
			String[] fhElems = parts[1].split("%%%FH");
			for (int i = 0; i < fhElems.length; i++) {
				fhSet.add(fhElems[i]);
			}
		}
	}

	public boolean equals (HeapDataString other) {
		return ( this.fhSet.equals(other.fhSet)  &&
		         this.hfhSet.equals(other.hfhSet));
	}
	
	public int hashCode() {
		return hfhSet.hashCode() + fhSet.hashCode();
	}
}

