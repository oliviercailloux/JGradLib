package io.github.oliviercailloux.java_grade.ex_jpa;

import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_EM;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_LIMIT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ASSERT_EQUALS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.AT_ROOT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.CDI_SCOPE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.COMPILE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.EXC;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET_EM;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GROUP_ID;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.IBM_MANIFEST;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.JAX_RS_APP;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.JUNIT5_DEP;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.MTYPE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.NO_JSP;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.NO_MISLEADING_URL;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.NO_WEB_XML;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ONLY_ORIG;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.PATH_ANNOT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.PERSISTENCE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.PERSISTENCE_CONTENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.POST;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.PREFIX;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.REPO_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.SOURCE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.TEST_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.TRANSACTIONS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.TRAVIS_BADGE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.TRAVIS_CONF;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.TRAVIS_OK;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.UTF;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.WAR;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

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

public class ExJpaGrader {

	private Instant deadline;
	private Instant ignoreAfter;

	public ImmutableSet<Mark> grade(RepositoryCoordinates coord) {
		final ImmutableSet.Builder<Mark> gradeBuilder = ImmutableSet.builder();

		final GitFullContext fullContext = ContextInitializer.withPathAndIgnoreAndInit(coord,
				Paths.get("/home/olivier/Professions/Enseignement/En cours/jpa"), ignoreAfter);
		final double maxGrade = Stream.of(ExJpaCriterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));

		gradeBuilder.add(Marks.timeMark(ON_TIME, fullContext, deadline, maxGrade));
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final PomSupplier pomSupplier = PomSupplier.basedOn(fullContext);
		final Path mavenRelativeRoot = pomSupplier.getForcedMavenRelativeRoot();
		final String pomContent = pomSupplier.getContent();

		gradeBuilder.add(Mark.binary(AT_ROOT, pomSupplier.isMavenProjectAtRoot()));

		final PomContexter pomContexter = new PomContexter(pomContent);
		pomContexter.init();

