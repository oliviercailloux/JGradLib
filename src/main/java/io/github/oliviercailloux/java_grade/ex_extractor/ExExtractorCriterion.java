package io.github.oliviercailloux.java_grade.ex_extractor;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExExtractorCriterion implements Criterion {
	ON_TIME("Delivered on time", 0d, Double.NEGATIVE_INFINITY), AT_ROOT("Pom is at root of repository", 0.5d),
	COMMIT("Commit done before 3/4 time using git", 2d), GROUP_ID("Group id has the right form", 0.5d),
	UTF("Encoding property", 0.5d, 0d), SOURCE("Maven compiler source property", 0.5d, 0d),
	NO_MISLEADING_URL("No misleading url pointer", 0.5d),
	/**
	 * TODO and on no incorrect version of Guava
	 */
	PDF_DEP("Depend on the latest version of PDFBox", 0.75d, 0d),
	SIMPLE_EXTRACTOR("Interface is at the right place", 0.75d, 0d),
	PREFIX("All own classes are prefixed by the group id", 0.5d, 0d),
	COMPILES("Project compiles using Maven", 0.5d, 0d), IMPL("SimpleExtractor implementation", 8d, 0d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExExtractorCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExExtractorCriterion(String requirement, double maxPoints, double minPoints) {
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
