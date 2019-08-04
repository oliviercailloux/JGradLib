package io.github.oliviercailloux.java_grade.ex_junit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExJUnitCriterion implements Criterion {
	REPO_EXISTS("Repository exists", 2), ON_TIME("Delivered on time", 0, -30d),
	CLASS_EXISTS("Class ExtractorTests exists", 0.5), CLASS_NAME("Class ExtractorTests is named correctly", 0.5),
	CLASS_IN_TEST("Class ExtractorTests is in src/test/java", 1.5),
	PDF_IN_TEST("hello-world.pdf is in src/test/resources in the expected sub-folder", 1.5),
	TEST_TESTS("Tests are written correctly (corrected manually)", 10),
	GIT("Some commit done without using GitHub web", 2, -30d), BRANCH("Some commit in a test branch", 2);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExJUnitCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExJUnitCriterion(String requirement, double maxPoints, double minPoints) {
		this.requirement = requireNonNull(requirement);
		checkArgument(Double.isFinite(maxPoints));
		checkArgument(maxPoints > minPoints);
		this.maxPoints = maxPoints;
		this.minPoints = minPoints;
	}

	public String getRequirement() {
		return requirement;
	}

	public double getMaxPoints() {
		return maxPoints;
	}

	public double getMinPoints() {
		return minPoints;
	}

	@Override
	public String getName() {
		return toString();
	}
}
