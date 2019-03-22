package io.github.oliviercailloux.java_grade.ex_jpa;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.grade.Criterion;

public enum ExJpaCriterion implements Criterion {
	REPO_EXISTS("Repository exists", 0.5d, 0d), ON_TIME("Delivered on time", 0d, -30d),
	AT_ROOT("Project is at root of repository", 0d, -0.5d),
	GROUP_ID("Group id follows Maven best practices", 0d, -0.5d), UTF("Encoding property", 0d, -0.5d),
	SOURCE("Maven compiler source property", 0d, -0.5d), NO_MISLEADING_URL("No misleading url pointer", 0d, -1d),
	WAR("war packaging", 0d, -0.5d), PREFIX("All classes are prefixed by the group id", 0d, -0.5d),
	COMPILE("mvn compile (without tests)", 1.5d, 0d), NO_JSP("No JSP files", 0d, -0.5d),
	NO_WEB_XML("No web.xml file", 0d, -0.5d), GET("Only method @GET in GetCommentsServlet", 0.5d),
	POST("Only method @POST in AddCommentServlet", 1d),
	NOT_POLLUTED("No noisy auto-generated code, comments or tests", 0d, -1d), EXC("Exceptions are not hidden", 0d, -2d),
	MTYPE("Servlets produce media type PLAIN (using a symbolic constant)", 0.5d),
	PATH_ANNOT("Servlets contain @Path listening on 'comments'", 0.5d),
	CDI_SCOPE("Servlets contain CDI scope annotation", 0.5d),
	JAX_RS_APP("Extends javax.ws.rs.core.Application with @ApplicationPath annotation", 0.5d),
	ONLY_ORIG("No derivative files in repo", 0d, -0.5d),
	PERSISTENCE("Persistence XML file is in the right place", 0.5d),
	PERSISTENCE_CONTENTS(
			"Persistence XML file declares a JTA persistence unit, drop-and-create schema, sets SQL debug variables, and does not define data source or JDBC properties",
			1d),
	GET_COMMENTS("GetCommentsServlet shows comments correctly (manual correction)", 2d),
	GET_EM("GetCommentsServlet injects an EntityManager", 1d),
	TRANSACTIONS("Servlets use container managed transactions correctly", 2d),
	ADD_COMMENTS("AddCommentServlet records comments correctly and answers ok (manual correction)", 2d),
	ADD_EM("AddCommentServlet injects an EntityManager", 1d),
	ADD_LIMIT("AddCommentServlet refuses content bigger than 2MB", 0.5d),
	TEST_EXISTS("Unit test provided and are in the right place", 0.5d),
	GENERAL_TEST(
			"Unit test uses appropriate JUnit (recent version of JUnit 5 or Arquillian) constructs and tests the right thing, and Maven tests pass (manual correction)",
			1.5d),
	TRAVIS_CONF("Travis .yaml configuration file exists", 0.5d), TRAVIS_OK("Travis configuration is correct", 1d),
	TRAVIS_BADGE("Correct Travis badge in README.adoc", 0.5d),
	IBM_MANIFEST("Manifest file for deployment to IBM Cloud is correct", 1d);

	private String requirement;
	private double maxPoints;
	private double minPoints;

	private ExJpaCriterion(String requirement, double maxPoints) {
		this(requirement, maxPoints, 0d);
	}

	private ExJpaCriterion(String requirement, double maxPoints, double minPoints) {
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
