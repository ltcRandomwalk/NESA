package chord.analyses.prunerefine.klimited;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import chord.analyses.alias.Ctxt;
import chord.analyses.prunerefine.AbstractionSet;
import chord.analyses.prunerefine.Histogram;

import static chord.analyses.prunerefine.klimited.GlobalInfo.G;

//An abstraction actually stores the set of contexts (abstract values), and through this the k value.
//TypeStrategy specifies the abstraction function (minus the k value).
class KlimitedAbstractionSet extends AbstractionSet<Ctxt> {
	
	Histogram lengthHistogram(Set<Ctxt> contexts) {
		Histogram hist = new Histogram();
		for (Ctxt c : contexts) hist.add(G.len(c));
		return hist;
	}

	// Extract the sites which are not pruned
	Set<Quad> inducedHeadSites() {
		Set<Quad> set = new HashSet<Quad>();
		for (Ctxt c : S)
			if (G.hasHeadSite(c))
				set.add(c.head());
		return set;
	}

	void printAbsLevelsCount() {

	}

	boolean lastElementRepeated(Ctxt c) {
		assert G.isSummary(c);
		int len = G.summaryLen(c);
		if (len <= 1) return false;
		Quad j = c.get(len-1); // Last element
		for (int k = len-2; k >= 0; k--)
			if (c.get(k) == j) return true; // Found another copy
		return false;
	}

	// assertDisjoint: this is when we are pruning slivers (make sure never step on each other's toes)
	// This should really not have typeStrategy in here (more general than useful)
	// REFINE
	void addRefinements(Ctxt c, int depth, TypeStrategy typeStrategy) {
		assert depth >= 0;
		if (S.contains(c)) return;
		if (depth == 0) // No refinement
			S.add(c);
		else if (typeStrategy.disallowRepeats && lastElementRepeated(c))
			S.add(c);
		else {
			assert G.isSummary(c); // Can only refine summarizes
			Ctxt d = G.atomize(c);
			Collection<Quad> extensions;
			if (G.summaryLen(c) == 0) // [*] = {[i,*] : i in I} by definition
				extensions = G.iSet;
			else { // c = [... k *]; consider all possible ways k can be extended
				List<Quad> ks = typeStrategy.lift(d.last()); // Actually with types, might be several values of k (need to consider them all)
				assert ks.size() > 0;
				if (ks.size() == 1) // Just for efficiency, don't create a new array
					extensions = G.rev_jm.get(ks.get(0).getMethod());
				else {
					extensions = new ArrayList();
					for (Quad k : ks) extensions.addAll(G.rev_jm.get(k.getMethod()));
				}
			}

			extensions = typeStrategy.usePrototypes(extensions); // Apply coarsening

			addRefinements(d, 0, typeStrategy);
			for (Quad j : extensions)
				addRefinements(G.summarize(d.append(j)), depth-1, typeStrategy);
		}
	}

	// c could either be a summary or atom, which means the output could technically be a set, but make sure this doesn't happen.
	Ctxt project(Ctxt c, TypeStrategy typeStrategy) {
		if (typeStrategy.disallowRepeats) {
			// EXTEND
			// ASSUMPTION: without the first element, c is barely-repeating, so we just need to check the first element
			// First truncate to eliminate repeats if any
			int k = 0;
			int len = G.len(c);
			for (k = 1; k < len; k++) // Find k, second occurrence of first element (if any)
				if (c.get(0) == c.get(k)) break;
			if (k < len) // Truncate (include position k)
				c = G.summarize(c.prefix(k+1));
		}

		if (G.isAtom(c)) { // atom
			if (S.contains(c)) return c; // Exact match
			// Assume there's at most one that matches
			for (int k = G.atomLen(c); k >= 0; k--) { // Take length k prefix
				Ctxt d = G.summarize(c.prefix(k));
				if (S.contains(d)) return d;
			}
			return null;
		}
		else { // summary
			// If we project ab* (by prepending a to b*) onto S={ab,abc*,abd*,...}, we should return all 3 values.
			// Generally, take every sliver that starts with ab, summary or not.
			// TODO: we don't handle this case, which is okay if all the longest summary slivers differ by length at most one.
			{ // Match ab?
				Ctxt d = G.atomize(c);
				if (S.contains(d)) return d;
			}
			for (int k = G.summaryLen(c); k >= 0; k--) { // Take length k prefix (consider {ab*, a*, *}, exactly one should match)
				Ctxt d = G.summarize(c.prefix(k));
				if (S.contains(d)) return d;
			}
			return null;
		}
	}

