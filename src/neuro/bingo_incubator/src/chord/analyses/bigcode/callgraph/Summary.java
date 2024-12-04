package chord.analyses.bigcode.callgraph;



import java.util.BitSet;
import java.util.HashMap;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.Quad;
import chord.util.ArraySet;
import chord.util.Utils;

public class Summary {
	public AbstractState srcNode;
	public AbstractState dstNode;
	

	public Summary() {
		srcNode = new AbstractState();
		dstNode = new AbstractState();
	}

	public Summary(AbstractState s, AbstractState d) {
		assert (s != null && d != null);
		srcNode = s;
		dstNode = d;
	}

	public Summary(String str) {
		String[] parts;
		parts = str.split(":DESTNODE:");
		if (!parts[0].equals(""))
			srcNode = new AbstractState(parts[0], false);
		else
			srcNode = new AbstractState();

		if (parts.length == 2 && !parts[1].equals("") && srcNode.badValue == false)
			dstNode = new AbstractState(parts[1], false);
		else
			dstNode = new AbstractState();
	}
	
	public boolean merge(Summary that) {
		if (that == null) return false;
		boolean modified = this.srcNode.merge(that.srcNode);
		modified |= this.dstNode.merge(that.dstNode);
		return modified;
		
	}
		
	public boolean isBadValue() {
		return (srcNode.badValue || dstNode.badValue);
	}
	
	@Override
	public String toString() {
		return "[SOURCE=[" + srcNode + "],DEST=[" + dstNode + "]]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof Summary) {
			Summary that = (Summary) obj;
			return Utils.areEqual(this.srcNode, that.srcNode) &&
					Utils.areEqual(this.dstNode, that.dstNode);
		}
		return false;
	}
	
	public String toParsableString() {
		String srcStr = srcNode.toParsableString();
		String dstStr = dstNode.toParsableString();
		return srcStr + ":DESTNODE:" + dstStr;
	}

	@Override
	public int hashCode() {
		return ((srcNode != null) ? srcNode.hashCode() : 0) +
				((dstNode != null) ? dstNode.hashCode() : 0);
	}
	
	public boolean isAppAccessed() {
		if (srcNode.isAppAccessed()) return true;
		//else if (dstNode.isAppAccessed()) return true;
		else return false;
	}
	
	public void setAppAccessed() {
		srcNode.setAppAccessed();
		dstNode.setAppAccessed();
	}
	
	public void setCallbkPres() {
		srcNode.setCallbkPres();
		dstNode.setCallbkPres();
	}
	
	public boolean IsCallbkPres() {
		if (dstNode.IsCallbkPres()) return true;
		else return false;
	}
}
		
