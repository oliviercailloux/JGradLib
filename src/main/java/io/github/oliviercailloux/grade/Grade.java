package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import java.util.Map;

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

	public static Grade composite(Map<Criterion, Grade> subGrades) {
		return CompositeGrade.givenGrades(subGrades);
	}

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

	/**
	 * @return empty iff this is a mark.
	 */
	public ImmutableSet<Criterion> getCriteria();

	/**
	 * getGrade(criterion as singleton path) = getGrade(criterion).
	 *
	 */
	public Grade getGrade(Criterion criterion);

	/**
	 * getGrade(criterion as singleton path) = getSubGrade(criterion).toGrade().
	 *
	 */
	public SubGrade getSubGrade(Criterion criterion);

	public ImmutableSet<GradePath> getPathsToMarks();

	public Grade getGrade(GradePath path);

	public Mark getMark(GradePath path);
}
