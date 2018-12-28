package io.github.oliviercailloux.st_projects.ex3;

import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ANNOT;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.AUTO;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.COMPILE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.DO_GET;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.EXC;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.FINAL_NAME;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.GET_HELLO;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.GROUP_ID;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.JUNIT5_DEP;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.LOC;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.MTYPE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NO_MISLEADING_URL;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ONLY_ORIG;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ON_TIME;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.PREFIX;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.REPO_EXISTS;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.SOURCE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TEST_EXISTS;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.UTF;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.WAR;
import static io.github.oliviercailloux.st_projects.utils.Utils.ANY;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.ex2.GraderOrchestrator;
import io.github.oliviercailloux.st_projects.ex2.MavenManager;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitToMultipleSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.Graders;
import io.github.oliviercailloux.st_projects.services.grading.GradingException;
import io.github.oliviercailloux.st_projects.services.grading.GradingExecutor;
import io.github.oliviercailloux.st_projects.services.grading.PomContexter;
import io.github.oliviercailloux.st_projects.services.grading.TimeGrader;

public class Ex3GradingBuilder {

	public static void main(String[] args) throws Exception {
		final Ex3GradingBuilder grader = new Ex3GradingBuilder();
		grader.proceed();
		// orch.jsonToMyCourse();
		// orch.writeCsv();
	}

	private ContextInitializer contextInitializer;
	private Instant deadline;
	private Instant ignoreAfter;
	private MutableGraph<Object> g;

	public void proceed() throws Exception {
		final GraderOrchestrator orch = new GraderOrchestrator("servlet");
		orch.readUsernames();

		orch.readRepositories();
		orch.setSingleRepo("edoreld");

		final GradingExecutor executor = build();

		final ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositories = orch
				.getRepositoriesByStudentKnown();
		final ImmutableSet<StudentGrade> grades = executor.gradeAll(repositories);

		orch.writeCsv(grades);
		// writeJson();
	}

