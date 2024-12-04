package chord.analyses.experiment.kobj;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;

import chord.analyses.experiment.solver.Tuple;
import chord.bddbddb.Dom;
import chord.project.analyses.ProgramRel;
import joeq.Compiler.Quad.Quad;

import static com.google.common.base.Verify.verify;

import static chord.util.RelUtil.domH;
import static chord.util.RelUtil.pRel;

/** An abstraction maps parameters to integers. */
public final class Abstraction implements Iterable<Abstraction.ParameterValue> {
  public static final class Parameter {
    public final Quad site;
    public final String type; // "H" or "O"
    public Parameter(Quad site, String type) {
      Preconditions.checkArgument(type.equals("H") || type.equals("O"));
      this.site = site;
      this.type = type;
    }
    @Override public int hashCode() {
      return 31 * (site == null? 0 : site.hashCode()) + type.hashCode();
    }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Parameter)) return false; // Parameter is final
      Parameter p = (Parameter) o;
      if (site == null ^ p.site == null) return false;
      return (site == null || site.equals(p.site)) && type.equals(p.type);
    }
    @Override public String toString() {
      return String.format("Parameter[%s,%s]", type, site);
    }
  }
  public static final class ParameterValue {
    public final Parameter parameter;
    public final int value;
    public ParameterValue(final Parameter parameter, final int value) {
      Preconditions.checkNotNull(parameter);
      this.parameter = parameter;
      this.value = value;
    }
    // Returns {@code null} iff {@code tuple} is not an abstraction tuple.
    public static ParameterValue of(Tuple tuple) {
      String type = null;
      if (tuple.is("HK")) type = "H";
      if (tuple.is("OK")) type = "O";
      if (type == null) return null;
      Quad site = (Quad) tuple.getValue(0);
      int value = (Integer) tuple.getValue(1);
      return new ParameterValue(new Parameter(site, type), value);
    }
  }

  public int valueOf(Quad q, String type) {
    return map.get(new Parameter(q, type));
  }

  public int valueOf(Parameter parameter) {
    return map.get(parameter);
  }

  public void set(Parameter parameter, int value) {
    map.put(parameter, value);
  }

  public void putAll(Abstraction other) {
    map.putAll(other.map);
  }

  @Override public Iterator<ParameterValue> iterator() {
    return new AIterator(map.entrySet());
  }

  public int cost() {
    int r = 0;
    for (int k : map.values()) r += k;
    return r;
  }

  public void max(Abstraction other) {
    for (Map.Entry<Parameter, Integer> e : other.map.entrySet()) {
      Integer oldK = map.get(e.getKey());
      int newK = Math.max(e.getValue(), (oldK == null? Integer.MIN_VALUE : oldK));
      map.put(e.getKey(), newK);
    }
  }

  public static Abstraction cheapest() {
    Abstraction result = new Abstraction();
    for (Object o : domH()) {
      Quad q = (Quad) o;
      result.set(new Parameter(q, "H"), 1);
      result.set(new Parameter(q, "O"), 0);
    }
    return result;
  }

  public static Abstraction constant(int k) {
    Preconditions.checkArgument(k >= 0);
    Abstraction result = new Abstraction();
    for (Object o : domH()) {
      Quad q = (Quad) o;
      result.set(new Parameter(q, "H"), k);
      result.set(new Parameter(q, "O"), k);
    }
    return result;
  }

  // TODO: can be simpler?
  public static Tuple tuple(Parameter parameter, int value) {
    ProgramRel relation = pRel(String.format("%sK", parameter.type));
    Dom[] domains = relation.getDoms();
    verify(domains.length == 2);
    // NOTE: Unsafe operations below.
    Dom<Quad> domSite = (Dom<Quad>) domains[0];
    Dom<Integer> domValue = (Dom<Integer>) domains[1];
    int[] indices =
        new int[] {domSite.getOrAdd(parameter.site), domValue.getOrAdd(value) };
    return new Tuple(relation, indices);
  }
  public static Tuple tuple(ParameterValue pv) {
    return tuple(pv.parameter, pv.value);
  }

  @Override public String toString() {
    return map.toString(); // TODO: better
  }

  private static final class AIterator implements Iterator<ParameterValue> {
    public AIterator(Iterable<Map.Entry<Parameter, Integer>> c) {
      this.i = c.iterator();
    }
    @Override public boolean hasNext() {
      return i.hasNext();
    }
    @Override public ParameterValue next() {
      Map.Entry<Parameter, Integer> e = i.next();
      return new ParameterValue(e.getKey(), e.getValue());
    }
    @Override public void remove() {
      throw new UnsupportedOperationException();
    }

    private Iterator<Map.Entry<Parameter, Integer>> i;
  }

  private final Map<Parameter, Integer> map = Maps.newHashMap();

}
