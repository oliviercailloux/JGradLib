package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;

class MaxGradeStructure implements GradeStructure {

	private final ImmutableSet<Criterion> absolutes;

	MaxGradeStructure(Set<Criterion> absolutes) {
		this.absolutes = ImmutableSet.copyOf(absolutes);
	}

	@Override
	public boolean isAbsolute(Criterion criterion) {
		return absolutes.contains(criterion);
	}

	@Override
	public double getWeight(Criterion criterion, Set<SubMark> subMarks) {
		checkArgument(subMarks.stream().map(SubMark::getCriterion).anyMatch(Predicate.isEqual(criterion)));
		checkArgument(!isAbsolute(criterion));
		final Comparator<SubMark> comparingPoints = Comparator.comparing(s -> s.getGrade().points());
		final ImmutableSortedSet<SubMark> subMarksLargestFirst = ImmutableSortedSet.copyOf(comparingPoints.reversed(),
				subMarks);

		final int criterionIndexByLargestMarks = Iterables
				.indexOf(subMarksLargestFirst.stream().map(SubGrade::getCriterion).toList(), c -> c.equals(criterion));
		verify(criterionIndexByLargestMarks >= 0);
		return criterionIndexByLargestMarks == 0 ? 1d : 0d;
	}

	@Override
	public double getSumOfWeights(Set<Criterion> criteria) {
		checkArgument(criteria.stream().allMatch(c -> !isAbsolute(c)));
		return criteria.isEmpty() ? 0d : 1d;
	}

	@Override
	public Mark getMark(Set<SubMark> subMarks) {
		return FixedWeightsGradeStructure.getMark(this, subMarks);
	}

}
