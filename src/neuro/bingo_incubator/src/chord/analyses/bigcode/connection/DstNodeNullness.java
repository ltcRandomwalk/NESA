/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.analyses.bigcode.connection;


import java.util.HashSet;
import java.util.Set;

import chord.util.ArraySet;


public class DstNodeNullness {
	public final VariablesPartitionNullness partition;
	public final boolean isRetn;
	public final Set<Integer> retIdxs;
	
	public DstNodeNullness(VariablesPartitionNullness part, boolean isRetn, Set<Integer> retIdxs) {
		this.partition = part;
		this.isRetn = isRetn;
		this.retIdxs = retIdxs;
	}
	public DstNodeNullness(DstNodeNullness dstNode) {
		this.partition = (dstNode.partition == null) ? null : new VariablesPartitionNullness(dstNode.partition);
		this.retIdxs = (dstNode.retIdxs == null) ? null : new HashSet<Integer>(dstNode.retIdxs);
		this.isRetn = dstNode.isRetn;
	}
	@Override
	public int hashCode() {
		return partition.hashCode();
	}
	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof DstNodeNullness)) return false;
		DstNodeNullness that = (DstNodeNullness) o;		
		return (partition.equals(that.partition) && isRetn == that.isRetn && 
			   (((retIdxs == null) && (that.retIdxs == null)) || 
			    ((retIdxs != null) && (retIdxs.equals(that.retIdxs)))));	
	}
	
	public String toString() {
		return "partition@d=" + partition.toString() +
			 "; retIdx@d=" + ((retIdxs != null) ? retIdxs.toString():"") + "; r@d: " + isRetn;
	}
}

