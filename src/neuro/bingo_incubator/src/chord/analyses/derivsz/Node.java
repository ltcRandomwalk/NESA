package chord.analyses.derivsz;

import java.util.HashSet;
import gnu.trove.set.hash.TIntHashSet;

public class Node {
	public HashSet<Node> children;
	public Node parent;
	public TIntHashSet condition;
	public int id;
	
	public Node() {
		this.children = new HashSet<Node>();
		this.parent = null;
		this.condition = new TIntHashSet();
		this.id = 0;
	}
}
