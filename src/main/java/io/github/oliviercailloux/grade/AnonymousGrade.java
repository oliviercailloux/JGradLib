package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableBiMap;

public interface AnonymousGrade {

	/**
	 * @return no criterion are a root criterion. Empty map iff this grade is a
	 *         {@link CriterionAndMark}.
	 */
	public ImmutableBiMap<CriterionAndPoints, GradeWithStudentAndCriterion> getMarks();

	public double getMaxGrade();

	public CriterionAndPoints getCriterion();

	/**
	 * @return unscaled: typically, a number between 0 and 1, but it’s not
	 *         mandatory: it must simply be understood by the aggregators which will
	 *         be given this grade (or other users of this grade).
	 */
	public double getPoints();

	public String getComment();
}
