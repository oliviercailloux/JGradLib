package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import java.util.Map;
import java.util.Set;

public class StructuredGrade {
	static Mark getMark(GradeStructure structure, Set<SubMark> subMarks) {
		ImmutableSet<SubMark> absoluteSubMarks = subMarks.stream().filter(s -> structure.isAbsolute(s.getCriterion()))
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<SubMark> toBeWeightedSubMarks = subMarks.stream()
				.filter(s -> !structure.isAbsolute(s.getCriterion())).collect(ImmutableSet.toImmutableSet());
		ImmutableMap<SubMark, Double> weightedSubMarks = structure.getWeights(toBeWeightedSubMarks);
		return getMark(absoluteSubMarks, weightedSubMarks);
	}

	static Mark getMark(Set<SubMark> absoluteSubMarks, Map<SubMark, Double> weightedSubMarks) {
		final double weightedSum = weightedSubMarks.keySet().stream()
				.mapToDouble(s -> weightedSubMarks.get(s) * s.getGrade().getPoints()).sum();
		final double sumOfWeights = weightedSubMarks.values().stream().mapToDouble(d -> d).sum();
		final boolean hasWeightedCriteria = sumOfWeights != 0d;

		final double absolutePoints = absoluteSubMarks.stream().mapToDouble(s -> s.getGrade().getPoints()).sum();

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
	private ImmutableMap<SubMark, Double> weightedSubMarks;
	private ImmutableSet<SubMark> absoluteSubMarks;

	private StructuredGrade(Grade grade, GradeStructure structure) {
		this.grade = checkNotNull(grade);
		this.structure = checkNotNull(structure);
		weightedSubMarks = null;
		absoluteSubMarks = null;
	}

	public Grade getGrade() {
		return grade;
	}

	public GradeStructure getStructure() {
		return structure;
	}

	public boolean isAbsolute(Criterion criterion) {
		return structure.isAbsolute(criterion);
	}

	/**
	 * @param criterion not absolute
	 */
	public double getWeight(Criterion criterion) {
		initSubMarks();
		final SubMark subMark = weightedSubMarks.keySet().stream().filter(s -> s.getCriterion().equals(criterion))
				.collect(MoreCollectors.onlyElement());
		return weightedSubMarks.get(subMark);
	}

	private void initSubMarks() {
		final boolean hasWeightedSubMarks = weightedSubMarks != null;
		final boolean hasAbsoluteSubMarks = absoluteSubMarks != null;
		verify(hasWeightedSubMarks == hasAbsoluteSubMarks);
		if (hasWeightedSubMarks) {
			return;
		}
		/*
		 * This assumes that the marks of the sub criteria can be computed using
		 * recursion; but does not use the root mark of this grade.
		 */
		final ImmutableSet<SubMark> allSubMarks = grade.getCriteria().stream()
				.map(c -> SubMark.given(c, getStructuredGrade(c).getRootMark())).collect(ImmutableSet.toImmutableSet());
		absoluteSubMarks = allSubMarks.stream().filter(s -> structure.isAbsolute(s.getCriterion()))
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<SubMark> toBeWeightedSubMarks = allSubMarks.stream()
				.filter(s -> !structure.isAbsolute(s.getCriterion())).collect(ImmutableSet.toImmutableSet());
		weightedSubMarks = structure.getWeights(toBeWeightedSubMarks);
	}

	public StructuredGrade getStructuredGrade(Criterion criterion) {
		return StructuredGrade.given(grade.getGrade(criterion), structure.getStructure(criterion));
	}

	/**
	 * @return the points at the root
	 */
	public Mark getRootMark() {
		if (grade.isMark()) {
			return grade.getMark(GradePath.ROOT);
		}
		initSubMarks();
		/* This assumes that the criteria weights can be computed recursively. */
		return getMark(absoluteSubMarks, weightedSubMarks);
	}

}