	// Need this assumption if we're going to prune!
	private void assertNotExists(Ctxt c, Ctxt cc) {
		assert !S.contains(cc) : G.cstr(c) + " exists, but subsumed by coarser " + G.cstr(cc);
	}

	void assertDisjoint() {
		// Make sure each summary sliver doesn't contain proper summary prefixes
		for (Ctxt c : S) {
			if (G.hasHeadSite(c) && !G.isAlloc(c.head())) // if [i...] exists, [*] cannot exist
				assertNotExists(c, G.summarize(G.emptyCtxt));

			if (G.isAtom(c))
				assertNotExists(c, G.summarize(c));
			else {
				for (int k = G.summaryLen(c)-1; k >= 1; k--) // if xy* exists, x* can't exist
					assertNotExists(c, G.summarize(c.prefix(k)));
			}
		}
	}

	@Override public String toString() {
		int numSummaries = 0;
		for (Ctxt c : S) if (G.isSummary(c)) numSummaries++;
		return String.format("%s(%s)%s", S.size(), numSummaries, lengthHistogram(S));
	}

	@Override
	public List<Ctxt> getSortedAbstractions() {

		List<Ctxt> sortedAbs = new ArrayList<Ctxt>(S);
		Collections.sort(sortedAbs, new Comparator<Ctxt>() {
			@Override
			public int compare(Ctxt c1, Ctxt c2) {
				Quad[] elems1 = c1.getElems();
				Quad[] elems2 = c2.getElems();
				if (elems1.length == 0)
					return elems2.length == 0 ? 0 : -1;
				if (elems2.length == 0)
					return 1;
				return compare(elems1, elems2, 0);
			}
			private int compare(Quad[] elems1, Quad[] elems2, int i) {
				if (i == elems1.length) return (i == elems2.length) ? 0 : -1;
				if (i == elems2.length) return 1;
				Quad q1 = elems1[i];
				Quad q2 = elems2[i];
				if (q1 == q2)
					return compare(elems1, elems2, i + 1);
				if (q1 == null) return -1;
				if (q2 == null) return 1;
				Operator op1 = q1.getOperator();
				Operator op2 = q2.getOperator();
				if (op1 instanceof Invoke) {
					if (op2 instanceof Invoke) {
						int i1 = G.domI.indexOf(q1);
						int i2 = G.domI.indexOf(q2);
						assert (i1 >= 0 && i2 >= 0);
						return i1 < i2 ? -1 : 1;
					} else
						return -1;
				} else {
					if (op2 instanceof Invoke)
						return 1;
					else {
						int h1 = G.domH.indexOf(q1);
						int h2 = G.domH.indexOf(q2);
						assert (h1 >= 0 && h2 >= 0);
						return h1 < h2 ? -1 : 1;
					}
				}
			}
		});
		return sortedAbs;
	}

	@Override
	public String printStatus() {
		StringBuilder buf = new StringBuilder();
	    
		int[] h_maxLen = new int[G.domH.size()];
		int[] i_maxLen = new int[G.domI.size()];
		for (Ctxt c : S) {
			if (!G.hasHeadSite(c)) continue;
			int len = G.len(c);
			if (G.isAlloc(c.head())) {
				int h = G.domH.indexOf(c.head());
				h_maxLen[h] = Math.max(h_maxLen[h], len);
			}
			else {
				int i = G.domI.indexOf(c.head());
				i_maxLen[i] = Math.max(i_maxLen[i], len);
			}
		}

		int size = 0;
		for (int k = 0; k < 4; k++) {
			int n = 0;
			for (int h = 0; h < G.domH.size(); h++) {
				if (!G.jSet.contains(G.domH.get(h))) continue;
				if (h_maxLen[h] == k) n++; 
			}
			buf.append("KVALUE H " + k + " " + n);
			size += k*n;
			n = 0;
			for (int i = 0; i < G.domI.size(); i++) {
				if (!G.jSet.contains(G.domI.get(i))) continue;
				if (i_maxLen[i] == k) n++; 
			}
			buf.append("KVALUE I " + k + " " + n);
			size += k*n;
		}
		buf.append("KVALUE SIZE " + size);
		return buf.toString();
	}

	@Override
	public String printAbstraction(Ctxt abs) {
		return G.cstr(abs);
	}
	
}