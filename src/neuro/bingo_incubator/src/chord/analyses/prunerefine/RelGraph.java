package chord.analyses.prunerefine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.Execution;

//For visualization
class RelNode {
	Execution X = Execution.v();
	List<Object> rel;
	List<RelNode> edges = new ArrayList<RelNode>();
	List<String> names = new ArrayList<String>();
	boolean visited;
	boolean root = true;

	RelNode(List<Object> rel) { this.rel = rel; }

	// Assume: if node visited iff children are also visited
	void clearVisited() {
		if (!visited) return;
		visited = false;
		for (RelNode node : edges)
			node.clearVisited();
	}

	String nameContrib(String name) { return name == null ? "" : "("+name+") "; }

	String extra() { return ""; }

	void display(String prefix, String parentName) {
		X.logs(prefix + extra() + nameContrib(parentName) + this + (edges.size() > 0 ? " {" : ""));
		String newPrefix = prefix+"  ";
		visited = true;
		for (int i = 0; i < edges.size(); i++) {
			RelNode node = edges.get(i);
			String name = names.get(i);
			if (node.visited) X.logs(newPrefix + node.extra() + nameContrib(name) + node + " ...");
			else node.display(newPrefix, name);
		}
		if (edges.size() > 0) X.logs(prefix+"}");
	}

	@Override public String toString() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < rel.size(); i++) {
			b.append(rel.get(i).toString());
			if (i == 0) b.append('[');
			else if (i == rel.size()-1) b.append(']');
			else b.append(' ');
		}
		return b.toString();
	}
}

//Graph where nodes are relations r_X and edges are transitions t_X_Y
public class RelGraph {
	Execution X = Execution.v();
	HashMap<List<Object>,RelNode> nodes = new HashMap<List<Object>,RelNode>();

	RelNode getNode(List<Object> rel) {
		RelNode node = nodes.get(rel);
		if (node == null)
			nodes.put(rel, node = new RelNode(rel));
		return node;
	}

	void add(List<Object> s, String name, List<Object> t) {
		RelNode node_s = getNode(s);
		RelNode node_t = getNode(t);
		//X.logs("EDGE | %s | %s", node_s, node_t);
		node_s.names.add(name);
		node_s.edges.add(node_t);
		node_t.root = false;
	}

	public void display() {
		X.logs("===== GRAPH =====");
		for (RelNode node : nodes.values()) {
			if (node.root) {
				node.clearVisited();
				node.display("", null);
			}
		}
	}

	List<Object> buildRel(String relName, Object[] l, int[] indices) {
		List<Object> rel = new ArrayList<Object>();
		rel.add(relName);
		for (int i : indices) rel.add(l[i]);
		return rel;
	}

	public void loadTransition(String name, String relName, String rel_s, String rel_t, int[] indices_s, int[] indices_t) {
		if (name.equals("-")) name = null;
		ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt(relName); rel.load();
		for (Object[] l : rel.getAryNValTuples()) {
			List<Object> s = buildRel(rel_s, l, indices_s);
			List<Object> t = buildRel(rel_t, l, indices_t);
			add(t, name, s); // Backwards
		}
		rel.close();
	}
}
