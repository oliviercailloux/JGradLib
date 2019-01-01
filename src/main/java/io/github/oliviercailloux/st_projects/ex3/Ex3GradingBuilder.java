package io.github.oliviercailloux.st_projects.ex3;

import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ANNOT;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ASSERT_EQUALS;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.AT_ROOT;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.COMPILE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.DO_GET;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.EXC;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.FINAL_NAME;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.GET_HELLO;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.GROUP_ID;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.JUNIT5_DEP;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.LOC;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.MTYPE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NOT_POLLUTED;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NO_DO_POST;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NO_JSP;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NO_MISLEADING_URL;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.NO_WEB_XML;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ONLY_ORIG;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.ON_TIME;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.PREFIX;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.REPO_EXISTS;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.SOURCE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TEST_EXISTS;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TEST_GREEN;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TEST_LOCATION;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TRAVIS_BADGE;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TRAVIS_CONF;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.TRAVIS_OK;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.UTF;
import static io.github.oliviercailloux.st_projects.ex3.Ex3Criterion.WAR;
import static io.github.oliviercailloux.st_projects.utils.Utils.ANY;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.mycourse.MyCourseCsvWriter;
import io.github.oliviercailloux.st_projects.GraderOrchestrator;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionGrader;
import io.github.oliviercailloux.st_projects.services.grading.FileCrawler;
import io.github.oliviercailloux.st_projects.services.grading.GitAndBaseToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitToMultipleSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToTestSourcer;
import io.github.oliviercailloux.st_projects.services.grading.Graders;
import io.github.oliviercailloux.st_projects.services.grading.GradingException;
import io.github.oliviercailloux.st_projects.services.grading.GradingExecutor;
import io.github.oliviercailloux.st_projects.services.grading.PomContexter;
import io.github.oliviercailloux.st_projects.services.grading.PomSupplier;
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
		final GraderOrchestrator orch = new GraderOrchestrator("ci");
		orch.readUsernames();

		orch.readRepositories();
