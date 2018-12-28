package io.github.oliviercailloux.st_projects.ex3;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.st_projects.model.Criterion;

public enum Ex3Criterion implements Criterion {
	REPO_EXISTS("Repository exists", 0.5d, 0d), ON_TIME("Delivered on time", 0d, -30d),
	GROUP_ID("Group id follows Maven best practices", 0d, -1d), JUNIT5_DEP("JUnit 5 dependency", 1d, 0d),
	UTF("Encoding property", 0d, -0.5d), SOURCE("Maven compiler source property", 0d, -0.5d),
	NO_MISLEADING_URL("No misleading url pointer", 0d, -0.5d), WAR("war packaging", 0d, -1d),
	PREFIX("All classes are prefixed by the group id", 0d, -1d), COMPILE("mvn compile (without tests)", 1d, 0d),
	DO_GET("Method doGet in servlet", 0.5d, 0d), AUTO("No noisy auto-generated code", 0d, -1d),
	EXC("Exceptions are not hidden", 0d, -1d), LOC("Locale set to English", 0.5d, 0d),
	MTYPE("Media type is PLAIN", 0.5d, 0d), ANNOT("Contains @WebServlet listening on 'hello'", 0.5d, 0d),
	FINAL_NAME("The final name is 'myapp', matching the requested context root", 0.5d, 0d),
	ONLY_ORIG("No derivative files in repo", 0d, -0.5d),
	GET_HELLO("Get request is answered with 'Hello world'", 1d, 0d), TEST_EXISTS("Unit test exists", 0.5d, 0d),
	TEST_LOCATION("Unit tests are located in test folder", 0.5d, 0d), TEST_GREEN("Unit test is green", 1d, 0d),
	TEST_TESTS("Unit test tests what it is supposed to", 1.5d, 0d),
	TRAVIS_CONF("Travis .yaml configuration file exists", 0.5d, 0d),
	TRAVIS_OK("Travis configuration is correct.", 1.5d, 0d), TRAVIS_BADGE("Correct badge in README.", 1.0d, 0d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private Ex3Criterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private Ex3Criterion(String requirement, double maxPoints, double minPoints) {
		this.requirement = requireNonNull(requirement);
		checkArgument(Double.isFinite(maxPoints));
		checkArgument(maxPoints > minPoints);
		this.maxPoints = maxPoints;
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
