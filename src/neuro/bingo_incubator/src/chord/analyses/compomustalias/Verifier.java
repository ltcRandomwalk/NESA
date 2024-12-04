package chord.analyses.compomustalias;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.util.ArraySet;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;

public class Verifier {
	int diffMissingCnt = 0;
	int diffExtraCnt = 0;
	TObjectIntHashMap<jq_Method> missing;
	TObjectIntHashMap<jq_Method> extra;
	ArrayList<String> missingSummaries = new ArrayList<String>();
	ArrayList<String> extraSummaries = new ArrayList<String>();
	
	private String libraryPrefix = "(java\\.|javax\\.|sun\\.|sunw\\.|launcher\\.|com\\.sun\\.|com\\.ibm\\.|org\\.apache\\.harmony\\.|org\\.apache\\.commons\\.|org\\.apache\\.xpath\\.|org\\.apache\\.xml\\.|org\\.jaxen\\.|org\\.objectweb\\.|org\\.w3c\\.|org\\.xml\\.|org\\.ietf\\.|org\\.omg\\.|slib\\.).*";
	
	// summEdges0 is the reference and we want to validate summEdges1 against it
	public void compareSEMaps(Map<jq_Method, ArraySet<Edge>> summEdges0, Map<jq_Method, ArraySet<Edge>> summEdges1,
							  Map<jq_Method, ArraySet<MustAliasBUEdge>> buSummEdges0,
							  Map<jq_Method, ArraySet<MustAliasBUEdge>> buSummEdges1) {
		missing = new TObjectIntHashMap<jq_Method>();
		extra = new TObjectIntHashMap<jq_Method>();
		
		for (jq_Method m : summEdges0.keySet()) {
			jq_Class cl = m.getDeclaringClass();
			String clName = cl.toString();
			if (!clName.matches(libraryPrefix)) {
				Set<Edge> seSetFrm0 = summEdges0.get(m);
				Set<Edge> seSetFrm1 = null;
				if (summEdges1.containsKey(m)) {
					seSetFrm1 = summEdges1.get(m);
					if (seSetFrm0.equals(seSetFrm1) == false) {
						Set<MustAliasBUEdge> buseSetFrm0 = buSummEdges0.get(m);
						Set<MustAliasBUEdge> buseSetFrm1 = buSummEdges1.get(m);
						checkFurther(seSetFrm0, seSetFrm1, m, buseSetFrm0, buseSetFrm1);
					}
				} else {
					System.out.println("CompoMustAlias: summaries for method " + m + " are missing (" + seSetFrm0.size() + ")");
					diffMissingCnt += seSetFrm0.size();
					for (Edge se : seSetFrm0) missingSummaries.add(se.toString());
				}
			}
		}
		
		for (jq_Method m : summEdges1.keySet()) {
			jq_Class cl = m.getDeclaringClass();
			String clName = cl.toString();
			if (!clName.matches(libraryPrefix)) {
				Set<Edge> seSetFrm1 = summEdges1.get(m);
				if (!summEdges0.containsKey(m)) {
					System.out.println("CompoMustAlias: summaries for method " + m + " are extra (" + seSetFrm1.size() + ")");
					diffExtraCnt += seSetFrm1.size();
				}
			}
		}
		
		
		System.out.println("CompoMustAlias: DIFF_MISSING_CNT: " + diffMissingCnt + "  DIFF_EXTRA_CNT: " + diffExtraCnt);
		
		/****
		System.out.println ("######################################################################################");
		System.out.println ("MISSING");
		for (String s : missingSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		System.out.println ("EXTRA");
		for (String s : extraSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		****/
	}

