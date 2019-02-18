package io.github.oliviercailloux.java_grade.ex3;

import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.ANNOT;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.ASSERT_EQUALS;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.AT_ROOT;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.COMPILE;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.DO_GET;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.EXC;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.FINAL_NAME;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.GET_HELLO;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.GROUP_ID;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.JUNIT5_DEP;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.LOC;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.MTYPE;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.NOT_POLLUTED;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.NO_DO_POST;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.NO_JSP;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.NO_MISLEADING_URL;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.NO_WEB_XML;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.ONLY_ORIG;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.PREFIX;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.REPO_EXISTS;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.SOURCE;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TEST_EXISTS;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TEST_GREEN;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TEST_LOCATION;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TRAVIS_BADGE;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TRAVIS_CONF;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.TRAVIS_OK;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.UTF;
import static io.github.oliviercailloux.java_grade.ex3.Ex3Criterion.WAR;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.MultiContent;
import io.github.oliviercailloux.grade.contexters.ContextInitializer;
import io.github.oliviercailloux.grade.contexters.FileCrawler;
import io.github.oliviercailloux.grade.contexters.GitAndBaseToSourcer;
import io.github.oliviercailloux.grade.contexters.GitToMultipleSourcer;
import io.github.oliviercailloux.grade.contexters.GitToSourcer;
import io.github.oliviercailloux.grade.contexters.GitToTestSourcer;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.markers.GitMarker;
import io.github.oliviercailloux.grade.markers.Markers;
import io.github.oliviercailloux.grade.markers.TimeMarker;
import io.github.oliviercailloux.utils.Utils;

public class Ex3Grader {

	private Instant deadline;
	private Instant ignoreAfter;

	public ImmutableSet<Mark> grade(RepositoryCoordinates coord) {
		final ImmutableSet.Builder<Mark> gradesBuilder = ImmutableSet.builder();

		final GitFullContext fullContext = ContextInitializer.withPathAndIgnoreAndInit(coord,
				Paths.get("/home/olivier/Professions/Enseignement/En cours/ci"), ignoreAfter);
		final double maxGrade = Stream.of(Ex3Criterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));

		gradesBuilder.add(TimeMarker.given(ON_TIME, fullContext, deadline, maxGrade));
		gradesBuilder.add(GitMarker.repoMark(REPO_EXISTS, fullContext));

		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		final MultiContent multiPom = GitToMultipleSourcer.satisfyingPathAndInit(fullContext,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("pom.xml"));
		final PomSupplier pomSupplier = PomSupplier.basedOn(multiPom);
		final Path projectRelativeRoot = pomSupplier.getProjectRelativeRoot().orElse(Paths.get(""));
		final String pomContent = pomSupplier.getContent();

		gradesBuilder.add(Mark.binary(AT_ROOT, pomSupplier.isProjectAtRoot()));

		final PomContexter pomContexter = new PomContexter(pomContent);
		pomContexter.init();