	public GradingExecutor build() {
		final GradingExecutor executor = new GradingExecutor();
		final Supplier<RepositoryCoordinates> initialSupplier = executor.getInitialSupplier();
		contextInitializer = ContextInitializer.ignoreAfter(initialSupplier, ignoreAfter);
		g.addNode(initialSupplier);

		final Double maxGrade = Stream.of(Ex3Criterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));
		final TimeGrader timeGrader = TimeGrader.given(ON_TIME, contextInitializer, deadline, maxGrade);
		putEdge(contextInitializer, timeGrader);
		final CriterionGrader repoGrader = GitGrader.repoGrader(REPO_EXISTS, contextInitializer);
		putEdge(contextInitializer, repoGrader);
		final GitToSourcer pomSupplier = new GitToSourcer(contextInitializer, Paths.get("pom.xml"));
		putEdge(contextInitializer, pomSupplier);
		final PomContexter pomContexter = new PomContexter(pomSupplier);
		putEdge(pomSupplier, pomContexter);
		putEdge(pomContexter, Graders.groupIdGrader(GROUP_ID, pomContexter));
		putEdge(pomSupplier,
				Graders.predicateGrader(JUNIT5_DEP, pomSupplier,
						Graders.containsOnce(Pattern.compile("<dependencies>" + ANY + "<dependency>" + ANY
								+ "<groupId>org\\.junit\\.jupiter</groupId>" + ANY
								+ "<artifactId>junit-jupiter-engine</artifactId>" + ANY + "<version>5\\.3\\.2</version>"
								+ ANY + "<scope>test</scope>"))));
		putEdge(pomSupplier,
				Graders.predicateGrader(UTF, pomSupplier,
						Graders.containsOnce(Pattern.compile("<properties>" + ANY
								+ "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>" + ANY
								+ "</properties>"))));
		putEdge(pomSupplier, Graders.predicateGrader(SOURCE, pomSupplier, Graders.containsOnce(Pattern.compile(
				"<properties>" + ANY + "<maven.compiler.source>.*</maven.compiler.source>" + ANY + "</properties>"))));
		putEdge(pomSupplier, Graders.predicateGrader(NO_MISLEADING_URL, pomSupplier,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate()));
		putEdge(pomSupplier, Graders.predicateGrader(WAR, pomSupplier,
				Graders.containsOnce(Pattern.compile("<packaging>war</packaging>"))));
		putEdge(contextInitializer, Graders.packageGroupIdGrader(PREFIX, contextInitializer, pomContexter));
		putEdge(pomContexter, Graders.packageGroupIdGrader(PREFIX, contextInitializer, pomContexter));
		putEdge(contextInitializer, () -> CriterionGrade.binary(COMPILE, new MavenManager()
				.compileWithoutTests(contextInitializer.getClient().getProjectDirectory().resolve("pom.xml"))));
		final GitToMultipleSourcer servletSourcer = new GitToMultipleSourcer(contextInitializer,
				Paths.get("src/main/java/"), (p) -> p.getFileName().equals(Paths.get("HelloServlet.java")));
		putEdge(contextInitializer, servletSourcer);
		putEdge(servletSourcer, Graders.predicateGraderWithComment(DO_GET, servletSourcer,
				Graders.containsOnce(Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))));
		putEdge(servletSourcer,
				Graders.predicateGrader(AUTO, servletSourcer, Predicates.contains(Pattern.compile("Auto-generated"))
						.negate().and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate())));
		putEdge(servletSourcer, Graders.predicateGrader(EXC, servletSourcer,
				Predicates.contains(Pattern.compile("printStackTrace")).negate()));
		putEdge(servletSourcer, Graders.predicateGrader(LOC, servletSourcer,
				Predicates.contains(Pattern.compile("setLocale.+FRENCH"))));
		putEdge(servletSourcer, Graders.predicateGrader(MTYPE, servletSourcer,
				Predicates.contains(Pattern.compile("setContentType.+PLAIN"))));
		putEdge(servletSourcer, Graders.predicateGrader(ANNOT, servletSourcer,
				Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/hello\".*\\)"))));
		putEdge(pomSupplier, Graders.predicateGrader(FINAL_NAME, pomSupplier, Graders
				.containsOnce(Pattern.compile("<build>" + ANY + "<finalName>myapp</finalName>" + ANY + "</build>"))));
		putEdge(contextInitializer, Graders.gradeOnlyOrig(ONLY_ORIG, contextInitializer));
		putEdge(servletSourcer, Graders.predicateGrader(GET_HELLO, servletSourcer,
				Predicates.contains(Pattern.compile("println(\"Hello world\")"))));

		/** Todo */
		final GitToMultipleSourcer testSourcer = new GitToMultipleSourcer(contextInitializer, Paths.get("src/"),
				(p) -> isTestFile(contextInitializer, p));
		putEdge(contextInitializer, testSourcer);
		putEdge(testSourcer, Graders.predicateGraderWithComment(TEST_EXISTS, testSourcer, Predicates.alwaysTrue()));

		executor.setGraph(g);
		return executor;
	}

	private void putEdge(GradingContexter contexter, GradingContexter dependentContexter) {
		g.putEdge(contexter, dependentContexter);
	}

	private boolean isTestFile(GitContext context, Path path) {
		try {
			return path.getFileName().toString().matches("Test.*\\.java")
					&& context.getClient().fetchBlobOrEmpty(path).contains("@Test");
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	private void putEdge(GradingContexter contexter, CriterionGrader criterionGrader) {
		g.putEdge(contexter, criterionGrader);
	}

	public Ex3GradingBuilder() {
		contextInitializer = null;
		deadline = ZonedDateTime.parse("2018-12-11T23:59:59+01:00").toInstant();
		ignoreAfter = Instant.MAX;
		g = GraphBuilder.directed().build();
	}
}
