package chord.analyses.composba;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.analyses.compomustalias.Edge;
import chord.util.ArraySet;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

public class Verifier {
	int diffMissingCnt = 0;
	int diffExtraCnt = 0;
	TObjectIntHashMap<jq_Method> missing;
	TObjectIntHashMap<jq_Method> extra;
	ArrayList<String> missingSummaries = new ArrayList<String>();
	ArrayList<String> extraSummaries = new ArrayList<String>();
	boolean useLibPrefix;
	
	private String libraryPrefix = "(java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
	private String appPrefix = "boofcv\\.examples\\..*";
	
	public Verifier(boolean useLibPrefix) {
		this.useLibPrefix = useLibPrefix;
	}
	
	// summEdges0 is the reference and we want to validate summEdges1 against it
	public void compareSEMaps(Map<jq_Method, ArraySet<BitEdge<Quad>>> summEdges0, Map<jq_Method, ArraySet<BitEdge<Quad>>> summEdges1) {
		missing = new TObjectIntHashMap<jq_Method>();
		extra = new TObjectIntHashMap<jq_Method>();
		
		for (jq_Method m : summEdges0.keySet()) {
			jq_Class cl = m.getDeclaringClass();
			String clName = cl.toString();
			boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
			if (!isLib) {
				ArraySet<BitEdge<Quad>> seSetFrm0 = summEdges0.get(m);
				ArraySet<BitEdge<Quad>> seSetFrm1 = null;
				if (summEdges1.containsKey(m)) {
					seSetFrm1 = summEdges1.get(m);
					if (seSetFrm0.equals(seSetFrm1) == false) {
						checkFurther(seSetFrm0, seSetFrm1, m);
					}
				} else {
					System.out.println("CompoSBA: summaries for method " + m + " are missing (" + seSetFrm0.size() + ")");
					diffMissingCnt += seSetFrm0.size();
					for (BitEdge<Quad> se : seSetFrm0) missingSummaries.add(se.toString());
				}
			}
		}
		
		for (jq_Method m : summEdges1.keySet()) {
			jq_Class cl = m.getDeclaringClass();
			String clName = cl.toString();
			boolean isLib = useLibPrefix ? clName.matches(libraryPrefix) : !clName.matches(appPrefix);
			if (!isLib) {
				Set<BitEdge<Quad>> seSetFrm1 = summEdges1.get(m);
				if (!summEdges0.containsKey(m)) {
					System.out.println("CompoSBA: summaries for method " + m + " are extra (" + seSetFrm1.size() + ")");
					diffExtraCnt += seSetFrm1.size();
					for (BitEdge<Quad> se : seSetFrm1) extraSummaries.add(se.toString());
				}
			}
		}
		
		System.out.println("CompoSBA: DIFF_MISSING_CNT: " + diffMissingCnt + "  DIFF_EXTRA_CNT: " + diffExtraCnt);
		
		/*****
		System.out.println ("######################################################################################");
		System.out.println ("MISSING");
		for (String s : missingSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		System.out.println ("EXTRA");
		for (String s : extraSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		*****/
	}