		gradeBuilder.add(Mark.binary(GROUP_ID, pomContexter.isGroupIdValid()));
		gradeBuilder.add(Mark.binary(JUNIT5_DEP,
				MarkingPredicates.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
						+ Utils.ANY_REG_EXP + "<groupId>org\\.junit\\.jupiter</groupId>" + Utils.ANY_REG_EXP
						+ "<artifactId>junit-jupiter-engine</artifactId>" + Utils.ANY_REG_EXP + "<version>5\\.[234]\\."
						+ Utils.ANY_REG_EXP + "</version>" + Utils.ANY_REG_EXP + "<scope>test</scope>"))
						.test(pomContent)));
		gradeBuilder.add(Mark.binary(UTF,
				MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
						+ Utils.ANY_REG_EXP + "</properties>")).test(pomContent)));
		gradeBuilder
				.add(Mark.binary(SOURCE,
						MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + Utils.ANY_REG_EXP
								+ "</properties>")).test(pomContent)));
		gradeBuilder.add(Mark.binary(NO_MISLEADING_URL,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate().test(pomContent)));
		gradeBuilder.add(Mark.binary(WAR,
				MarkingPredicates.containsOnce(Pattern.compile("<packaging>war</packaging>")).test(pomContent)));
		gradeBuilder.add(Marks.packageGroupId(PREFIX, fullContext, pomSupplier, pomContexter));
		gradeBuilder.add(Marks.mavenCompile(COMPILE, fullContext, pomSupplier));

		final MultiContent anySourcer = GitToMultipleSourcer.satisfyingPath(fullContext,
				(p) -> p.startsWith(pomSupplier.getSrcFolder()));
		final Predicate<Path> srcMainJavaPredicate = (p) -> p.startsWith(pomSupplier.getSrcMainJavaFolder());
		final MultiContent getServletSourcer = GitToMultipleSourcer.satisfyingPath(fullContext,
				srcMainJavaPredicate.and((p) -> p.getFileName().equals(Paths.get("GetCommentsServlet.java"))));
		final MultiContent addServletSourcer = GitToMultipleSourcer.satisfyingPath(fullContext,
				srcMainJavaPredicate.and((p) -> p.getFileName().equals(Paths.get("AddCommentServlet.java"))));

		gradeBuilder.add(Mark.binary(NO_JSP, JavaEEMarkers.getNoJsp(fullContext)));
		gradeBuilder.add(Mark.binary(NO_WEB_XML, JavaEEMarkers.getNoWebXml(fullContext)));
		final Predicate<CharSequence> containsGet = Predicates.containsPattern("@GET");
		final Predicate<CharSequence> containsNoGet = containsGet.negate();
		final Predicate<CharSequence> containsNoPut = Predicates.containsPattern("@PUT").negate();
		final Predicate<CharSequence> containsPost = Predicates.containsPattern("@POST");
		final Predicate<CharSequence> containsNoPost = containsPost.negate();
		gradeBuilder.add(Mark.binary(GET,
				getServletSourcer.existsAndAllMatch(containsGet.and(containsNoPut).and(containsNoPost))));
		gradeBuilder.add(Mark.binary(POST,
				addServletSourcer.existsAndAllMatch(containsNoGet.and(containsNoPut).and(containsPost))));

		gradeBuilder
				.add(Mark.binary(EXC, anySourcer.noneMatch(Predicates.contains(Pattern.compile("printStackTrace")))));

		final ImmutableList<MultiContent> servletSourcers = ImmutableList.of(getServletSourcer, addServletSourcer);
		gradeBuilder.add(Mark.proportional(MTYPE,
				(int) servletSourcers.stream().filter(
						(mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@Produces(MediaType.TEXT_PLAIN)")))
						.count(),
				servletSourcers.size()));
		gradeBuilder.add(Mark.proportional(PATH_ANNOT,
				(int) servletSourcers.stream().filter(
						(mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@Path.*\\\\(.*comments\\\".*\\\\)")))
						.count(),
				servletSourcers.size()));
		gradeBuilder.add(Mark.proportional(CDI_SCOPE,
				(int) servletSourcers.stream()
						.filter((mc) -> mc.existsAndAllMatch(Predicates.containsPattern("\"@.*Scoped"))).count(),
				servletSourcers.size()));

		final MultiContent applicationClasses = GitToMultipleSourcer.satisfyingOnContent(fullContext,
				(f) -> srcMainJavaPredicate.test(f.getPath()) && f.getContent().contains("@ApplicationPath")
						&& f.getContent().contains("extends.* Application"));
		gradeBuilder.add(Mark.binary(JAX_RS_APP, applicationClasses.getContents().size() == 1));
		gradeBuilder.add(Marks.noDerivedFiles(ONLY_ORIG, fullContext));

		final MultiContent multiPersistence = GitToMultipleSourcer.satisfyingPath(fullContext,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("persistence.xml"));
		gradeBuilder.add(Mark.binary(PERSISTENCE, multiPersistence.getContents()
				.containsKey(mavenRelativeRoot.resolve(Paths.get("src/main/resources/persistence.xml")))));

		gradeBuilder.add(Mark.proportional(PERSISTENCE_CONTENTS,
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("persistence version=\"2.1\"")),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("RESOURCE_LOCAL")),
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("drop-and-create")),
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("hibernate.show_sql")
						.or(Predicates.containsPattern("eclipselink.logging.level"))),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jta-data-source")),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jdbc.driver"))));

		gradeBuilder.add(Mark.binary(GET_EM, getServletSourcer.existsAndAllMatch(
				MarkingPredicates.containsOnce(Pattern.compile("@Inject" + "[^;]+" + "EntityManager ")))));
		/** TODO check manually. */
		gradeBuilder.add(Mark.binary(TRANSACTIONS, anySourcer.anyMatch(Predicates.containsPattern("@Transactional"))));
		gradeBuilder.add(Mark.binary(ADD_EM, addServletSourcer.existsAndAllMatch(
				MarkingPredicates.containsOnce(Pattern.compile("@Inject" + "[^;]+" + "EntityManager ")))));
		/** TODO check manually. */
		gradeBuilder.add(Mark.binary(ADD_LIMIT,
				addServletSourcer.existsAndAllMatch(Predicates.containsPattern("getContentLength"))));

		final GitToTestSourcer testSourcer = GitToTestSourcer.testSourcer(fullContext);
		gradeBuilder.add(Mark.binary(TEST_EXISTS, testSourcer.getContents().size() >= 1 && testSourcer.getContents()
				.keySet().stream().allMatch((p) -> p.startsWith(pomSupplier.getSrcTestJavaFolder()))));
		gradeBuilder.add(Mark.binary(ASSERT_EQUALS, testSourcer.anyMatch(Predicates.containsPattern("assertEquals"))));
		final String travisContent = fullContext.getMainCommitFilesReader().getContent(Paths.get(".travis.yml"));
		gradeBuilder.add(Mark.binary(TRAVIS_CONF, !travisContent.isEmpty()));
		gradeBuilder.add(Marks.travisConfMark(TRAVIS_OK, travisContent));
		final String readmeContent = GitAndBaseToSourcer.given(fullContext, mavenRelativeRoot.resolve("README.adoc"));
		gradeBuilder.add(Mark.binary(TRAVIS_BADGE, Pattern.compile(
				"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + coord.getRepositoryName() + "\\.svg")
				.matcher(readmeContent).find()));
		final String manifestContent = fullContext.getMainCommitFilesReader().getContent(Paths.get("manifest.yml"));
		gradeBuilder
				.add(Mark.binary(IBM_MANIFEST,
						Pattern.compile("- " + Utils.ANY_REG_EXP + "name: ").matcher(manifestContent).find() && Pattern
								.compile("path: " + Utils.ANY_REG_EXP + "target/" + Utils.ANY_REG_EXP + "\\.war")
								.matcher(manifestContent).find()));

		final ImmutableSet<Mark> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExJpaCriterion.values()),
				grade.stream().map(Mark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.equals(ImmutableSet.of(GET_COMMENTS, ADD_COMMENTS));
		return grade;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExJpaGrader.class);

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	public ExJpaGrader() {
		deadline = ZonedDateTime.parse("2018-12-11T23:59:59+01:00").toInstant();
		ignoreAfter = Instant.MAX;
	}
}
