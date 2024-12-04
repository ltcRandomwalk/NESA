package chord.analyses.experiment.solver;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;

public class UnviableAbstractionFinder {
  /** Returns an unviable abstraction, if any. An abstraction is unviable when
  its tuples are sufficient to derive all queries {@code qs}. If there is no
  such abstraction, then {@code null} is returned. If there are several unviable
  abstractions, then a best-effort is made to pick a small one. (Finding the
  smallest one is a problem on the second level of PH.) */
  public Set<Tuple> get(Set<Tuple> qs) {
    return get(qs, NO_WEIGHTS);
  }

  /** Returns an unviable abstraction, if any. This method is almost the same
  as {@code get(Set<Tuple>)}, so please read the comment there first. The
  difference is only that here the size of an abstraction takes into account
  the weights {@code ws}. Tuple {@code t} has weight
    {@code ws.get(t) == null? 1 : ws.get(t)}.
  The size of an abstraction is the sum of the weights of its tuples.
  */
  public Set<Tuple> get(Set<Tuple> qs, Map<Tuple, Integer> ws) {
    return null;
  }

  public UnviableAbstractionFinder(
      final ParamTupleConsHandler parameterHandler
  ) {
    this.parameterHandler = parameterHandler;
  }

  private final ParamTupleConsHandler parameterHandler;
  private static final Map<Tuple, Integer> NO_WEIGHTS = Maps.newHashMap();
}
