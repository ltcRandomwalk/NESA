/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.analyses.bigcode.connection;

import java.util.HashSet;
import java.util.Set;

import chord.project.analyses.rhs.IEdge;

public class EdgeNullness implements IEdge 
{
	public SrcNodeNullness srcNode;
	// DstNodeNullness is intentionally not final: it is updated when this edge
	// is merged with another edge with matching SrcNodeNullness; see mergeWith
	public DstNodeNullness dstNode;
	public EdgeNullness(SrcNodeNullness s, DstNodeNullness d) {
		srcNode = new SrcNodeNullness(s);
		dstNode = new DstNodeNullness(d);
	}
	public EdgeNullness(EdgeNullness pe) {
		srcNode = new SrcNodeNullness(pe.srcNode);
		dstNode = new DstNodeNullness(pe.dstNode);
	}
	
	@Override
	public int canMerge(IEdge pe2, boolean canMerge) {
		if (this.srcNode.equals(((EdgeNullness) pe2).srcNode)){
			if (this.dstNode.equals(((EdgeNullness) pe2).dstNode)) return 0;
			else return 3;
		}
		return -1;
	}
	
	@Override
	public boolean mergeWith(IEdge pe2) {
		if (this.equals(pe2)) return false;
		
		DstNodeNullness DstNodeNullness1 = this.dstNode;
		DstNodeNullness DstNodeNullness2 = ((EdgeNullness) pe2).dstNode;
		boolean isRetn1 = DstNodeNullness1.isRetn;
		boolean isRetn2 = DstNodeNullness2.isRetn;
		assert (isRetn1 == isRetn2);
		
		Set<Integer> retIdxs1 = DstNodeNullness1.retIdxs;
		Set<Integer> retIdxs2 = DstNodeNullness2.retIdxs;
		assert(retIdxs1 == null || isRetn1);
		assert(retIdxs2 == null || isRetn2);
		
		VariablesPartitionNullness oldVP = DstNodeNullness1.partition;
		VariablesPartitionNullness newVP = (VariablesPartitionNullness) oldVP.merge(DstNodeNullness2.partition);
		boolean changed = (oldVP != newVP);

		if (retIdxs1 != null) {
			if (retIdxs2 != null) {
				changed = retIdxs1.addAll(retIdxs2) || changed;
			}
		} else if (retIdxs2 != null) {
			retIdxs1 = new HashSet<Integer>(retIdxs2);
			changed = true;
		}
		if (changed) {
			this.srcNode = new SrcNodeNullness(srcNode.partition);
			this.dstNode = new DstNodeNullness(newVP, isRetn1, retIdxs1);			
			return true;
		}      
		return false;
	}
	
	@Override
	public int hashCode() {
		return srcNode.hashCode() + dstNode.hashCode();
	}
	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdgeNullness)) return false;
		EdgeNullness that = (EdgeNullness) o;
		return srcNode.equals(that.srcNode) && dstNode.equals(that.dstNode);
	}
	public String toString() {
		return srcNode + ";" + dstNode;
	}
}

