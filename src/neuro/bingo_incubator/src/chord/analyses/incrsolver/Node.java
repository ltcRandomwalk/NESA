package chord.analyses.incrsolver;

import chord.project.analyses.provenance.Tuple;
import java.util.HashSet;

public class Node {
	public HashSet<Node> children;
	public Node parent;
	public HashSet<Tuple> condition;
	public boolean presInFrnt;
	public String summaryFile;
	
	public Node() {
		this.children = new HashSet<Node>();
		this.parent = null;
		this.condition = new HashSet<Tuple>();
		this.presInFrnt = false;
		this.summaryFile = "";
	}
}
