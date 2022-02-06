package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import java.util.Optional;
import java.util.Set;

/**
 * Accepts only sets of two or three criteria that include the weighting and the
 * multiplied one. Aggregates by multiplying the mark of the multiplied
 * criterion by the mark of the weighting one, and, if a third criterion is
 * present, summing with the mark of the third criterion multiplied by the
 * complement of the mark of the weighting criterion (so <i>cW × c1 + (1 − cW) ×
 * c2</i>, where cW is the mark of the weighting criterion).
 */
public class ParametricWeighter implements MarkAggregator {

	private final Criterion multiplied;
	private final Criterion weighting;

	public static ParametricWeighter given(Criterion multiplied, Criterion weighting) {
		return new ParametricWeighter(multiplied, weighting);
	}

	public ParametricWeighter(Criterion multiplied, Criterion weighting) {
		this.weighting = checkNotNull(weighting);
		this.multiplied = checkNotNull(multiplied);
	}

	@Override
	public ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws IllegalArgumentException {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());
		checkArgument(criteria.contains(multiplied));
		checkArgument(criteria.contains(weighting));
		checkArgument(criteria.size() <= 3);

		final SubMark multipliedMark = marks.stream().filter(s -> s.getCriterion().equals(multiplied))
				.collect(MoreCollectors.onlyElement());
		final SubMark weightingMark = marks.stream().filter(s -> s.getCriterion().equals(weighting))
				.collect(MoreCollectors.onlyElement());
		final Optional<SubMark> remainingMark = marks.stream().filter(m -> !m.getCriterion().equals(multiplied))
				.filter(m -> !m.getCriterion().equals(weighting)).collect(MoreCollectors.toOptional());

		final ImmutableMap.Builder<SubMark, Double> builder = ImmutableMap.builder();
		builder.put(multipliedMark, weightingMark.getPoints());
		builder.put(weightingMark, 0d);
		remainingMark.ifPresent(m -> builder.put(m, 1d - weightingMark.getPoints()));
		return builder.build();
	}

}
