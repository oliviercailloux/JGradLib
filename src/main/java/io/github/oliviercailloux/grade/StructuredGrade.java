package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class StructuredGrade {
	static Mark getMark(GradeStructure structure, Set<SubMark> subMarks) {
		final ImmutableSet<SubMark> weightedSubMarks = subMarks.stream()
				.filter(s -> !structure.isAbsolute(s.getCriterion())).collect(ImmutableSet.toImmutableSet());
		final ImmutableMap<Criterion, Double> weights = structure.getWeights(weightedSubMarks);
		final double weightedSum = weightedSubMarks.stream()
				.mapToDouble(s -> weights.get(s.getCriterion()) * s.getGrade().points()).sum();
		final double sumOfWeights = weights.values().stream().mapToDouble(d -> d).sum();
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

	public static StructuredGrade given(Grade grade, GradeStructure structure) {
		return new StructuredGrade(grade, structure);
	}

	private final Grade grade;
	private final GradeStructure structure;
	private ImmutableMap<Criterion, Double> weights;

	private StructuredGrade(Grade grade, GradeStructure structure) {
		this.grade = checkNotNull(grade);
		this.structure = checkNotNull(structure);
		weights = null;
	}

	public boolean isAbsolute(Criterion criterion) {
		return structure.isAbsolute(criterion);
	}

	/**
	 * @param criterion not absolute
	 */
	public double getWeight(Criterion criterion) {
		return getWeights().get(criterion);
	}

	private ImmutableMap<Criterion, Double> getWeights() {
		weights = structure.getWeights(getSubMarks());
		return weights;
	}

	private ImmutableSet<SubMark> getSubMarks() {
		/*
		 * This assumes that the marks of the sub criteria can be computed using
		 * recursion; but does not use the root mark of this grade.
		 */
		final ImmutableSet<SubMark> markedCriteria = grade.getCriteria().stream()
				.map(c -> SubMark.given(c, getStructuredGrade(c).getRootMark())).collect(ImmutableSet.toImmutableSet());
		return markedCriteria;
	}

	public StructuredGrade getStructuredGrade(Criterion criterion) {
		return StructuredGrade.given(grade.getGrade(criterion), structure.getStructure(criterion));
	}

	/**
	 * @return the points at the root
	 */
	public Mark getRootMark() {
		/* This assumes that the criteria weights can be computed recursively. */
		return structure.getMark(getSubMarks());
	}

}