		gradesBuilder.add(Markers.groupIdMark(GROUP_ID, pomContexter));
		gradesBuilder.add(Markers.predicateMark(JUNIT5_DEP, pomContent,
				Markers.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
						+ Utils.ANY_REG_EXP + "<groupId>org\\.junit\\.jupiter</groupId>" + Utils.ANY_REG_EXP
						+ "<artifactId>junit-jupiter-engine</artifactId>" + Utils.ANY_REG_EXP + "<version>5\\.[23]\\."
						+ Utils.ANY_REG_EXP + "</version>" + Utils.ANY_REG_EXP + "<scope>test</scope>"))));
		gradesBuilder.add(Markers.predicateMark(UTF, pomContent,
				Markers.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
						+ Utils.ANY_REG_EXP + "</properties>"))));
		gradesBuilder
				.add(Markers.predicateMark(SOURCE, pomContent,
						Markers.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + Utils.ANY_REG_EXP
								+ "</properties>"))));
		gradesBuilder.add(Markers.predicateMark(NO_MISLEADING_URL, pomContent,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate()));
		gradesBuilder.add(Markers.predicateMark(WAR, pomContent,
				Markers.containsOnce(Pattern.compile("<packaging>war</packaging>"))));
		gradesBuilder.add(Markers.packageGroupIdMark(PREFIX, fullContext, pomSupplier, pomContexter));
		gradesBuilder.add(Markers.mavenCompileMark(COMPILE, fullContext, pomSupplier));

		final MultiContent servletSourcer = GitToMultipleSourcer.satisfyingPathAndInit(fullContext,
				Markers.startsWithPredicate(pomSupplier, Paths.get("src/main/java"))
						.and((p) -> p.getFileName().equals(Paths.get("HelloServlet.java"))));

		gradesBuilder.add(Mark.binary(NO_JSP, getNoJsp(fullContext)));
		gradesBuilder.add(Mark.binary(NO_WEB_XML, getNoWebXml(fullContext)));
		gradesBuilder.add(Markers.predicateMark(DO_GET, servletSourcer,
				Markers.containsOnce(Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))));
		gradesBuilder.add(Markers.predicateMark(NO_DO_POST, servletSourcer,
				Markers.containsOnce(Pattern.compile("void\\s*doPost\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))
						.negate()));
		final GitToTestSourcer testSourcer = GitToTestSourcer.testSourcer(fullContext);
		gradesBuilder.add(Markers.predicateMark(NOT_POLLUTED, servletSourcer,
				Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()
								.and((c) -> testSourcer.getContents().size() <= 1))));
		gradesBuilder.add(Markers.predicateMark(EXC, servletSourcer,
				Predicates.contains(Pattern.compile("printStackTrace")).negate()));
		gradesBuilder.add(
				Markers.predicateMark(LOC, servletSourcer, Predicates.contains(Pattern.compile("setLocale.+ENGLISH"))));
		gradesBuilder.add(Markers.predicateMark(MTYPE, servletSourcer,
				Predicates.contains(Pattern.compile("setContentType.+PLAIN"))));
		gradesBuilder.add(Markers.predicateMark(ANNOT, servletSourcer,
				Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/hello\".*\\)"))));
		gradesBuilder.add(Markers.predicateMark(FINAL_NAME, pomContent, Markers.containsOnce(Pattern.compile(
				"<build>" + Utils.ANY_REG_EXP + "<finalName>myapp</finalName>" + Utils.ANY_REG_EXP + "</build>"))));
		gradesBuilder.add(Markers.gradeOnlyOrig(ONLY_ORIG, fullContext));
		gradesBuilder.add(Markers.predicateMark(GET_HELLO, servletSourcer,
				Predicates.contains(Pattern.compile("\"Hello,? world\\.?\""))));
		gradesBuilder.add(Markers.notEmpty(TEST_EXISTS, testSourcer));
		gradesBuilder.add(Mark.binary(TEST_LOCATION, testSourcer.getContents().keySet().stream()
				.allMatch(Markers.startsWithPredicate(pomSupplier, Paths.get("src/test/java")))));
		gradesBuilder.add(Markers.mavenTestMark(TEST_GREEN, fullContext, testSourcer, pomSupplier));
		gradesBuilder.add(Markers.predicateMarkAny(ASSERT_EQUALS, testSourcer, Predicates
				.contains(Pattern.compile("assertEquals")).and(Predicates.contains(Pattern.compile("sayHello()")))));
		final String travisContent = GitToSourcer.given(fullContext, Paths.get(".travis.yml"));
		gradesBuilder.add(Mark.binary(TRAVIS_CONF, !travisContent.isEmpty()));
		final String readmeContent = GitAndBaseToSourcer.given(fullContext, projectRelativeRoot,
				Paths.get("README.adoc"));
		gradesBuilder.add(Mark.binary(TRAVIS_BADGE, Pattern.compile(
				"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + coord.getRepositoryName() + "\\.svg")
				.matcher(readmeContent).find()));
		gradesBuilder.add(getTravisConfGrade(travisContent));

		return gradesBuilder.build();
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
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex3Grader.class);

	private boolean getNoWebXml(GitContext context) {
		try {
			final ImmutableSet<Path> paths = new FileCrawler(context.getClient()).getRecursively(Paths.get(""));
			return !paths.stream().anyMatch((p) -> p.getFileName().toString().equals("web.xml"));
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	private Mark getTravisConfGrade(String travisContent) {
		if (travisContent.isEmpty()) {
			return Mark.min(TRAVIS_OK, "Configuration not found or incorrectly named.");
		}

		final Predicate<CharSequence> lang = Predicates.contains(Pattern.compile("language: java"));
		final Predicate<CharSequence> dist = Predicates.contains(Pattern.compile("dist: xenial"));
		final Predicate<CharSequence> script = Predicates.contains(Pattern.compile("script: "));
		final boolean hasLang = lang.apply(travisContent);
		final boolean hasDist = dist.apply(travisContent);
		final boolean hasScript = script.apply(travisContent);
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
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	public Ex3Grader() {
		deadline = ZonedDateTime.parse("2018-12-11T23:59:59+01:00").toInstant();
		ignoreAfter = ZonedDateTime.parse("2018-12-30T23:59:59+01:00").toInstant();
	}
}
