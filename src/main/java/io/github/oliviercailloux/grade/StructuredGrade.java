package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import java.util.Comparator;
import java.util.Set;

public class StructuredGrade {
	public static StructuredGrade given(Grade grade, GradeStructure structure) {
		return new StructuredGrade(grade, structure);
	}

	private static void checkCanAggregate(Grade grade, GradeStructure structure) {
		/*
		 * This could be relaxed by using zero for missing absolute values,
		 * renormalizing weights, and extending owaStrs.
		 */
		checkArgument(grade.getCriteria().containsAll(structure.getKnownCriteria()));

		for (Criterion criterion : grade.getCriteria()) {
			final Grade subGrade = grade.getGrade(criterion);
			final GradeStructure subStructure = structure.getStructure(criterion);
			checkCanAggregate(subGrade, subStructure);
		}
	}

	private final Grade grade;
	private final GradeStructure structure;

	private StructuredGrade(Grade grade, GradeStructure structure) {
		this.grade = checkNotNull(grade);
		this.structure = checkNotNull(structure);
		checkCanAggregate(grade, structure);
	}

	public boolean isAbsolute(Criterion criterion) {
		return structure.isAbsolute(criterion);
	}

	/**
	 * @return one if absolute
	 */
	public double getWeight(Criterion criterion) {
		/*
		 * This assumes that the marks of the sub criteria can be computed using
		 * recursion; but does not use the root mark of this grade.
		 */
		if (isAbsolute(criterion)) {
			return 1d;
		}

		if (structure.hasWeight(criterion)) {
			return structure.getWeight(criterion);
		}

		if (structure.isInOwa(criterion)) {
			final OwaStructure owaStructure = structure.getOwaStructure(criterion);
			final ImmutableSet<Criterion> criteria = owaStructure.getCriteria();
			final int criterionIndexByLargestMarks = getCriterionIndexByLargestMarksAmong(criterion, criteria);
			return owaStructure.getWeightForPosition(criterionIndexByLargestMarks);
		}

		verify(!structure.getKnownCriteria().contains(criterion));
		{
			final ImmutableSet<Criterion> unknownCriteria = Sets
					.difference(grade.getCriteria(), structure.getKnownCriteria()).immutableCopy();
			verify(unknownCriteria.contains(criterion));
			final int criterionIndexByLargestMarks = getCriterionIndexByLargestMarksAmong(criterion, unknownCriteria);
			return structure.getDefaultOwaWeightForPosition(criterionIndexByLargestMarks);
		}
	}

	private int getCriterionIndexByLargestMarksAmong(Criterion criterion, Set<Criterion> criteria) {
		verify(grade.getCriteria().containsAll(criteria));
		final Comparator<SubGrade> comparingPoints = Comparator
				.comparing(s -> s.getGrade().getMark(GradePath.ROOT).points());
		final ImmutableSortedSet<SubGrade> markedCriteria = criteria.stream()
				.map(c -> SubGrade.given(c, getStructuredGrade(c).getRootMark()))
				.collect(ImmutableSortedSet.toImmutableSortedSet(comparingPoints.reversed()));

		final int criterionIndexByLargestMarks = Iterables
				.indexOf(markedCriteria.stream().map(SubGrade::getCriterion).toList(), c -> c.equals(criterion));
		return criterionIndexByLargestMarks;
	}

	public StructuredGrade getStructuredGrade(Criterion criterion) {
		return StructuredGrade.given(grade.getGrade(criterion), structure.getStructure(criterion));
	}

	/**
	 * @return the points at the root
	 */
	public Mark getRootMark() {
		/* This assumes that the criteria weights can be computed recursively. */

	}

}
