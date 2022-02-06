package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;

/**
 * Rejects unknown criteria. If some criteria are missing in the given marks,
 * weights are re-normalized accordingly.
 */
public class StaticWeighter implements MarkAggregator {

	public static StaticWeighter given(Map<Criterion, Double> weights) {
		return new StaticWeighter(weights);
	}

	private final Map<Criterion, Double> weights;

	public StaticWeighter(Map<Criterion, Double> weights) {
		this.weights = checkNotNull(weights);
		checkArgument(weights.values().stream().allMatch(Double::isFinite));
		checkArgument(weights.values().stream().allMatch(w -> w >= 0d));
	}

	@Override
	public ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws IllegalArgumentException {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());
		checkArgument(criteria.stream().allMatch(weights::containsKey));

		final double sum = marks.stream().map(SubMark::getCriterion).mapToDouble(weights::get).sum();
		return marks.stream().collect(ImmutableMap.toImmutableMap(s -> s, s -> weights.get(s.getCriterion()) / sum));
	}

}
