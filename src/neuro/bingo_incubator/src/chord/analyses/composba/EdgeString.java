package chord.analyses.composba;

import chord.util.Utils;

public class EdgeString {
	public ASstring srcNode;
	public ASstring dstNode;

	
	public EdgeString(String str) {
		String[] parts;
		parts = str.split("DESTNODE");
		if (!parts[0].equals(""))
			srcNode = new ASstring(parts[0]);
		else
			srcNode = new ASstring("");
		if (parts.length == 2 && !parts[1].equals(""))
			dstNode = new ASstring(parts[1]);
		else
			dstNode = new ASstring("");
	}
		
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof EdgeString) {
			EdgeString that = (EdgeString) obj;
			return Utils.areEqual(this.srcNode, that.srcNode) &&
					Utils.areEqual(this.dstNode, that.dstNode);
		}
		return false;
	}

	public int hashCode() {
		return ((srcNode != null) ? srcNode.hashCode() : 0) +
				((dstNode != null) ? dstNode.hashCode() : 0);
	}
	
	public String toString() {
		return "[SOURCE=[" + srcNode + "],DEST=[" + dstNode + "]]";
	}
}
		
