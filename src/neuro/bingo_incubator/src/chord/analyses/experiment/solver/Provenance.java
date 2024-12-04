package chord.analyses.experiment.solver;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import chord.util.ProfileUtil;

// For now, just a map.
public final class Provenance implements Iterable<ConstraintItem> {
  public Map<String, List<ConstraintItem>> data;

  public Provenance() {
    data = Maps.newHashMap();
  }

  // Takes a snapshot, so that global data doesn't cause problems.
  // Also, iteration should be faster in this explicit form.
  public static Provenance of(List<LookUpRule> rules) {
    ProfileUtil.start("MAIN", "Provenance.of");
    Provenance result = new Provenance();
    for (LookUpRule r : rules) {
      result.data.put(r.id(), Lists.newArrayList(r.viewAll()));
    }
    ProfileUtil.stop("MAIN", "Provenance.of");
    return result;
  }

  @Override public Iterator<ConstraintItem> iterator() {
    return Iterables.concat(data.values()).iterator();
  }
}
