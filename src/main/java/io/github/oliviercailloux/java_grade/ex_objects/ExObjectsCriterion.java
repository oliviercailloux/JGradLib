package io.github.oliviercailloux.java_grade.ex_objects;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExObjectsCriterion implements Criterion {
	REPO_EXISTS("Repository exists", 0.5d, 0d), ON_TIME("Delivered on time", 0d, -30d),
	P43("Some source code in project43/src (or projet43/src)", 1d, 0d),
	P47("Some source code in project47/src (or projet47/src)", 1d, 0d),
	P53UTILS("PairOfDice and StatCalc source code in project53utils/src", 0.5d, 0d),
	P53JAR("project53utils/src/utils.jar has right content", 2d, 0d),
	PREFIX("project53main/src source code has a unique package name", 1d, 0d),
	JAR_REQUIRED("project53main/src compiles iff jar is included", 4d, 0d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExObjectsCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExObjectsCriterion(String requirement, double maxPoints, double minPoints) {
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
