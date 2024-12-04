package chord.analyses.derivsz;

import com.google.common.collect.ImmutableSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Set;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import static com.google.common.base.Verify.verify;
import chord.project.analyses.provenance.Tuple;

/** A one-to-one correspondence between tuples and positive integers. */
public final class TupleIndex {
  public TupleIndex() {
    tupleDic = new TObjectIntHashMap<Tuple>();
    tuplePool = Lists.newArrayList();
    tuplePool.add(null); // for 1-based indices
  }

  public int getIndex(Tuple t) {
    int ret = tupleDic.get(t);
    if (ret <= 0) {
      verify(!frozen);
      ret = tuplePool.size();
      tupleDic.put(t, ret);
      tuplePool.add(t);
    }
    return ret;
  }

  public int getOldIndex(Tuple t) {
    int r = tupleDic.get(t);
    verify(r > 0);
    return r;
  }

  public boolean hasIndex(Tuple t) {
    return tupleDic.get(t) > 0;
  }

  // If you are very careful, you can also use indices above size() for
  // auxiliary variables, thus avoiding having to increase the tuplePool.
  public int getFreeIndex() {
    verify(!frozen);
    int idx = tuplePool.size();
    tuplePool.add(null);
    return idx;
  }

  public Tuple getTuple(int i) {
    if (i >= tuplePool.size()) return null;
    return tuplePool.get(i);
  }
  public int size() { return tupleDic.size(); }

  public Set<Tuple> getAllTuples() {
    return Sets.newHashSet(tuplePool.subList(1, tuplePool.size()));
  }

  // DBG
  public void freeze() { frozen = true; }
  public void unfreeze() { frozen = false; }

  public void print(PrintWriter out) {
    for (int i = 1; i < tuplePool.size(); ++i) {
      Tuple t = tuplePool.get(i);
      String s = t == null? "<null_tuple>" : t.toVerboseString();
      out.printf("%d %s%n", i, s);
    }
  }

  @Override public String toString() {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintWriter pw = new PrintWriter(os);
    print(pw);
    pw.flush();
    pw.close();
    return os.toString();
  }

  private final TObjectIntMap<Tuple> tupleDic;
  private final ArrayList<Tuple> tuplePool;

  // DBG
  private boolean frozen = false;
}