	private void checkFurther(Set<Edge> seSet0, Set<Edge> seSet1, jq_Method m, 
			Set<MustAliasBUEdge> buseSet0, Set<MustAliasBUEdge> buseSet1) {
		Set<Edge> pres0abs1 = new HashSet<Edge>();
		Set<Edge> pres1abs0 = new HashSet<Edge>();
		pres0abs1.addAll(seSet0);
		pres0abs1.removeAll(seSet1);
		pres1abs0.addAll(seSet1);
		pres1abs0.removeAll(seSet0);
		
	    System.out.println("\nMethod: " + m + "  pres0abs1: " + pres0abs1.size() + "  pres1abs0: " + pres1abs0.size() 
	    		+ "  seSet0: "  + seSet0.size() + "  seSet1: " + seSet1.size());
	    
	    for (Edge se0 : pres0abs1) {
	    	if (se0.type == EdgeKind.NULL || se0.type == EdgeKind.ALLOC) {
	    		System.out.println("\tNo match: se from set0: missing NULL or ALLOC edge in SE1");
	    		System.out.println("\t"+se0);
    			diffMissingCnt++;
    			missingSummaries.add(se0.toString());
    			continue;
	    	}
	    	
	    	int match = 0;
	    	// CHK1 : Is there an se in pres1abs0 (i.e. in set1) which has the same src node as this?
	    	for (Edge se1 : pres1abs0) {
	    		if (!se0.equals(se1)) {
		    		if (se0.srcNode.equals(se1.srcNode)) {
		    			match++;
			    		if (!se0.dstNode.equals(se1.dstNode)) {
			    			System.out.println("\tNo match: se from set0: src node equal dst node different from an se in set1");
			    			diffMissingCnt++;
			    			missingSummaries.add(se0.toString());
			    			System.out.println ("\tSE0:" + se0);
                            System.out.println ("\tSE1:" + se1);
			    		} else
			    			assert false : "Unequal edges with same src node should have different dst node";
		    		} 
	    		} else 
	    			assert false : "Equal edges should not be present in pres0abs1 and pres1abs0";
	    	}
	    	if (match == 0) {
	    		// CHK1 fail: This se from seSet0 has no se in seSet1 with a matching src node
	    		boolean okay = false;
	    		// CHK2: check if there is some SE in seSet1 which subsumes the missing se0 from seSet0
	    		for (Edge se1 : seSet1) {
	    			if (se1.subsumes(se0)) {
	    				okay = true;
	    				break;
	    			}
	    		}
	    		if (!okay) {
	    			// CHK2 fail: no se in seSet1 subsumes this se from seSet0
	    			boolean okay1 = false;
	    			//CHK3: check if there is some BU SE in buseSet1 of which this missing se from seSet0 is an instance of
		    		ArraySet<AccessPath> se0MS = se0.dstNode.ms;
		    		for (MustAliasBUEdge buse : buseSet1) {
		    			if (((AliasConstraint)buse.getConstraint()).statisfy(se0MS)) {
		    				okay1 = true;
		    				break;
		    			}
		    		}
		    		if (!okay1) {
			    		System.out.println("\tNo match:  se from set0: No SE found in set1 with matching src node OR which subsumes OR with a matching bu se");
			    		diffMissingCnt++;
			    		missingSummaries.add(se0.toString());
			    		System.out.println ("\tSE0:" + se0);
		    		} else {
		    			System.out.println("\tMatch: se from set0: Found a bu se in set1, of which this is an instance.");
		    		}
	    		} else {
	    			System.out.println("\tMatch: se from set0: Found an se in set1 which subsumes this.");
	    		}
	    	}
	    }
	    
	    for (Edge se1 : pres1abs0) {
	    	if (se1.type == EdgeKind.NULL || se1.type == EdgeKind.ALLOC) {
	    		System.out.println("\tNo match: se from set1: additional NULL or ALLOC edge in SE1");
	    		System.out.println("\t"+se1);
    			diffExtraCnt++;
    			extraSummaries.add(se1.toString());
    			continue;
	    	}
	    	
	    	int match = 0;
	    	// CHK1 : Is there an se in pres0abs1 (i.e. in set0) which has the same src node as this?
	    	for (Edge se0 : pres0abs1) {
	    		if (!se1.equals(se0)) {
		    		if (se1.srcNode.equals(se0.srcNode)) {
		    			match++;
			    		if (!se1.dstNode.equals(se0.dstNode)) {
			    			System.out.println("\tNo match: se from set1: src node equal dst node different from an se in set0");
			    			diffExtraCnt++;
			    			extraSummaries.add(se1.toString());
			    			System.out.println ("\tSE1:" + se1);
			    			System.out.println ("\tSE0:" + se0);
			    		} else
			    			assert false : "Unequal edges with same src node should have different dst node";
		    		} 
	    		} else
	    			assert false : "Equal edges should not be present in pres0abs1 and pres1abs0";
	    	}
	    	if (match == 0) {
	    		// CHK1 fail: This se from seSet1 has no se in seSet0 with a matching src node
	    		boolean okay = false;
	    		// CHK2: check if there is some SE in seSet1 which subsumes the extra se1 from seSet1
	    		for (Edge se11 : seSet1) {
	    			if (!se11.equals(se1) && se11.subsumes(se1)) {
	    				okay = true;
	    				break;
	    			}
	    		}
	    		if (!okay) {
	    			// CHK2 fail: no se in seSet1 subsumes this se from seSet1
	    			boolean okay1 = false;
	    			//CHK3: check if there is some BU SE in buseSet0 of which this extra se from seSet1 is an instance of
		    		ArraySet<AccessPath> se1MS = se1.dstNode.ms;
		    		for (MustAliasBUEdge buse : buseSet0) {
		    			if (((AliasConstraint)buse.getConstraint()).statisfy(se1MS)) {
		    				okay1 = true;
		    				break;
		    			}
		    		}
		    		if (!okay1) {
			    		System.out.println("\tNo match:  se from set1: No SE found in set0 with matching src node OR an se in set1 which subsumes OR with a matching bu se from buseSet0");
			    		diffExtraCnt++;
			    		extraSummaries.add(se1.toString());
			    		System.out.println ("\tSE1:" + se1);
		    		} else {
		    			System.out.println("\tMatch: se from set1: Found a bu se in set0, of which this is an instance.");
		    		}
	    		} else {
	    			System.out.println("\tMatch: se from set1: Found an se in set1 which subsumes this.");
	    		}
	    	}
	    }
	    /****
	    if (pres0abs1.size() > 0) {
	    	System.out.println("==============================MISSING=====================================");
	    	System.out.println("FOR METHOD: " + m);
	    	for (Edge se : seSet0) System.out.println(se.toString());
	    	System.out.println("--------------------------------------------------------------------------");
	    	for (Edge se : seSet1) System.out.println(se.toString());
	    	System.out.println("==========================================================================");
	    }
	    
	    if (pres1abs0.size() > 0) {
	    	System.out.println("===============================EXTRA======================================");
	    	System.out.println("FOR METHOD: " + m);
	    	for (Edge se : seSet0) System.out.println(se.toString());
	    	System.out.println("--------------------------------------------------------------------------");
	    	for (Edge se : seSet1) System.out.println(se.toString());
	    	System.out.println("==========================================================================");
	    }
	    ****/
	}
	
