package chord.analyses.experiment.solver;

import java.util.Arrays;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.bddbddb.Dom;
import chord.project.analyses.ProgramRel;
import chord.project.ClassicProject;

import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.*;
import static chord.util.RelUtil.*;

/**
 * Represents a tuple in the program relation
 *
 * @author xin
 *
 */
public class Tuple implements Comparable<Tuple> {
  private final ProgramRel relation;
  private final Dom[] domains;
  private final int[] domIndices; // these are "indices IN domains", not OF
  private final int hash;

  public Tuple(ProgramRel relation, int[] indices) {
    this.relation = relation;
    domains = relation.getDoms();
    this.domIndices = indices;
    if (domains.length != domIndices.length) fail("should be equal");
    hash = computeHash();
  }

  /**
   * Assume s has the following form: VH(2,3)
   *
   * @param s
   */
  public Tuple(String s) {
    String splits1[] = s.split("\\(");
    String rName = splits1[0];
    String indexString = splits1[1].replace(")", "");
    String splits2[] = indexString.split(",");
    relation = pRel(rName);
    domains = relation.getDoms();
    domIndices = new int[splits2.length];
    for(int i = 0 ; i < splits2.length; i++){
      domIndices[i] = Integer.parseInt(splits2[i]);
    }
    hash = computeHash();
  }

  public Dom[] getDomains(){
    return this.domains;
  }

  public Object getValue(int idx){
    return this.domains[idx].get(domIndices[idx]);
  }

  public ProgramRel getRel(){
    return relation;
  }

  public String getRelName() {
    return relation.getName();
  }

  public int[] getIndices() {
    return domIndices;
  }

  public boolean is(String relName) {
    return relation.getName().equals(relName);
  }

  public boolean isA(String relNamePrefix) {
    return relation.getName().startsWith(relNamePrefix);
  }

  // NOTE: Apparently Java's type inference is useless if the return is a typevar
  // ... which is why this isn't "T getArg(int i)".
  // ... a proper solution requires global refactoring. Not now.
  public <T> boolean argIs(int i, T value) {
    return getValue(i).equals(value);
  }

  @Override public int compareTo(Tuple o) {
    if (relation.getName().compareTo(o.relation.getName()) != 0)
      return relation.getName().compareTo(o.relation.getName());
    return compare(domIndices, o.domIndices);
  }

	public Tuple project(int k) {
		final int n = domains.length;
		int i;
		{ // space optimization; could be removed
			for (i = 0; i < n && !(domains[i] instanceof DomC); ++i);
			if (i == n) return this;
		}
		int[] newDomIndices = new int[n];
		for (i = 0; i < n; ++i) {
			if (domains[i] instanceof DomC) {
				DomC d = (DomC) domains[i];
				newDomIndices[i] = d.indexOf(d.get(domIndices[i]).prefix(k));
			} else newDomIndices[i] = domIndices[i];
		}
		return new Tuple(relation, newDomIndices);
	}

	// Returns the maximum length of a context mentioned by this tuple. If no
	// context is mentioned, then it returns 0.
	public int getHighestAbs() {
		int r = 0;
		for (int i = 0; i < domains.length; ++i) if (domains[i] instanceof DomC) {
			DomC d = (DomC) (domains[i]);
			r = Math.max(r, d.get(domIndices[i]).length());
		}
		return r;
	}

  @Override
  public int hashCode() { return hash; }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Tuple other = (Tuple) obj;
    if (hash != other.hash)
      return false;
    if (relation == null) {
      if (other.relation != null)
        return false;
    } else if (!relation.equals(other.relation))
      return false;
    if (!Arrays.equals(domIndices, other.domIndices))
      return false;
    return true;
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder("");
    sb.append(relation.getName());
    sb.append("(");
    for(int i = 0; i < domIndices.length; i++){
      if(i!=0)
        sb.append(',');
      sb.append(domIndices[i]);
    }
    sb.append(")");
    return sb.toString();
  }

  public String toVerboseString() {
    StringBuilder sb = new StringBuilder("");
    sb.append(this.toString());
    sb.append(" : ");
    for(int i = 0; i < domIndices.length; i++){
      if(i!=0)
        sb.append(',');
      sb.append(domains[i].get(domIndices[i]));
    }
    return sb.toString();
  }

  private int computeHash() {
    return
      Arrays.hashCode(domIndices)
      + (relation == null? 0 : 31 * relation.hashCode());
  }
}
