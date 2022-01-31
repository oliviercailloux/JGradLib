package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

class FixedWeightsGradeStructure implements GradeStructure {

	static Mark getMark(GradeStructure structure, Set<SubMark> subMarks) {
		final ImmutableSet<SubMark> weightedSubMarks = subMarks.stream()
				.filter(s -> !structure.isAbsolute(s.getCriterion())).collect(ImmutableSet.toImmutableSet());
		final double weightedSum = weightedSubMarks.stream()
				.mapToDouble(s -> structure.getWeight(s.getCriterion(), subMarks) * s.getGrade().points()).sum();
		final double sumOfWeights = structure.getSumOfWeights(
				weightedSubMarks.stream().map(SubMark::getCriterion).collect(ImmutableSet.toImmutableSet()));
		final boolean hasWeightedCriteria = sumOfWeights != 0d;

		final ImmutableSet<SubMark> absoluteSubMarks = subMarks.stream()
				.filter(s -> structure.isAbsolute(s.getCriterion())).collect(ImmutableSet.toImmutableSet());
		final double absolutePoints = absoluteSubMarks.stream().mapToDouble(s -> s.getGrade().points()).sum();

		if (hasWeightedCriteria) {
			final double weightedAverage = weightedSum / sumOfWeights;
			return Mark.given(weightedAverage + absolutePoints, "");
		}

		final boolean hasAbsoluteCriteria = !absoluteSubMarks.isEmpty();
		if (hasAbsoluteCriteria) {
			return Mark.given(absolutePoints, "");
		}

		throw new IllegalArgumentException("No positively weighted criteria and no absolute criteria");
	}

	private final ImmutableMap<Criterion, Double> weights;
	private final ImmutableMap<Criterion, GradeStructure> subStructures;

	FixedWeightsGradeStructure(Map<Criterion, Double> weights, Map<Criterion, GradeStructure> subStructures) {
		this.weights = ImmutableMap.copyOf(weights);
		this.subStructures = ImmutableMap.copyOf(subStructures);
	}

	@Override
	public boolean isAbsolute(Criterion criterion) {
		return !weights.containsKey(criterion);
	}

	@Override
	public double getWeight(Criterion criterion, Set<SubMark> subMarks) {
		checkArgument(subMarks.stream().map(SubMark::getCriterion).anyMatch(Predicate.isEqual(criterion)));
		checkArgument(!isAbsolute(criterion));
		return weights.get(criterion);
	}

	@Override
	public double getSumOfWeights(Set<Criterion> criteria) {
		return criteria.stream().peek(c -> checkArgument(!isAbsolute(c))).mapToDouble(weights::get).sum();
	}

	@Override
	public Mark getMark(Set<SubMark> subMarks) {
		return FixedWeightsGradeStructure.getMark(this, subMarks);
	}

	@Override
	public GradeStructure getStructure(Criterion criterion) {
		checkArgument(subStructures.containsKey(criterion));
		return subStructures.get(criterion);
	}

}