	private void checkFurther(ArraySet<BitEdge<Quad>> seSet0, ArraySet<BitEdge<Quad>> seSet1, jq_Method m) {
		Set<BitEdge<Quad>> pres0abs1 = new HashSet<BitEdge<Quad>>();
		Set<BitEdge<Quad>> pres1abs0 = new HashSet<BitEdge<Quad>>();
		pres0abs1.addAll(seSet0);
		pres0abs1.removeAll(seSet1);
		pres1abs0.addAll(seSet1);
		pres1abs0.removeAll(seSet0);
		
		boolean gOkay = true;
	    System.out.println("\nMethod: " + m + "  pres0abs1: " + pres0abs1.size() + "  pres1abs0: " + pres1abs0.size() 
	    		+ "  seSet0: "  + seSet0.size() + "  seSet1: " + seSet1.size());
	    for (BitEdge<Quad> se0 : pres0abs1) {
	    	int match = 0;
	    	for (BitEdge<Quad> se1 : pres1abs0) {
	    		if (!se0.equals(se1)) {
		    		if (se0.srcNode.equals(se1.srcNode)) {
		    			match++;
			    		if (!se0.dstNode.equals(se1.dstNode)) {
			    			System.out.println("\t CompoSBA: se from set0: src node equal dst node different from an se in set1");
			    			diffMissingCnt++;
			    			missingSummaries.add(se0.toString());
			    			gOkay = false;
			    		} else
			    			assert false : "Unequal edges with same src node should have different dst node";
		    		} 
	    		} else 
	    			assert false : "Equal edges should not be present in pres0abs1 and pres1abs0";
	    	}
	    	if (match == 0) {
	    		// if some SE in seSet1 subsumes the missing se0, its ok.
	    		boolean okay = false;

	    		for (BitEdge<Quad> se1 : seSet1) {
	    			if (se1.subsumes(se0)) {
	    				okay = true;
	    				break;
	    			}
	    		}

	    		if (!okay) {
		    		System.out.println("\t CompoSBA: se from set0: No comparable SE found in set1");
		    		diffMissingCnt++;
		    		missingSummaries.add(se0.toString());
		    		gOkay = false;
	    		}
	    	}
	    }
	    
	    for (BitEdge<Quad> se1 : pres1abs0) {
	    	int match = 0;
	    	for (BitEdge<Quad> se0 : pres0abs1) {
	    		if (!se1.equals(se0)) {
		    		if (se1.srcNode.equals(se0.srcNode)) {
		    			match++;
			    		if (!se1.dstNode.equals(se0.dstNode)) {
			    			System.out.println("\t CompoSBA: se from set1: src node equal dst node different from an se in set0");
			    			diffExtraCnt++;
			    			extraSummaries.add(se1.toString());
			    			gOkay = false;
			    		} else 
			    			assert false : "Unequal edges with same src node should have different dst node";
		    		} 
	    		} else
	    			assert false : "Equal edges should not be present in pres0abs1 and pres1abs0";
	    	}
	    	if (match == 0) {
	    		// if some SE in seSet1 subsumes the extra se1, its ok.
	    		boolean okay = false;

	    		for (BitEdge<Quad> se11 : seSet1) {
	    			if (!se11.equals(se1) && se11.subsumes(se1)) {
	    				okay = true;
	    				break;
	    			}
	    		}
	    		
	    		if (!okay) {
		    		System.out.println("\t CompoSBA: se from set1: No comparable SE found in set0");
		    		diffExtraCnt++;
		    		extraSummaries.add(se1.toString());
		    		gOkay = false;
	    		}
	    	}
	    }
	    
/*	    if (!gOkay) {
	    	System.out.println("\t CompoSBA: all se from set0");
	    	for (BitEdge<Quad> se0 : seSet0) {
	    		System.out.println(se0);
	    	}

	    	System.out.println("\t CompoSBA: all se from set1");
	    	for (BitEdge<Quad> se1 : seSet1) {
	    		System.out.println(se1);
	    	}
	    }
*/
	}
	
	private void printHashCode(ArraySet<BitEdge<Quad>> seSet0, ArraySet<BitEdge<Quad>> seSet1) {
		for (BitEdge<Quad> e : seSet0) {
			System.out.println("First SE: ");
			System.out.println("\tedge: " + e.hashCode());
			System.out.println("\t\tedge - src node: " + e.srcNode.hashCode());
			System.out.println("\t\t\tedge - src node - lclenv: " + e.srcNode.envLocal.hashCode());
			System.out.println("\t\tedge - dst node: " + e.dstNode.hashCode());
			System.out.println("\t\t\tedge - dst node - lclenv: " + e.dstNode.envLocal.hashCode());
		}
		for (BitEdge<Quad> e : seSet1) {
			System.out.println("Second SE: ");
			System.out.println("\tedge: " + e.hashCode());
			System.out.println("\t\tedge - src node: " + e.srcNode.hashCode());
			System.out.println("\t\t\tedge - src node - lclenv: " + e.srcNode.envLocal.hashCode());
			System.out.println("\t\tedge - dst node: " + e.dstNode.hashCode());
			System.out.println("\t\t\tedge - dst node - lclenv: " + e.dstNode.envLocal.hashCode());
		}
	}
}
