package io.github.oliviercailloux.st_projects.ex2;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public enum Ex2Criterion implements GradeCriterion {
	REPO_EXISTS("Repository exists", 0.5d), ON_TIME("Delivered on time", 0),
	GROUP_ID("Group id follows Maven best practices", 0.5d), ICAL("Ical dependency", 1d),
	UTF("Encoding property", 0.5d), SOURCE("Maven compiler source property", 0.5d),
	NO_MISLEADING_URL("No misleading url pointer", 0d), WAR("war packaging", 0.5d),
	PREFIX("All classes are prefixed by the group id", 1d), COMPILE("mvn compile", 1d),
	DO_GET("Method doGet in servlet", 0.5d), SRC_ENC("Source code is UTF-8 encoded and error message is correct", 0.5d),
	AUTO("No noisy auto-generated code", 0d), EXC("Exceptions are not hidden", 1d), ENC("Encoding set to UTF-8", 1d),
	LOC("Locale set to French", 1d), MTYPE("Media type is PLAIN", 0.5d), ANNOT("Contains @WebServlet on add", 1d),
	FINAL_NAME("The final name is 'additioner', matching the requested context root", 1d),
	ERROR_STATUS("The servlet sends an error status (using a symbolic constant)", 1d), PARAM("Calls getParameter", 1d),
	// IGNORE("git ignore derivative files", 0.5d),
	ONLY_ORIG("No derivative files in repo", 1d),
	GET_SIMPLE("Get request is treated properly, in absence of default (non-automated)", 1d),
	DEFAULT_PARAM("Both requests are treated properly, when use of default param2 (non-automated)", 4d);

	private String requirement;
	private double maxPoints;

	private Ex2Criterion(String requirement, double maxPoints) {
		this.requirement = requireNonNull(requirement);
		checkArgument(Double.isFinite(maxPoints));
		checkArgument(maxPoints >= 0);
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
}
