package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.NoSuchElementException;

/**
 * A tree whose leaves contains marks plus aggregation information at each node,
 * so, producing a tree with marks at each node, plus the ability to explain the
 * aggregation strategy used to obtain them.
 * <p>
 * Equivalent to a single mark iff there are no children in the (root of the)
 * tree.
 * </p>
 */
public class Grade {
	public static Grade given(GradeAggregator aggregator, MarksTree marks) {
		return new Grade(aggregator, marks);
	}

	private final GradeAggregator aggregator;
	private final MarksTree marks;

	private Grade(GradeAggregator aggregator, MarksTree marks) {
		this.aggregator = checkNotNull(aggregator);
		this.marks = checkNotNull(marks);
		/* Just to check that it is able to compute it. */
		getWeightedSubMarks();
	}

	public Mark getMark() {
		/*
		 * This method throws IllegalArgumentException if unable to aggregate, but this
		 * is an implementation detail: after initial check when building, it will not
		 * throw more than announced.
		 */
		if (marks.isMark()) {
			return marks.getMark(CriteriaPath.ROOT);
		}
		return aggregator.getMarkAggregator().aggregate(getWeightedSubMarks().keySet());
	}

	/**
	 * TODO check before that each complex sub-tree has associated aggregation
	 * information.
	 *
	 * @throws NoSuchElementException iff the given criterion is not in this tree.
	 */
	public Grade getGrade(Criterion criterion) throws NoSuchElementException {
		/*
		 * This method throws IllegalArgumentException if unable to aggregate, but this
		 * is an implementation detail: after initial check when building, it will not
		 * throw more than announced.
		 */
		final MarksTree subMarks = marks.getTree(criterion);
		final GradeAggregator subAggregator;
		if (subMarks.isMark()) {
			subAggregator = GradeAggregator.max();
		} else {
			subAggregator = aggregator.getGradeAggregator(criterion);
		}
		return given(subAggregator, subMarks);
	}

	public Grade getGrade(CriteriaPath path) throws NoSuchElementException {
		if (path.isRoot()) {
			return this;
		}
		return getGrade(path.getHead()).getGrade(path.withoutHead());
	}

	/**
	 * @throws NoSuchElementException iff the given criterion is not in this tree.
	 */
	public double getWeight(Criterion criterion) throws NoSuchElementException {
		final ImmutableMap<SubMark, Double> weightedSubMarks = getWeightedSubMarks();
		return weightedSubMarks.keySet().stream().filter(s -> s.getCriterion().equals(criterion))
				.map(weightedSubMarks::get).collect(MoreCollectors.onlyElement());
	}

	public double getWeight(CriteriaPath path) throws NoSuchElementException {
		if (path.isRoot()) {
			return 1d;
		}
		return getWeight(path.getHead()) * getGrade(path.getHead()).getWeight(path.withoutHead());
	}

	/**
	 * @return the criteria in the key set equal the child criteria of this (root of
	 *         the) tree.
	 */
	public ImmutableMap<SubMark, Double> getWeightedSubMarks() {
		/*
		 * This method throws IllegalArgumentException if unable to aggregate, but this
		 * is an implementation detail: after initial check when building, it will not
		 * throw more than announced.
		 */
		final ImmutableSet<SubMark> subMarks = marks.getCriteria().stream()
				.map(c -> SubMark.given(c, getGrade(c).getMark())).collect(ImmutableSet.toImmutableSet());
		return aggregator.getMarkAggregator().weights(subMarks);
	}
}
