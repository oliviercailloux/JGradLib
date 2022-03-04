package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rejects nothing.
 */
public sealed interface OwaWeighter extends MarkAggregator permits MaxAggregator,MinAggregator {
	@SuppressWarnings("unused")
	public static final Logger LOGGER = LoggerFactory.getLogger(OwaWeighter.class);

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

		final Comparator<SubMark> comparingPoints = Comparator.<SubMark, Double>comparing(s -> s.getPoints())
				.thenComparing(s -> s.getCriterion(),
						Comparator.comparing(Criterion::getName, Collator.getInstance(Locale.ENGLISH)));
		final Stream<SubMark> subMarksLargestFirstStream = marks.stream().sorted(comparingPoints.reversed());

		final ImmutableMap.Builder<SubMark, Double> weightsBuilder = ImmutableMap.builder();
		final Stream<Double> weightsLargestFirstStream = weights(marks.size());
		Streams.forEachPair(subMarksLargestFirstStream, weightsLargestFirstStream, weightsBuilder::put);
		final ImmutableMap<SubMark, Double> weights = weightsBuilder.build();
		verify(marks.size() == weights.size());
		return weights;
	}

}
