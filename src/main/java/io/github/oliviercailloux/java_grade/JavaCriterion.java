package io.github.oliviercailloux.java_grade;

import io.github.oliviercailloux.grade.Criterion;

public enum JavaCriterion implements Criterion {

	POM,
	/**
	 * A commit has been done, not through GitHub, but not necessarily with the
	 * right identity.
	 */
	COMMIT,
	/**
	 * Commit exists that uses the right identity.
	 */
	ID;

	@Override
	public String getName() {
		return toString();
	}

}
