package chord.analyses.experiment.solver;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.List;

import static chord.util.CollectionUtil.compare;
import static chord.util.ExceptionUtil.fail;

public class FormattedConstraint implements Comparable<FormattedConstraint> {
  /* TODO private */ int weight;
  /* TODO private */ int[] constraint;

  public static FormattedConstraint make(int weight, int ... constraint) {
    return new FormattedConstraint(weight, constraint);
  }
  public static FormattedConstraint unit(int weight, int literal) {
    if (weight < 0) {
      weight = -weight;
      literal = -literal;
    }
    return FormattedConstraint.make(weight, literal);
  }

  public FormattedConstraint(int weight, int[] constraint){
    this.weight = weight;
    this.constraint = constraint;
  }

  public int weight() { return weight; }
  public void weight(int weight) { this.weight = weight; }
  public int[] constraint() { return constraint; }

  public void unitWeight(final int weight) {
    Preconditions.checkState(constraint.length == 1);
    this.weight = weight;
    if (this.weight < 0) {
      this.weight = -this.weight;
      constraint[0] = -constraint[0];
    }
  }

  public FormattedConstraint withWeight(int w) {
    return new FormattedConstraint(w, constraint);
  }

  // TODO assert no duplicates, or remove them
  public void normalize() { Arrays.sort(constraint); }

  // TODO(rg): Is this in the stdlib?
  @Override public int compareTo(FormattedConstraint o) {
    if (weight != o.weight) return compare(weight, o.weight);
    return compare(constraint, o.constraint);
  }

  @Override public boolean equals(Object o) {
    fail("DBG is this called? (82rdweif8d)");
    return
      this == o ||
      (o != null && getClass() == o.getClass()
        && compareTo((FormattedConstraint) o) == 0);
  }

  public String toString(){
    StringBuffer sb = new StringBuffer();
    sb.append(weight);
    for(int i : constraint)
      sb.append(" "+i);
    sb.append(" 0");
    return sb.toString();
  }

  public String toExplicitString(List<Tuple> tuplePool){
    StringBuffer sb = new StringBuffer();
    for(int i : constraint){
      if(i > 0)
        sb.append(tuplePool.get(i).toString());
      else
        sb.append("!"+tuplePool.get(0-i).toString());
      sb.append(" \\/ ");
    }
    return sb.toString();
  }

  @Override public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(constraint);
    result = prime * result + weight;
    return result;
  }
}
