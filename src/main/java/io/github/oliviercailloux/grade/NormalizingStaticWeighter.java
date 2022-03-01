package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;

/**
 * If some criteria are missing in the given marks, weights are re-normalized
 * accordingly.
 */
public final class NormalizingStaticWeighter extends AbstractStaticWeighter {

	public static NormalizingStaticWeighter given(Map<Criterion, Double> weights) {
		return new NormalizingStaticWeighter(weights);
	}

	protected NormalizingStaticWeighter(Map<Criterion, Double> weights) {
		super(weights);
	}

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) throws AggregatorException {
		checkCanAggregate(criteria.stream().allMatch(weights::containsKey), "Unknown criterion");

		final double sum = criteria.stream().mapToDouble(weights::get).sum();
		return criteria.stream().collect(ImmutableMap.toImmutableMap(c -> c, c -> weights.get(c) / sum));
	}
}
