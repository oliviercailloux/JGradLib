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
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.Mark;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionMarker;
import io.github.oliviercailloux.st_projects.services.grading.FileCrawler;
import io.github.oliviercailloux.st_projects.services.grading.GitAndBaseToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitMarker;
import io.github.oliviercailloux.st_projects.services.grading.GitToMultipleSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToTestSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GradingException;
import io.github.oliviercailloux.st_projects.services.grading.GradingExecutor;
import io.github.oliviercailloux.st_projects.services.grading.Markers;
import io.github.oliviercailloux.st_projects.services.grading.PomContexter;
import io.github.oliviercailloux.st_projects.services.grading.PomSupplier;
import io.github.oliviercailloux.st_projects.services.grading.TimeMarker;

public class Ex3GradingBuilder {

	private ContextInitializer contextInitializer;
	private Instant deadline;
	private Instant ignoreAfter;
	private MutableGraph<Object> g;

	public GradingExecutor build() {
		final GradingExecutor executor = new GradingExecutor();
		initialSupplier = executor.getInitialSupplier();
		contextInitializer = ContextInitializer.withPathAndIgnore(initialSupplier,
				Paths.get("/home/olivier/Professions/Enseignement/En cours/ci"), ignoreAfter);
		g.putEdge(initialSupplier, contextInitializer);

		final Double maxGrade = Stream.of(Ex3Criterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));
		putTaskWithDependencies(TimeMarker.given(ON_TIME, contextInitializer, deadline, maxGrade), contextInitializer);
		putTaskWithDependencies(GitMarker.repoMarker(REPO_EXISTS, contextInitializer), contextInitializer);
		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		final GitToMultipleSourcer multiPomSupplier = GitToMultipleSourcer.satisfyingPath(contextInitializer,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("pom.xml"));
		putTaskWithDependencies(multiPomSupplier, contextInitializer);
		final PomSupplier pomSupplier = PomSupplier.basedOn(multiPomSupplier);
		putTaskWithDependencies(() -> Mark.binary(AT_ROOT, pomSupplier.isProjectAtRoot()), multiPomSupplier);
		final PomContexter pomContexter = new PomContexter(pomSupplier);
		putTaskWithDependencies(pomContexter, multiPomSupplier);
		putTaskWithDependencies(Markers.groupIdMarker(GROUP_ID, pomContexter), pomContexter);
		putTaskWithDependencies(
				Markers.predicateMarker(JUNIT5_DEP, pomSupplier,
						Markers.containsOnce(Pattern.compile("<dependencies>" + ANY + "<dependency>" + ANY
								+ "<groupId>org\\.junit\\.jupiter</groupId>" + ANY
								+ "<artifactId>junit-jupiter-engine</artifactId>" + ANY + "<version>5\\.[23]\\." + ANY
								+ "</version>" + ANY + "<scope>test</scope>"))),
				multiPomSupplier);
		putTaskWithDependencies(Markers.predicateMarker(UTF, pomSupplier,
				Markers.containsOnce(Pattern.compile("<properties>" + ANY
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>" + ANY
						+ "</properties>"))),
				multiPomSupplier);
		putTaskWithDependencies(
				Markers.predicateMarker(SOURCE, pomSupplier, Markers.containsOnce(Pattern.compile("<properties>" + ANY
						+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + ANY + "</properties>"))),
				multiPomSupplier);
		putTaskWithDependencies(
				Markers.predicateMarker(NO_MISLEADING_URL, pomSupplier,
						Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate()),
				multiPomSupplier);
		putTaskWithDependencies(Markers.predicateMarker(WAR, pomSupplier,
				Markers.containsOnce(Pattern.compile("<packaging>war</packaging>"))), multiPomSupplier);
		putTaskWithDependencies(Markers.packageGroupIdMarker(PREFIX, contextInitializer, pomSupplier, pomContexter),
				contextInitializer, multiPomSupplier, pomContexter);
		putTaskWithDependencies(Markers.mavenCompileMarker(COMPILE, contextInitializer, pomSupplier),
				contextInitializer);
		final GitToMultipleSourcer servletSourcer = GitToMultipleSourcer.satisfyingPath(contextInitializer,
				Markers.startsWithPredicate(pomSupplier, Paths.get("src/main/java"))
						.and((p) -> p.getFileName().equals(Paths.get("HelloServlet.java"))));
		putTaskWithDependencies(servletSourcer, contextInitializer, multiPomSupplier);
		putTaskWithDependencies(() -> Mark.binary(NO_JSP, getNoJsp(contextInitializer)), contextInitializer);
		putTaskWithDependencies(() -> Mark.binary(NO_WEB_XML, getNoWebXml(contextInitializer)), contextInitializer);
		putTaskWithDependencies(
				Markers.predicateMarker(DO_GET, servletSourcer,
						Markers.containsOnce(
								Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))),
				servletSourcer);
		putTaskWithDependencies(Markers.predicateMarker(NO_DO_POST, servletSourcer,
				Markers.containsOnce(Pattern.compile("void\\s*doPost\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))
						.negate()),
				servletSourcer);
		final GitToTestSourcer testSourcer = GitToTestSourcer.testSourcer(contextInitializer);
		putTaskWithDependencies(testSourcer, contextInitializer);
		putTaskWithDependencies(Markers.predicateMarker(NOT_POLLUTED, servletSourcer,
				Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()
								.and((c) -> testSourcer.getContents().size() <= 1))),
				servletSourcer);
		putTaskWithDependencies(Markers.predicateMarker(EXC, servletSourcer,
				Predicates.contains(Pattern.compile("printStackTrace")).negate()), servletSourcer);
		putTaskWithDependencies(Markers.predicateMarker(LOC, servletSourcer,
				Predicates.contains(Pattern.compile("setLocale.+ENGLISH"))), servletSourcer);
		putTaskWithDependencies(Markers.predicateMarker(MTYPE, servletSourcer,
				Predicates.contains(Pattern.compile("setContentType.+PLAIN"))), servletSourcer);
		putTaskWithDependencies(Markers.predicateMarker(ANNOT, servletSourcer,
				Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/hello\".*\\)"))), servletSourcer);
		putTaskWithDependencies(
				Markers.predicateMarker(FINAL_NAME, pomSupplier,
						Markers.containsOnce(
								Pattern.compile("<build>" + ANY + "<finalName>myapp</finalName>" + ANY + "</build>"))),
				multiPomSupplier);
		putTaskWithDependencies(Markers.gradeOnlyOrig(ONLY_ORIG, contextInitializer), contextInitializer);
		putTaskWithDependencies(Markers.predicateMarker(GET_HELLO, servletSourcer,
				Predicates.contains(Pattern.compile("\"Hello,? world\\.?\""))), servletSourcer);
		putTaskWithDependencies(Markers.notEmpty(TEST_EXISTS, testSourcer), testSourcer);
		putTaskWithDependencies(
				() -> Mark.binary(TEST_LOCATION,
						testSourcer.getContents().keySet().stream()
								.allMatch(Markers.startsWithPredicate(pomSupplier, Paths.get("src/test/java")))),
				multiPomSupplier, testSourcer);
		putTaskWithDependencies(Markers.mavenTestMarker(TEST_GREEN, contextInitializer, testSourcer, pomSupplier),
				contextInitializer, testSourcer, multiPomSupplier);
		putTaskWithDependencies(Markers.predicateMarkerAny(ASSERT_EQUALS, testSourcer, Predicates
				.contains(Pattern.compile("assertEquals")).and(Predicates.contains(Pattern.compile("sayHello()")))),
				testSourcer);
		final GitToSourcer travisSupplier = new GitToSourcer(contextInitializer, Paths.get(".travis.yml"));
		putTaskWithDependencies(travisSupplier, contextInitializer);
		putTaskWithDependencies(Markers.notEmpty(TRAVIS_CONF, travisSupplier), travisSupplier);
		final GitAndBaseToSourcer readmeSupplier = new GitAndBaseToSourcer(contextInitializer, pomSupplier,
				Paths.get("README.adoc"));
		putTaskWithDependencies(readmeSupplier, contextInitializer, multiPomSupplier);
		final CriterionMarker travisBadgeMarker = () -> Mark.binary(TRAVIS_BADGE,
				Predicates
						.contains(Pattern.compile("image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/"
								+ initialSupplier.get().getRepositoryName() + "\\.svg"))
						.apply(readmeSupplier.getContent()));
		putTaskWithDependencies(travisBadgeMarker, readmeSupplier);
		g.putEdge(initialSupplier, travisBadgeMarker);
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

	private CriterionMarker getTravisConfGrader(final GitToSourcer travisSupplier) {
		return () -> {
			final String travisConf = travisSupplier.getContent();
			if (travisConf.isEmpty()) {
				return Mark.min(TRAVIS_OK, "Configuration not found or incorrectly named.");
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
			return Mark.of(TRAVIS_OK, points, comment);
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

	private void putTaskWithDependencies(CriterionMarker criterionGrader, GradingContexter... contexters) {
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
