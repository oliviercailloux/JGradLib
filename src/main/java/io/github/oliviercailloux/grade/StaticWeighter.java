package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;

/**
 * If some criteria are missing in the given marks, they are considered as if they had a zero mark
 * (i.e., the rest of the criteria receive their normal weight, independently of the possible
 * absence of some other criteria).
 */
public class StaticWeighter extends AbstractStaticWeighter implements PerCriterionWeighter {

  private final double sum;

  public static StaticWeighter given(Map<Criterion, Double> weights) {
    return new StaticWeighter(weights);
  }

  protected StaticWeighter(Map<Criterion, Double> weights) {
    super(weights);
    sum = weights.values().stream().mapToDouble(d -> d).sum();
  }

  @Override
  public double weight(Criterion criterion) throws AggregatorException {
    checkCanAggregate(weights.containsKey(criterion), "Unknown criterion: %s, not in %s.",
        criterion, weights);
    return weights.get(criterion) / sum;
  }

  @Override
  public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria)
      throws AggregatorException {
    checkCanAggregate(criteria.stream().allMatch(weights::containsKey), "Unknown criterion");

    return criteria.stream()
        .collect(ImmutableMap.toImmutableMap(c -> c, c -> weights.get(c) / sum));
  }
}
