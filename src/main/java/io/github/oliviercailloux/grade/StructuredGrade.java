package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;

public class StructuredGrade {
	public static StructuredGrade given(Grade grade, GradeStructure structure) {
		return new StructuredGrade(grade, structure);
	}

	private final Grade grade;
	private final GradeStructure structure;

	private StructuredGrade(Grade grade, GradeStructure structure) {
		this.grade = checkNotNull(grade);
		this.structure = checkNotNull(structure);
	}

	public boolean isAbsolute(Criterion criterion) {
		return structure.isAbsolute(criterion);
	}

	/**
	 * @param criterion not absolute
	 */
	public double getWeight(Criterion criterion) {
		final ImmutableSet<SubMark> markedCriteria = getSubMarks();
		return structure.getWeight(criterion, markedCriteria);
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
