package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;

/**
 * Rejects unknown criteria. If some criteria are missing in the given marks,
 * weights are re-normalized accordingly.
 */
public class StaticWeighter implements CriteriaWeighter {

	public static StaticWeighter given(Map<Criterion, Double> weights) {
		return new StaticWeighter(weights);
	}

	private final ImmutableMap<Criterion, Double> weights;

	public StaticWeighter(Map<Criterion, Double> weights) {
		this.weights = ImmutableMap.copyOf(weights);
		checkArgument(weights.values().stream().allMatch(Double::isFinite));
		checkArgument(weights.values().stream().allMatch(w -> w >= 0d));
	}

	public ImmutableMap<Criterion, Double> weights() {
		return weights;
	}

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria)
			throws IllegalArgumentException {
		checkArgument(criteria.stream().allMatch(weights::containsKey));

		final double sum = criteria.stream().mapToDouble(weights::get).sum();
		return criteria.stream().collect(ImmutableMap.toImmutableMap(c -> c, c -> weights.get(c) / sum));
	}

}
