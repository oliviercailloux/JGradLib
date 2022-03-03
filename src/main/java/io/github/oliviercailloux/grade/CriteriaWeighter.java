package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * A mark aggregator that is able to obtain weights using criteria only, thus
 * with no needs of the marks. This is of great value as it permits to know the
 * weights of the tree without having a marks tree. Thus, an aggregation tree
 * using only criteria weighters is guaranteed to give an equal tree of weights
 * for any two compatible mark tree, provided the two mark trees represent equal
 * trees of sets of criteria.
 */
public interface CriteriaWeighter extends MarkAggregator {
	/**
	 * @return the same thing as {@link #weights(Set)} when called with the same set
	 *         of criteria.
	 * @throws AggregatorException iff the given set of criteria is rejected.
	 */
	ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) throws AggregatorException;

	@Override
	default ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws AggregatorException {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());

		final ImmutableMap<Criterion, Double> weights = weightsFromCriteria(criteria);

		return marks.stream().collect(ImmutableMap.toImmutableMap(s -> s, s -> weights.get(s.getCriterion())));
	}
}
