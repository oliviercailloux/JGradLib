package io.github.oliviercailloux.java_grade.ex_two_sets;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.CriterionAndPoints;

public enum ExTwoSetsCriterion implements CriterionAndPoints {
	REPO_EXISTS("Repository exists", 0.5d, 0d), ON_TIME("Delivered on time", 0d, -30d),
	EX_57("Exercice 5.7 also attempted", 0.5d, 0d), TYPE_SET("Uses type Set<Integer> (and compiles)", 2d, 0d),
	THROWS("Throws defensive exceptions", 1d, 0d), JAVADOC("Uses Javadoc and no auto-generated doc", 1d, 0d),
	CONCATENATES("Uses appropriate construct to manipulate the sets", 1.5d, 0d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExTwoSetsCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExTwoSetsCriterion(String requirement, double maxPoints, double minPoints) {
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
