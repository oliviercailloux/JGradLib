package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A tree of criteria paths with marks; with no aggregation information, thus,
 * no marks at non-leaf nodes.
 * <p>
 * Another possible implementation:
 *
 * TreeGrade implements MarksTree
 *
 * – Map<CritPath, Mark> // no extensions: no paths p1, p2 such that p2 extends
 * p1.
 * </p>
 * <p>
 * Generally goes logically together with a GradeAggregator (because similar
 * structures are required for aggregation to be possible), but code may be
 * simpler with a grade object alone; and this class may be useful for more
 * fundamental purposes such as collecting marks given by various graders with
 * no specific aggregation purpose in mind; or using a logical categorization of
 * grades that differs from the aggregating structure.
 */
public interface MarksTree {

	public static MarksTree composite(Map<Criterion, MarksTree> subGrades) {
		return CompositeMarksTree.givenGrades(subGrades);
	}

	/**
	 * If {@code true}, calling {@link #getMark(CriteriaPath)} with argument
	 * {@link CriteriaPath#ROOT} will return this object as a {@link Mark}.
	 *
	 * @returns {@code true} iff has a root (empty) grade path, equivalently, iff
	 *          has only a root grade path
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
	 * getGrade(criterion as singleton grade path) = getGrade(criterion).
	 *
	 * @throws NoSuchElementException iff the given criterion is not contained in
	 *                                this object.
	 *
	 */
	public MarksTree getTree(Criterion criterion);

	/**
	 * @return not empty; the singleton containing {@link CriteriaPath#ROOT} iff
	 *         this object is a mark; otherwise, does not contain
	 *         {@link CriteriaPath#ROOT}.
	 */
	public ImmutableSet<CriteriaPath> getPathsToMarks();

	/**
	 * @return {@code true} iff this tree contains a node (terminal or not) at the
	 *         given path.
	 */
	public boolean hasPath(CriteriaPath path);

	/**
	 * Returns the grade at the given path, if it exists in this instance.
	 * <p>
	 * If the given path is the root grade path, returns this instance.
	 * </p>
	 *
	 * @throws NoSuchElementException iff the given path is not contained in this
	 *                                object.
	 * @see #hasPath(CriteriaPath)
	 */
	public MarksTree getTree(CriteriaPath path);

	/**
	 * @throws NoSuchElementException   iff the given path is not contained in this
	 *                                  object.
	 * @throws IllegalArgumentException iff the given path is contained in this
	 *                                  object but is not one of
	 *                                  {@link #getPathsToMarks()}
	 */
	public Mark getMark(CriteriaPath path);

	/**
	 * Two grades are equal iff they have the same paths to marks and the same
	 * marks.
	 */
	@Override
	boolean equals(Object obj);
}