	public void dumpSummaryEdges(Map<jq_Method, Set<Edge>> summEdges) {
		for (jq_Method m : summEdges.keySet()) {
			Set<Edge> seSet = summEdges.get(m);
			System.out.println("Method: " + m.toString());
			for (Edge se : seSet)
				System.out.println(se.toString());
		}
	}
	
	public void dumpSavedSummaryEdges(Map<jq_Method, ArraySet<MustAliasBUEdge>> summEdges) {
		for (jq_Method m : summEdges.keySet()) {
			Set<MustAliasBUEdge> seSet = summEdges.get(m);
			System.out.println("Method: " + m.toString());
			System.out.println("Saved summary for method: " + m.toString());
			for (MustAliasBUEdge se : seSet)
				System.out.println(se.toString());
		}
	}
	
	// summEdges0 is the reference and we want to validate summEdges1 against it
	public void compareSEMapsBU(Map<jq_Method, ArraySet<MustAliasBUEdge>> summEdges0, Map<jq_Method, ArraySet<MustAliasBUEdge>> summEdges1) {
		for (jq_Method m : summEdges0.keySet()) {
			Set<MustAliasBUEdge> seSetFrm0 = summEdges0.get(m);
			Set<MustAliasBUEdge> seSetFrm1 = null;
			if (summEdges1.containsKey(m)) {
				seSetFrm1 = summEdges1.get(m);
				if (seSetFrm0.equals(seSetFrm1) == false) {
					checkFurtherBU(seSetFrm0, seSetFrm1, m);
				}
			} else {
				System.out.println("CompoMustAlias: summaries for method " + m + " are missing (" + seSetFrm0.size() + ")");
				diffMissingCnt += seSetFrm0.size();
				for (MustAliasBUEdge se : seSetFrm0) missingSummaries.add(se.toString());
			}
		}
		
		for (jq_Method m : summEdges1.keySet()) {
			Set<MustAliasBUEdge> seSetFrm1 = summEdges1.get(m);
			if (!summEdges0.containsKey(m)) {
				System.out.println("CompoMustAlias: summaries for method " + m + " are extra (" + seSetFrm1.size() + ")");
				diffExtraCnt += seSetFrm1.size();
			}
		}
		
		
		System.out.println("CompoMustAlias: DIFF_MISSING_CNT: " + diffMissingCnt + "  DIFF_EXTRA_CNT: " + diffExtraCnt);
		
		/****
		System.out.println ("######################################################################################");
		System.out.println ("MISSING");
		for (String s : missingSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		System.out.println ("EXTRA");
		for (String s : extraSummaries) System.out.println(s);
		System.out.println ("######################################################################################");
		****/
	}
	
	private void checkFurtherBU(Set<MustAliasBUEdge> seSet0, Set<MustAliasBUEdge> seSet1, jq_Method m) {
		Set<MustAliasBUEdge> pres0abs1 = new HashSet<MustAliasBUEdge>();
		Set<MustAliasBUEdge> pres1abs0 = new HashSet<MustAliasBUEdge>();
		pres0abs1.addAll(seSet0);
		pres0abs1.removeAll(seSet1);
		pres1abs0.addAll(seSet1);
		pres1abs0.removeAll(seSet0);
		
	    System.out.println("\nMethod: " + m + "  pres0abs1: " + pres0abs1.size() + "  pres1abs0: " + pres1abs0.size() 
	    		+ "  seSet0: "  + seSet0.size() + "  seSet1: " + seSet1.size());
	}
}
