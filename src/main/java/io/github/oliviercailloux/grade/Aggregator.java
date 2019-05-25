package io.github.oliviercailloux.grade;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 *
 *
 * @author Olivier Cailloux
 *
 */
public interface Aggregator {
	/**
	 * Provides the criteria that this aggregator knows in advance will intervene
	 * when aggregating.
	 *
	 * @return empty set if unknown (useful with an anonymous aggregator; for
	 *         example, one using a non-weighted average).
	 */
	public ImmutableSet<Criterion> getAggregatedCriteria();

	/**
	 * The returned grade must not necessarily have all the given grades as
	 * sub-grades. For example, an aggregator may want to delete some sub-grades
	 * when their point evaluations do not intervene in the aggregated total; while
	 * merging the comments in the comment of the parent. Consider the case of two
	 * sub-grades: 0, file content incorrect, and 1, file name has correct case;
	 * then we may want to remove information of 1 if the aggregated grade is zero
	 * anyway. In such a case the comment could be moved to the parent or deleted.
	 * Similarly, an aggregator may decide to change some evaluations for some
	 * sub-grades to make the parent grade clearer.
	 *
	 * @param grades not empty.
	 * @return not <code>null</code>.
	 */
	public AnonymousGrade aggregate(Set<Grade> grades);

	/**
	 * Returns the criterion to be set for the parent grade when aggregating.
	 *
	 * @return not <code>null</code>.
	 */
	public Criterion getParentCriterion();

}
