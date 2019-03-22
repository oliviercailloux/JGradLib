package io.github.oliviercailloux.java_grade.ex_eck;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExEckCriterion implements Criterion {
	CONTENTS("Repository exists with expected content", 1d), ON_TIME("Submitted on time", 0d, -1d),
	USERNAME("GitHub username submitted on MyCourse", 0d, -0.2d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExEckCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExEckCriterion(String requirement, double maxPoints, double minPoints) {
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
