package chord.analyses.prunerefine.klimited;


import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.Ctxt;
import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

import static chord.analyses.prunerefine.klimited.GlobalInfo.G;

public class TypeStrategy {

	  private Execution X = Execution.v();

	  private HashMap<Quad,Quad> prototypes = new HashMap<Quad,Quad>(); // Map each site to the prototype of its equivalence class
	  private HashMap<Quad,List<Quad>> clusters = new HashMap<Quad,List<Quad>>(); // Map each prototype to all others in equivalence class
	  private boolean use_c; // Use class
	  private boolean use_t; // Type of allocation sites
	  private boolean isIdentity, isSingle;

	  public boolean isIdentity() { return isIdentity; }

	  String description;
	  boolean disallowRepeats; // Whether to truncate early
	  @Override public String toString() { return description; }

	  public TypeStrategy(String description, boolean disallowRepeats) {
	    this.description = description;
	    this.disallowRepeats = disallowRepeats;
	    if ("has".equals(description)) use_c = true;
	    else if ("is".equals(description)) use_t = true;
	    else if ("is,has".equals(description)) use_c = use_t = true;
	    else if ("identity".equals(description)) isIdentity = true;
	    else if ("single".equals(description)) isSingle = true;
	    else throw new RuntimeException("Unknown typeStrategy: "+description);
	  }

	  // COARSEN
	  public Ctxt project(Ctxt c) {
	    // Apply the coarsening elementwise
	    Quad[] elems = new Quad[c.length()];
	    for (int i = 0; i < elems.length; i++)
	      elems[i] = project(c.get(i));

	    // Might need to truncate more to remove repeats
	    Ctxt cc = new Ctxt(elems);
	    // Take the longest barely-repeating prefix (quadratic warning!)
	    if (disallowRepeats) {
	      int len = G.len(cc);
	      if (len <= 1) return cc;
	      int m;
	      for (m = 2; m <= len; m++) { // See if the first m is not non-repeating (c[m-1] exists before)
	        boolean found = false;
	        for (int k = 0; k < m-1; k++)
	          if (elems[k] == elems[m-1]) { found = true; break; }
	        if (found) return G.summarize(cc.prefix(m)); // Longest is length m
	      }
	    }
	    return cc; // Take everything
	  }

	  public Quad project(Quad j) {
	    if (j == null) return null;
	    Quad proto_j = prototypes.get(j);
	    assert proto_j != null : G.jstr(j);
	    return proto_j;
	  }
	  public List<Quad> lift(Quad j) {
	    j = project(j);
	    List<Quad> cluster = clusters.get(j);
	    assert cluster != null : G.jstr(j);
	    return cluster;
	  }

	  public Collection<Quad> usePrototypes(Collection<Quad> sites) {
	    if (isIdentity()) return sites;

	    Set<Quad> prototypeSites = new HashSet();
	    for (Quad j : sites)
	      prototypeSites.add(prototypes.get(j));
	    return prototypeSites;
	  }

	  private void addIdentity(Quad j) {
	    prototypes.put(j, j);
	    List<Quad> l = new ArrayList();
	    l.add(j);
	    clusters.put(j, l);
	  }

	  public void init() {
	    if (isIdentity) {
	      X.logs("TypeStrategy: using identity");
	      for (Quad j : G.jSet) addIdentity(j);
	    }
	    else if (isSingle) { // Put every site into one cluster (just for testing/sanity checking)
	      X.logs("TypeStrategy: using single");
	      Quad proto_j = null;
	      List<Quad> cluster = new ArrayList();
	      for (Quad j : G.jSet) {
	        if (proto_j == null) {
	          proto_j = j;
	          clusters.put(proto_j, cluster);
	        }
	        prototypes.put(j, proto_j);
	        cluster.add(j);
	      }
	    }
	    else {
	      HashMap<Object,Quad> summary2prototypes = new HashMap<Object,Quad>();
	      X.logs("TypeStrategy: containing class (%s), type of site (%s)", use_c, use_t);
	      for (Quad h : G.hSet) {
	        Object summary = null;
	        if (use_c && use_t) summary = new Pair(G.h2c(h), G.h2t(h));
	        else if (use_c) summary = G.h2c(h);
	        else if (use_t) summary = G.h2t(h);
	        else assert false;

	        Quad proto_h = summary2prototypes.get(summary);
	        if (proto_h == null) summary2prototypes.put(summary, proto_h = h);
	        prototypes.put(h, proto_h);

	        List<Quad> cluster = clusters.get(proto_h);
	        if (cluster == null) clusters.put(proto_h, cluster = new ArrayList());
	        cluster.add(h);
	      }
	      for (Quad i : G.iSet) addIdentity(i);
	    }
	    X.logs("  %s sites -> %s clusters", G.jSet.size(), clusters.size());

	    // Output
	    PrintWriter out = Utils.openOut(X.path("typeStrategy"));
	    for (Quad proto_j : clusters.keySet()) {
	      out.println(G.jstr(proto_j));
	      for (Quad j : clusters.get(proto_j))
	        out.println("  "+G.jstr(j));
	    }
	    out.close();
	  }
	
}
