package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rejects unknown criteria. If some criteria are missing in the given marks,
 * weights are re-normalized accordingly.
 */
public sealed class StaticWeighter implements CriteriaWeighter permits VoidAggregator {

	public static StaticWeighter given(Map<Criterion, Double> weights) {
		return new StaticWeighter(weights);
	}

	private final ImmutableMap<Criterion, Double> weights;

	protected StaticWeighter(Map<Criterion, Double> weights) {
		this.weights = ImmutableMap.copyOf(weights);
		checkArgument(weights.values().stream().allMatch(Double::isFinite));
		checkArgument(weights.values().stream().allMatch(w -> w >= 0d));
	}

	public ImmutableMap<Criterion, Double> weights() {
		return weights;
	}

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) throws AggregatorException {
		checkCanAggregate(criteria.stream().allMatch(weights::containsKey), "Unknown criterion");

		final double sum = criteria.stream().mapToDouble(weights::get).sum();
		return criteria.stream().collect(ImmutableMap.toImmutableMap(c -> c, c -> weights.get(c) / sum));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof StaticWeighter)) {
			return false;
		}
		final StaticWeighter t2 = (StaticWeighter) o2;
		return weights.equals(t2.weights);
	}

	@Override
	public int hashCode() {
		return Objects.hash(weights);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("weights", weights).toString();
	}
}