//		orch.setSingleRepo("guillaumerg7");

		final GradingExecutor executor = build();
		final Comparator<Criterion> comparingEx3Criteria = Comparator.comparing((c) -> (Ex3Criterion) c,
				Comparator.naturalOrder());
		executor.setCriteriaComparator(Comparator.comparing(CriterionGrade::getCriterion, comparingEx3Criteria));

		final ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositories = orch
				.getRepositoriesByStudentKnown();
		final ImmutableSet<StudentGrade> grades = executor.gradeAll(repositories);
		orch.writeCsv(grades);
		new MyCourseCsvWriter().writeCsv("Devoir CI", 110774, grades);
	}

	public GradingExecutor build() {
		final GradingExecutor executor = new GradingExecutor();
		initialSupplier = executor.getInitialSupplier();
		contextInitializer = ContextInitializer.withPathAndIgnore(initialSupplier,
				Paths.get("/home/olivier/Professions/Enseignement/En cours/ci"), ignoreAfter);
		g.putEdge(initialSupplier, contextInitializer);

		final Double maxGrade = Stream.of(Ex3Criterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));
		putTaskWithDependencies(TimeGrader.given(ON_TIME, contextInitializer, deadline, maxGrade), contextInitializer);
		putTaskWithDependencies(GitGrader.repoGrader(REPO_EXISTS, contextInitializer), contextInitializer);
		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		final GitToMultipleSourcer multiPomSupplier = GitToMultipleSourcer.satisfyingPath(contextInitializer,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("pom.xml"));
		putTaskWithDependencies(multiPomSupplier, contextInitializer);
		final PomSupplier pomSupplier = PomSupplier.basedOn(multiPomSupplier);
		putTaskWithDependencies(() -> CriterionGrade.binary(AT_ROOT, pomSupplier.isProjectAtRoot()), multiPomSupplier);
		final PomContexter pomContexter = new PomContexter(pomSupplier);
		putTaskWithDependencies(pomContexter, multiPomSupplier);
		putTaskWithDependencies(Graders.groupIdGrader(GROUP_ID, pomContexter), pomContexter);
		putTaskWithDependencies(
				Graders.predicateGrader(JUNIT5_DEP, pomSupplier,
						Graders.containsOnce(Pattern.compile("<dependencies>" + ANY + "<dependency>" + ANY
								+ "<groupId>org\\.junit\\.jupiter</groupId>" + ANY
								+ "<artifactId>junit-jupiter-engine</artifactId>" + ANY + "<version>5\\.[23]\\." + ANY
								+ "</version>" + ANY + "<scope>test</scope>"))),
				multiPomSupplier);
		putTaskWithDependencies(Graders.predicateGrader(UTF, pomSupplier,
				Graders.containsOnce(Pattern.compile("<properties>" + ANY
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>" + ANY
						+ "</properties>"))),
				multiPomSupplier);
		putTaskWithDependencies(
				Graders.predicateGrader(SOURCE, pomSupplier, Graders.containsOnce(Pattern.compile("<properties>" + ANY
						+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + ANY + "</properties>"))),
				multiPomSupplier);
		putTaskWithDependencies(
				Graders.predicateGrader(NO_MISLEADING_URL, pomSupplier,
						Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate()),
				multiPomSupplier);
		putTaskWithDependencies(Graders.predicateGrader(WAR, pomSupplier,
				Graders.containsOnce(Pattern.compile("<packaging>war</packaging>"))), multiPomSupplier);
		putTaskWithDependencies(Graders.packageGroupIdGrader(PREFIX, contextInitializer, pomSupplier, pomContexter),
				contextInitializer, multiPomSupplier, pomContexter);
		putTaskWithDependencies(Graders.mavenCompileGrader(COMPILE, contextInitializer, pomSupplier),
				contextInitializer);
		final GitToMultipleSourcer servletSourcer = GitToMultipleSourcer.satisfyingPath(contextInitializer,
				Graders.startsWithPredicate(pomSupplier, Paths.get("src/main/java"))
						.and((p) -> p.getFileName().equals(Paths.get("HelloServlet.java"))));
		putTaskWithDependencies(servletSourcer, contextInitializer, multiPomSupplier);
		putTaskWithDependencies(() -> CriterionGrade.binary(NO_JSP, getNoJsp(contextInitializer)), contextInitializer);
		putTaskWithDependencies(() -> CriterionGrade.binary(NO_WEB_XML, getNoWebXml(contextInitializer)),
				contextInitializer);
		putTaskWithDependencies(
				Graders.predicateGrader(DO_GET, servletSourcer,
						Graders.containsOnce(
								Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))),
				servletSourcer);
		putTaskWithDependencies(Graders.predicateGrader(NO_DO_POST, servletSourcer,
				Graders.containsOnce(Pattern.compile("void\\s*doPost\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))
						.negate()),
				servletSourcer);
		final GitToTestSourcer testSourcer = GitToTestSourcer.testSourcer(contextInitializer);
		putTaskWithDependencies(testSourcer, contextInitializer);
		putTaskWithDependencies(Graders.predicateGrader(NOT_POLLUTED, servletSourcer,
				Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()
								.and((c) -> testSourcer.getContents().size() <= 1))),
				servletSourcer);
		putTaskWithDependencies(Graders.predicateGrader(EXC, servletSourcer,
				Predicates.contains(Pattern.compile("printStackTrace")).negate()), servletSourcer);
		putTaskWithDependencies(Graders.predicateGrader(LOC, servletSourcer,
				Predicates.contains(Pattern.compile("setLocale.+ENGLISH"))), servletSourcer);
		putTaskWithDependencies(Graders.predicateGrader(MTYPE, servletSourcer,
				Predicates.contains(Pattern.compile("setContentType.+PLAIN"))), servletSourcer);
		putTaskWithDependencies(Graders.predicateGrader(ANNOT, servletSourcer,
				Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/hello\".*\\)"))), servletSourcer);
		putTaskWithDependencies(
				Graders.predicateGrader(FINAL_NAME, pomSupplier,
						Graders.containsOnce(
								Pattern.compile("<build>" + ANY + "<finalName>myapp</finalName>" + ANY + "</build>"))),
				multiPomSupplier);
		putTaskWithDependencies(Graders.gradeOnlyOrig(ONLY_ORIG, contextInitializer), contextInitializer);
		putTaskWithDependencies(Graders.predicateGrader(GET_HELLO, servletSourcer,
				Predicates.contains(Pattern.compile("\"Hello,? world\\.?\""))), servletSourcer);
		putTaskWithDependencies(Graders.notEmpty(TEST_EXISTS, testSourcer), testSourcer);
		putTaskWithDependencies(
				() -> CriterionGrade.binary(TEST_LOCATION,
						testSourcer.getContents().keySet().stream()
								.allMatch(Graders.startsWithPredicate(pomSupplier, Paths.get("src/test/java")))),
				multiPomSupplier, testSourcer);
		putTaskWithDependencies(Graders.mavenTestGrader(TEST_GREEN, contextInitializer, testSourcer, pomSupplier),
				contextInitializer, testSourcer, multiPomSupplier);
		putTaskWithDependencies(Graders.predicateGraderAny(ASSERT_EQUALS, testSourcer, Predicates
				.contains(Pattern.compile("assertEquals")).and(Predicates.contains(Pattern.compile("sayHello()")))),
				testSourcer);
		final GitToSourcer travisSupplier = new GitToSourcer(contextInitializer, Paths.get(".travis.yml"));
		putTaskWithDependencies(travisSupplier, contextInitializer);
		putTaskWithDependencies(Graders.notEmpty(TRAVIS_CONF, travisSupplier), travisSupplier);
		final GitAndBaseToSourcer readmeSupplier = new GitAndBaseToSourcer(contextInitializer, pomSupplier,
				Paths.get("README.adoc"));
		putTaskWithDependencies(readmeSupplier, contextInitializer, multiPomSupplier);
		final CriterionGrader travisBadgeGrader = () -> CriterionGrade.binary(TRAVIS_BADGE,
				Predicates
						.contains(Pattern.compile("image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/"
								+ initialSupplier.get().getRepositoryName() + "\\.svg"))
						.apply(readmeSupplier.getContent()));
		putTaskWithDependencies(travisBadgeGrader, readmeSupplier);
		g.putEdge(initialSupplier, travisBadgeGrader);
		putTaskWithDependencies(getTravisConfGrader(travisSupplier), testSourcer);

		executor.setGraph(g);
		return executor;
	}

	private boolean getNoJsp(GitContext context) {
		try {
			final ImmutableSet<Path> paths = new FileCrawler(context.getClient()).getRecursively(Paths.get(""));
//			final GitToMultipleSourcer sourcer = GitToMultipleSourcer.satisfyingPath(context, Paths.get(""),
//					(p) -> p.getFileName().toString().endsWith(".jsp"));
//			sourcer.init();
//			return sourcer.getContents().isEmpty();
			final boolean anyMatch = paths.stream().anyMatch((p) -> p.getFileName().toString().endsWith(".jsp"));
			LOGGER.debug("Found as potential JSPs: {}, found: {}.", paths, anyMatch);
			return !anyMatch;
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex3GradingBuilder.class);
	private Supplier<RepositoryCoordinates> initialSupplier;

	private boolean getNoWebXml(GitContext context) {
		try {
			final ImmutableSet<Path> paths = new FileCrawler(context.getClient()).getRecursively(Paths.get(""));
			return !paths.stream().anyMatch((p) -> p.getFileName().toString().equals("web.xml"));
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	private CriterionGrader getTravisConfGrader(final GitToSourcer travisSupplier) {
		return () -> {
			final String travisConf = travisSupplier.getContent();
			if (travisConf.isEmpty()) {
				return CriterionGrade.min(TRAVIS_OK, "Configuration not found or incorrectly named.");
			}

			final Predicate<CharSequence> lang = Predicates.contains(Pattern.compile("language: java"));
			final Predicate<CharSequence> dist = Predicates.contains(Pattern.compile("dist: xenial"));
			final Predicate<CharSequence> script = Predicates.contains(Pattern.compile("script: "));
			final boolean hasLang = lang.apply(travisConf);
			final boolean hasDist = dist.apply(travisConf);
			final boolean hasScript = script.apply(travisConf);
			final double points;
			final String comment;
			assert TRAVIS_OK.getMinPoints() == 0d;
			if (!hasLang && !hasScript) {
				points = TRAVIS_OK.getMinPoints();
				comment = "Missing language.";
			} else if (!hasLang && hasScript && !hasDist) {
				points = TRAVIS_OK.getMaxPoints() / 3d;
				comment = "Missing language (script should be defaulted).";
			} else if (!hasLang && hasScript && hasDist) {
				points = TRAVIS_OK.getMinPoints();
				comment = "Missing language (script should be defaulted). Missing dist.";
			} else {
				assert hasLang;
				if (!hasDist && !hasScript) {
					points = TRAVIS_OK.getMaxPoints() * 2d / 3d;
					comment = "Missing dist.";
				} else if (!hasDist && hasScript) {
					points = TRAVIS_OK.getMaxPoints() / 3d;
					comment = "Missing dist. Inappropriate script, why not default?";
				} else if (hasDist && !hasScript) {
					points = TRAVIS_OK.getMaxPoints();
					comment = "";
				} else {
					assert hasDist && hasScript;
					points = TRAVIS_OK.getMaxPoints() / 2d;
					comment = "Inappropriate script, why not default?";
				}
			}
			return CriterionGrade.of(TRAVIS_OK, points, comment);
		};
	}

	private void putTaskWithDependencies(GradingContexter dependentContexter, GradingContexter... contexters) {
		g.addNode(dependentContexter);
		for (GradingContexter contexter : contexters) {
			g.putEdge(contexter, dependentContexter);
		}
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	private void putTaskWithDependencies(CriterionGrader criterionGrader, GradingContexter... contexters) {
//		g.nodes().stream().filter(Predicates.instanceOf(CriterionGrader.class)).map((n)->(CriterionGrader)n).map(CriterionGrader::)
		g.addNode(criterionGrader);
		for (GradingContexter contexter : contexters) {
			g.putEdge(contexter, criterionGrader);
		}
	}

	public Ex3GradingBuilder() {
		contextInitializer = null;
		deadline = ZonedDateTime.parse("2018-12-11T23:59:59+01:00").toInstant();
		ignoreAfter = ZonedDateTime.parse("2018-12-30T23:59:59+01:00").toInstant();
		g = GraphBuilder.directed().build();
		initialSupplier = null;
	}
}
