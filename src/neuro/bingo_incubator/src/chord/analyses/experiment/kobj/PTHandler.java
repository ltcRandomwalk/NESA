package chord.analyses.experiment.kobj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.analyses.experiment.solver.FormattedConstraint;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.MaxSatGenerator;
import chord.analyses.experiment.solver.ParamTupleConsHandler;
import chord.analyses.experiment.solver.Tuple;
import chord.project.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.util.ProfileUtil;
import chord.util.tuple.object.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import joeq.Compiler.Quad.Quad;

import static com.google.common.base.Verify.verify;
import static chord.util.CollectionUtil.mkPair;
import static chord.util.ExceptionUtil.fail;
import static chord.util.RelUtil.pRel;

public final class PTHandler implements ParamTupleConsHandler {
  private Set<String> derivedRs;
  private Set<String> allRs;
  private Set<Tuple> constTuples;
  private boolean ifMono;
  private boolean ifBool;
  private int boolO;
  private int boolH;

  public PTHandler(boolean ifMono, boolean ifBool, int boolO, int boolH) {
    constTuples = Sets.newHashSet();
    constTuples.add(mkTuple("reachableCM", new int[]{0, 0}));
    constTuples.add(mkTuple("rootCM", new int[]{0, 0}));

    this.ifMono = ifMono;
    this.ifBool = ifBool;
    this.boolO = boolO;
    this.boolH = boolH;
  }

  @Override
  public void init(List<LookUpRule> rules) {
    ProfileUtil.start("MAIN", "PTHandler.init");
    allRs = Sets.newHashSet();
    derivedRs = Sets.newHashSet();
    for (LookUpRule r : rules) {
      derivedRs.add(r.getHeadRelName());
      allRs.add(r.getHeadRelName());
      allRs.addAll(r.getSubRelNames());
    }
    Set<String> rs = Sets.newHashSet(allRs);
    rs.removeAll(derivedRs);
    if (false) System.out.printf("DBG PTHandler nonderived %s\n", rs);
    ProfileUtil.stop("MAIN", "PTHandler.init");
  }

  // For the meaning of return, see ParamTupleConsHandler.
  @Override public Tuple transform(Tuple t, Boolean sign) {
    if (constTuples.contains(t) || !sign) return null;
    if (isParam(t) || isDerived(t)) return t;
    return null; // Assumed to be an input tuple that holds.
  }

  @Override
  public boolean isParam(Tuple t) {
    return t.is("OK") || t.is("HK");
  }

  @Override
  public boolean isDerived(Tuple t) {
    return derivedRs.contains(t.getRelName());
  }

  @Override
  public Set<Tuple> getConstTuples() {
    return constTuples;
  }

  @Override
  public Set<String> getDerivedRelNames() {
    return derivedRs;
  }

  @Override
  public Set<String> getAllRelNames() {
    return allRs;
  }

  // some abbreviations
  static Tuple mkTuple(String n, int[] ps) { return new Tuple(pRel(n), ps); }


}
