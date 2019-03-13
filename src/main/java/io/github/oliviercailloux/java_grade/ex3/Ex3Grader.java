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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.MultiContent;
import io.github.oliviercailloux.grade.contexters.ContextInitializer;
import io.github.oliviercailloux.grade.contexters.GitAndBaseToSourcer;
import io.github.oliviercailloux.grade.contexters.GitToMultipleSourcer;
import io.github.oliviercailloux.grade.contexters.GitToTestSourcer;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.markers.JavaEEMarkers;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
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

		gradesBuilder.add(Marks.timeMark(ON_TIME, fullContext, deadline, maxGrade));
		gradesBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		final MultiContent multiPom = GitToMultipleSourcer.satisfyingPath(fullContext,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("pom.xml"));
		final PomSupplier pomSupplier = PomSupplier.basedOn(multiPom);
		final Path projectRelativeRoot = pomSupplier.getMavenRelativeRoot().orElse(Paths.get(""));
		final String pomContent = pomSupplier.getContent();

		gradesBuilder.add(Mark.binary(AT_ROOT, pomSupplier.isMavenProjectAtRoot()));

		final PomContexter pomContexter = new PomContexter(pomContent);
		pomContexter.init();

		gradesBuilder.add(Mark.binary(GROUP_ID, pomContexter.isGroupIdValid()));
		gradesBuilder.add(Mark.binary(JUNIT5_DEP,
				MarkingPredicates.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
						+ Utils.ANY_REG_EXP + "<groupId>org\\.junit\\.jupiter</groupId>" + Utils.ANY_REG_EXP
						+ "<artifactId>junit-jupiter-engine</artifactId>" + Utils.ANY_REG_EXP + "<version>5\\.[23]\\."
						+ Utils.ANY_REG_EXP + "</version>" + Utils.ANY_REG_EXP + "<scope>test</scope>"))
						.test(pomContent)));
		gradesBuilder.add(Mark.binary(UTF,
				MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
						+ Utils.ANY_REG_EXP + "</properties>")).test(pomContent)));
		gradesBuilder
				.add(Mark.binary(SOURCE,
						MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + Utils.ANY_REG_EXP
								+ "</properties>")).test(pomContent)));
		gradesBuilder.add(Mark.binary(NO_MISLEADING_URL,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate().test(pomContent)));
		gradesBuilder.add(Mark.binary(WAR,
				MarkingPredicates.containsOnce(Pattern.compile("<packaging>war</packaging>")).test(pomContent)));
		gradesBuilder.add(Marks.packageGroupId(PREFIX, fullContext, pomSupplier, pomContexter));
		gradesBuilder.add(Marks.mavenCompile(COMPILE, fullContext, pomSupplier));

		final MultiContent servletSourcer = GitToMultipleSourcer.satisfyingPath(fullContext,
				MarkingPredicates
						.startsWithPathRelativeTo(pomSupplier.getMavenRelativeRoot(), Paths.get("src/main/java"))
						.and((p) -> p.getFileName().equals(Paths.get("HelloServlet.java"))));

		gradesBuilder.add(Mark.binary(NO_JSP, JavaEEMarkers.getNoJsp(fullContext)));
		gradesBuilder.add(Mark.binary(NO_WEB_XML, JavaEEMarkers.getNoWebXml(fullContext)));
		gradesBuilder.add(Mark.binary(DO_GET, servletSourcer.existsAndAllMatch(MarkingPredicates
				.containsOnce(Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)")))));
		gradesBuilder.add(Mark.binary(NO_DO_POST,
				servletSourcer.existsAndAllMatch(MarkingPredicates
						.containsOnce(Pattern.compile("void\\s*doPost\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)"))
						.negate())));
		final GitToTestSourcer testSourcer = GitToTestSourcer.testSourcer(fullContext);
		gradesBuilder.add(Mark.binary(NOT_POLLUTED,
				servletSourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()
								.and((c) -> testSourcer.getContents().size() <= 1)))));
		gradesBuilder.add(Mark.binary(EXC,
				servletSourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("printStackTrace")).negate())));
		gradesBuilder.add(Mark.binary(LOC,
				servletSourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("setLocale.+ENGLISH")))));
		gradesBuilder.add(Mark.binary(MTYPE,
				servletSourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("setContentType.+PLAIN")))));
		gradesBuilder.add(Mark.binary(ANNOT, servletSourcer
				.existsAndAllMatch(Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/hello\".*\\)")))));
		gradesBuilder
				.add(Mark.binary(FINAL_NAME,
						MarkingPredicates
								.containsOnce(Pattern.compile("<build>" + Utils.ANY_REG_EXP
										+ "<finalName>myapp</finalName>" + Utils.ANY_REG_EXP + "</build>"))
								.test(pomContent)));
		gradesBuilder.add(Marks.noDerivedFiles(ONLY_ORIG, fullContext));
		gradesBuilder.add(Mark.binary(GET_HELLO,
				servletSourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("\"Hello,? world\\.?\"")))));
		gradesBuilder.add(Marks.notEmpty(TEST_EXISTS, testSourcer));
		gradesBuilder
				.add(Mark.binary(TEST_LOCATION, testSourcer.getContents().keySet().stream().allMatch(MarkingPredicates
						.startsWithPathRelativeTo(pomSupplier.getMavenRelativeRoot(), Paths.get("src/test/java")))));
		gradesBuilder.add(Marks.mavenTest(TEST_GREEN, fullContext, testSourcer, pomSupplier));
		gradesBuilder.add(Mark.binary(ASSERT_EQUALS, testSourcer.anyMatch(Predicates
				.contains(Pattern.compile("assertEquals")).and(Predicates.contains(Pattern.compile("sayHello()"))))));
		final String travisContent = fullContext.getMainCommitFilesReader().getContent(Paths.get(".travis.yml"));
		gradesBuilder.add(Mark.binary(TRAVIS_CONF, !travisContent.isEmpty()));
		final String readmeContent = GitAndBaseToSourcer.given(fullContext, projectRelativeRoot.resolve("README.adoc"));
		gradesBuilder.add(Mark.binary(TRAVIS_BADGE, Pattern.compile(
				"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + coord.getRepositoryName() + "\\.svg")
				.matcher(readmeContent).find()));
		gradesBuilder.add(Marks.travisConfMark(TRAVIS_OK, travisContent));

		return gradesBuilder.build();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex3Grader.class);

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
