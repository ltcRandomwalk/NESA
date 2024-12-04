package chord.analyses.bigcode.callgraph;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;


public class VerifierCHA {
	
	public void verify(CHAAnalysis analysis0, CHAAnalysis analysis1) {
		if (analysis0.classes.equals(analysis1.classes)) {
			System.out.println("CompoCHA: Reachable classes match");
		} else {
			System.out.println("CompoCHA: Reachable classes do not match");
			Set<jq_Reference> c0 = new HashSet<jq_Reference>(analysis0.classes);
			c0.removeAll(analysis1.classes);
			for (jq_Reference c : c0) {
				System.out.println(c);
			}
		}
		
		if (analysis0.methods.equals(analysis1.methods)) {
			System.out.println("CompoCHA: Reachable methods match");
		} else {
			System.out.println("CompoCHA: Reachable methods do not match");
			Set<jq_Method> c0 = new HashSet<jq_Method>(analysis0.methods);
			c0.removeAll(analysis1.methods);
			for (jq_Method c : c0) {
				System.out.println(c);
			}
		}
		
		if (analysis0.callGraph.equals(analysis1.callGraph)) {
			System.out.println("CompoCHA: Callgraphs match");
		} else {
			System.out.println("CompoCHA: Callgraphs do not match");
		}

	}
}
