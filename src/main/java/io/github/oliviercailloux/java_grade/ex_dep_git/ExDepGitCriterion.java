package io.github.oliviercailloux.java_grade.ex_dep_git;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.CriterionAndPoints;

public enum ExDepGitCriterion implements CriterionAndPoints {
	ON_TIME("Delivered on time", 0d, Double.NEGATIVE_INFINITY), COMMIT("Own commit created (using git)", 1d),
	FIRST_COMMIT("Commit created child of starting commit and ancestor of my-branch", 2d),
	MERGE_COMMIT("Commit created with two parents as requested (among which 'bba2a8')", 2d),
	DEP("Correct dependency to JGit", 2d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExDepGitCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExDepGitCriterion(String requirement, double maxPoints, double minPoints) {
		this.requirement = requireNonNull(requirement);
		checkArgument(Double.isFinite(maxPoints));
		checkArgument(maxPoints > minPoints);
		this.maxPoints = maxPoints;
		this.minPoints = minPoints;
	}

	@Override
	public String getRequirement() {
		return requirement;
	}

	@Override
	public double getMaxPoints() {
		return maxPoints;
	}

	@Override
	public double getMinPoints() {
		return minPoints;
	}
}
