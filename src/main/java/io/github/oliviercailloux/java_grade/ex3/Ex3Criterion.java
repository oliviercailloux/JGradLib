package io.github.oliviercailloux.java_grade.ex3;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.CriterionAndPoints;

public enum Ex3Criterion implements CriterionAndPoints {
	REPO_EXISTS("Repository exists", 0.5d, 0d), ON_TIME("Delivered on time", 0d, -30d),
	AT_ROOT("Project is at root of repository", 0d, -1d), GROUP_ID("Group id follows Maven best practices", 0d, -2d),
	JUNIT5_DEP("JUnit 5 (recent and correct) dependency", 1d, 0d), UTF("Encoding property", 0d, -1d),
	SOURCE("Maven compiler source property", 0d, -1d), NO_MISLEADING_URL("No misleading url pointer", 0d, -1d),
	WAR("war packaging", 0d, -2d), PREFIX("All classes are prefixed by the group id", 0d, -2d),
	COMPILE("mvn compile (without tests)", 1.5d, 0d), NO_JSP("No JSP files", 0d, -0.5d),
	NO_WEB_XML("No web.xml file", 0d, -0.5d), DO_GET("Method doGet in servlet", 1d, 0d),
	NO_DO_POST("No method doPost in servlet", 0d, -2d),
	NOT_POLLUTED("No noisy auto-generated code, comments or tests", 0d, -2d), EXC("Exceptions are not hidden", 0d, -2d),
	LOC("Locale set to English", 0.5d, 0d), MTYPE("Media type is PLAIN (using a symbolic constant)", 0.5d, 0d),
	ANNOT("Contains @WebServlet listening on 'hello'", 0.5d, 0d),
	FINAL_NAME("The final name is 'myapp', matching the requested context root", 1d, 0d),
	ONLY_ORIG("No derivative files in repo", 0d, -1d), GET_HELLO("Get request is answered with 'Hello world'", 1d, 0d),
	TEST_EXISTS("Unit test exists", 1d, 0d), TEST_LOCATION("Unit tests are located in test folder", 1d, 0d),
	TEST_GREEN("Unit test is green", 2.5d, 0d),
	ASSERT_EQUALS("Unit test uses the most appropriate JUnit 5 constructs and compares to sayHello()", 2d, 0d),
	TRAVIS_CONF("Travis .yaml configuration file exists", 1d, 0d), TRAVIS_OK("Travis configuration is correct", 3d, 0d),
	TRAVIS_BADGE("Correct badge in README.adoc", 2d, 0d);

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
