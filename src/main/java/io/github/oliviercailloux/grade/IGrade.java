package io.github.oliviercailloux.grade;

import java.util.Map;

/**
 *
 *
 * @author Olivier Cailloux
 *
 */
public interface IGrade {
	public double getPoints();

	public String getComment();

	/**
	 * Returns the sub grades, empty iff this grade is a mark.
	 */
	public Map<Criterion, Grade> getSubGrades();
}
