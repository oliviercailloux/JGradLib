package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;

/**
 * Able to aggregate a set of marks associated to criteria, to a single Mark.
 * <p>
 * Some instances reject some sets of criteria, for example, an aggregator using
 * static weights given an unknown criterion; or a parametric weighter given
 * more than one unknown criterion. Implementors must give a programmatic way of
 * determining which sets of criteria are accepted.
 */
public sealed interface MarkAggregator permits ParametricWeighter,CriteriaWeighter,OwaWeighter {
	static void checkCanAggregate(boolean check, String message) throws AggregatorException {
		if (!check) {
			throw new AggregatorException(message);
		}
	}

	/**
	 * @param marks may be empty; must have all different criteria
	 * @return non-negative numbers such that the mark can be computed by a weighted
	 *         sum (not necessarily equal to a weighted average as the sum may not
	 *         be one); may sum to greater than one, for example, if absolute
	 *         aggregation. May contain zeroes, and may consist in only zeroes. The
	 *         returned key set equals the given set.
	 * @throws AggregatorException iff the given set of criteria is rejected.
	 */
	ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws AggregatorException;

	/**
	 * @param marks may be empty; must have all different criteria
	 * @return the weighted sum of the marks, according to {@link #getWeights(Set)},
	 *         bound within [−1, 1].
	 * @throws AggregatorException iff the given set of criteria is rejected.
	 */
	default Mark aggregate(Set<SubMark> marks) throws AggregatorException {
		final Map<SubMark, Double> weights = weights(marks);
		final double weightedSum = weights.keySet().stream().mapToDouble(s -> weights.get(s) * s.getPoints()).sum();
		return Mark.given(Double.min(1d, Double.max(weightedSum, 0d)), "");
	}

	/**
	 * Returns {@code true} iff the given object is a mark aggregator that
	 * aggregates accepts the same sets of criteria and aggregates the sub marks in
	 * the same way as this object.
	 */
	@Override
	public boolean equals(Object o2);
}
