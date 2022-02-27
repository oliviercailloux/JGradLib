package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rejects nothing.
 */
public sealed interface OwaWeighter extends MarkAggregator permits MaxAggregator {
	/**
	 * @param size ≥ 0
	 * @return the weights, starting with the one corresponding to the biggest mark,
	 *         such that the mark can be computed using a weighted sum. Non-negative
	 *         numbers.
	 */
	Stream<Double> weights(int size);

	@Override
	default ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());

		final Comparator<SubMark> comparingPoints = Comparator.comparing(s -> s.getMarksTree().getPoints());
		final Stream<SubMark> subMarksLargestFirstStream = marks.stream().sorted(comparingPoints.reversed());

		final ImmutableMap.Builder<SubMark, Double> weightsBuilder = ImmutableMap.builder();
		final Stream<Double> weightsLargestFirstStream = weights(marks.size());
		Streams.forEachPair(subMarksLargestFirstStream, weightsLargestFirstStream, weightsBuilder::put);
		return weightsBuilder.build();
	}

}
