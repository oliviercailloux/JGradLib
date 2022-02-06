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
public interface MarkAggregator {
	/**
	 * @param marks may be empty; must have all different criteria
	 * @return non-negative numbers such that the mark can be computed by a weighted
	 *         sum (not necessarily equal to a weighted average as the sum may not
	 *         be one); may sum to greater than one, for example, if absolute
	 *         aggregation. May contain zeroes, and may consist in only zeroes. The
	 *         returned key set is a subset of the given set (omitted entries do not
	 *         count for the weighted sum; they are omitted iff it is suggested to
	 *         not mention them in an explanation of the weighted sum, eg because
	 *         the corresponding mark is taken into account in another way as in the
	 *         parametric weighter, otherwise some entries may have a zero weight).
	 * @throws IllegalArgumentException iff the given set of criteria is rejected.
	 */
	ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws IllegalArgumentException;

	/**
	 * @param marks may be empty; must have all different criteria
	 * @return the weighted sum of the marks, according to {@link #getWeights(Set)},
	 *         bound within [−1, 1].
	 * @throws IllegalArgumentException iff the given set of criteria is rejected.
	 */
	default Mark aggregate(Set<SubMark> marks) throws IllegalArgumentException {
		final Map<SubMark, Double> weights = weights(marks);
		final double weightedSum = weights.keySet().stream().mapToDouble(s -> weights.get(s) * s.getPoints()).sum();
		return Mark.given(Double.min(1d, Double.max(weightedSum, 0d)), "");
	}
}
