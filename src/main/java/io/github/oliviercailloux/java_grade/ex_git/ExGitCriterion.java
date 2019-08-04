package io.github.oliviercailloux.java_grade.ex_git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExGitCriterion implements Criterion {
	REPO_EXISTS("Repository exists", 2), ON_TIME("Delivered on time", 0, -30d), GIT("Commits done using git", 0, -30d),
	USER_NAME("Commit is associated to the right user name", 2), SINGLE_ROOT_COMMIT("Only one root commit", 0d, -3d),
	CONTAINS_START("First commit contains non-empty start.txt", 1),
	HELLO2("First commit contains a blob “hello2” in start.txt", 1), DEV_EXISTS("Branch dev exists", 1),
	DEV_CONTAINS_BOLD("Branch dev contains non-empty bold.txt containing “try 1”", 1d),
	DEV2_EXISTS("Branch dev2 exists", 1),
	ALTERNATIVE_APPROACH("Branch dev2 contains “alternative approach” in bold.txt", 1),
	MERGE1_COMMIT(
			"A single commit exists with exactly two parents, both being parent of the first commit or being the branch dev and dev2",
			2),
	MERGE1_CONTAINS_BOLD("Merged commit contains bold.txt", 1),
	MERGED1("Merged commit contains neither “<<<” nor “===” nor “>>>”", 1),
	AFTER_MERGE1("Commit exists after merged one", 0.5),
	AFTER_MERGE1_BOLD("Commit after merged one contains a blob in bold.txt different than the one merged", 1.5),
	MERGE2_COMMIT("Some commit is parent of two commits each having at least four ascendents", 4);

	private ExGitCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExGitCriterion(String requirement, double maxPoints, double minPoints) {
		checkNotNull(requirement);
		checkArgument(Double.isFinite(maxPoints));
		checkArgument(maxPoints > minPoints);
	}

	@Override
	public String getName() {
		return toString();
	}
}
