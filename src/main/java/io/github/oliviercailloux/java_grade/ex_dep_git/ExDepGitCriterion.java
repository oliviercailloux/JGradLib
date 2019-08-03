package io.github.oliviercailloux.java_grade.ex_dep_git;

import io.github.oliviercailloux.grade.Criterion;

public enum ExDepGitCriterion implements Criterion {
	ON_TIME, COMMIT, CHILD_OF_STARTING, PARENT_OF_MY_BRANCH, FIRST_COMMIT, MERGE_COMMIT, DEP;

	@Override
	public String getName() {
		return toString();
	}
}
