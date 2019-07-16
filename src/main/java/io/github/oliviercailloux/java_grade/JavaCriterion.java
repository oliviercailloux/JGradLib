package io.github.oliviercailloux.java_grade;

import io.github.oliviercailloux.grade.Criterion;

public enum JavaCriterion implements Criterion {

	POM;

	@Override
	public String getName() {
		return toString();
	}

}
