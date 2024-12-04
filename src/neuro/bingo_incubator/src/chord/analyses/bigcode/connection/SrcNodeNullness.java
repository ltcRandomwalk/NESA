/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.analyses.bigcode.connection;

public class SrcNodeNullness {
	
	public VariablesPartitionNullness partition;
	public SrcNodeNullness(VariablesPartitionNullness p) {
		this.partition = p;
	}
	public SrcNodeNullness(SrcNodeNullness srcNode) {
		this.partition = (srcNode.partition == null) ? null : new VariablesPartitionNullness(srcNode.partition);
	}
	
	@Override
	public int hashCode() {
		return partition.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof SrcNodeNullness)) return false;
		SrcNodeNullness that = (SrcNodeNullness) o;
		return partition.equals(that.partition);
	}
	
	public String toString() {
		return "partition@s=" + partition.toString();
	}
}
