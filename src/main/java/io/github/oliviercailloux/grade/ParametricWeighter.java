package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import java.util.Objects;
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
public final class ParametricWeighter implements MarkAggregator {

	static Criterion toPenalized(Criterion original) {
		return Criterion.given(original.getName() + ", penalty");
	}

	private final Criterion multiplied;
	private final Criterion weighting;

	public static ParametricWeighter given(Criterion multiplied, Criterion weighting) {
		return new ParametricWeighter(multiplied, weighting);
	}

	private ParametricWeighter(Criterion multiplied, Criterion weighting) {
		this.weighting = checkNotNull(weighting);
		this.multiplied = checkNotNull(multiplied);
	}

	public Criterion multipliedCriterion() {
		return multiplied;
	}

	public Criterion weightingCriterion() {
		return weighting;
	}

	@Override
	public ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) throws AggregatorException {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());
		checkCanAggregate(criteria.size() <= 3, "Too many criteria");

		if (criteria.isEmpty()) {
			return ImmutableMap.of();
		}

		checkCanAggregate(criteria.contains(multiplied), "Multiplied criterion not found among %s", marks);
		checkCanAggregate(criteria.contains(weighting), "Weighting criterion not found");

		final SubMark multipliedMark = marks.stream().filter(s -> s.getCriterion().equals(multiplied))
				.collect(MoreCollectors.onlyElement());
		final SubMark weightingMark = marks.stream().filter(s -> s.getCriterion().equals(weighting))
				.collect(MoreCollectors.onlyElement());
		final Optional<SubMark> remainingMark = marks.stream().filter(m -> !m.getCriterion().equals(multiplied))
				.filter(m -> !m.getCriterion().equals(weighting)).collect(MoreCollectors.toOptional());

		final ImmutableMap.Builder<SubMark, Double> builder = ImmutableMap.builder();
		final double weightingValue = weightingMark.getPoints();
		checkCanAggregate(weightingValue >= 0d, "Negative weighting points in set %s.", marks);
		builder.put(multipliedMark, weightingValue);
		builder.put(weightingMark, 0d);
		remainingMark.ifPresent(m -> builder.put(m, 1d - weightingValue));
		return builder.build();
	}

	/**
	 * Returns [the multiplied criterion × 100%, − the multiplied criterion × (1 −
	 * weighting)]. The second one has a different criterion name and uses the
	 * comment of the weighting sub mark. This breaks the {@link #weights} contract
	 * as the returned criteria set generally differs from the input criteria set
	 * (although the size is the same). Only authorized if there are two criteria,
	 * not three.
	 */
	public ImmutableMap<SubMark, Double> weightsWithPenalty(Set<SubMark> marks) throws AggregatorException {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());
		checkCanAggregate(criteria.contains(multiplied), "Multiplied criterion not found");
		checkCanAggregate(criteria.contains(weighting), "Weighting criterion not found");
		checkCanAggregate(criteria.size() <= 2, "Too many criteria");

		final SubMark multipliedMark = marks.stream().filter(s -> s.getCriterion().equals(multiplied))
				.collect(MoreCollectors.onlyElement());
		final SubMark weightingMark = marks.stream().filter(s -> s.getCriterion().equals(weighting))
				.collect(MoreCollectors.onlyElement());

		final Criterion multipliedPenalty = toPenalized(multiplied);
		final Mark negativeMultipledMark = Mark.given(-multipliedMark.getPoints(), weightingMark.comment());
		final SubMark penaltySubMark = SubMark.given(multipliedPenalty, negativeMultipledMark);

		final ImmutableMap.Builder<SubMark, Double> builder = ImmutableMap.builder();
		final double weightingValue = weightingMark.getPoints();
		checkCanAggregate(weightingValue >= 0d, "Negative weighting points");
		builder.put(multipliedMark, 1d);
		builder.put(penaltySubMark, 1d - weightingValue);
		return builder.build();
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof ParametricWeighter)) {
			return false;
		}
		final ParametricWeighter t2 = (ParametricWeighter) o2;
		return multiplied.equals(t2.multiplied) && weighting.equals(t2.weighting);
	}

	@Override
	public int hashCode() {
		return Objects.hash(multiplied, weighting);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Multiplied", multiplied).add("Weighting", weighting).toString();
	}

}
