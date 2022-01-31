package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;

/**
 * A tree of criterionpaths with marks; with no aggregation information, thus,
 * no marks at non-leaf nodes.
 * <p>
 * Another possible implementation:
 *
 * TreeGrade implements Grade
 *
 * – Map<CritPath, Mark> // no extensions: no paths p1, p2 such that p2 extends
 * p1.
 * </p>
 */
public interface Grade {
	/**
	 * returns {@code true} iff has a root (empty) critpath, equivalently, iff has
	 * only a root critpath
	 */
	public boolean isMark();

	/**
	 * @return {@code true} iff is not a mark, equivalently, has at least one
	 *         criterion
	 */
	public boolean isComposite();

	public Grade getGrade(GradePath path);

	/**
	 * getGrade(criterion as path) = getSubGrade(criterion).toGrade().
	 *
	 */
	public SubGrade getSubGrade(Criterion criterion);

	public Mark getMark(GradePath path);

	public ImmutableSet<GradePath> getPathsToMarks();
}
