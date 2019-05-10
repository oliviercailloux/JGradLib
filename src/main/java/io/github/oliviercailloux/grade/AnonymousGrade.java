package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableBiMap;

public interface AnonymousGrade {

	public double getGrade();

	public ImmutableBiMap<Criterion, Mark> getMarks();

	public double getMaxGrade();

	public Criterion getCriterion();

	public double getPoints();

	public String getComment();
}
