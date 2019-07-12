package io.github.oliviercailloux.java_grade.ex_jpa;

import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_LIMIT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.AT_ROOT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.COMPILE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.EXC;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GENERAL_TEST;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GROUP_ID;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.IBM_MANIFEST;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.JAX_RS_APP;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.MTYPE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.NOT_POLLUTED;
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
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.USING_EM;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.UTF;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.WAR;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.JavaEEMarkers;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.JavaMarks;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.utils.Utils;

public class ExJpaGrader {

	private Instant deadline;

	public ImmutableSet<GradeWithStudentAndCriterion> grade(RepositoryCoordinates coord) {
		final AnonymousGrade usingLastCommit = grade(coord, Instant.MAX);
		final ImmutableSet<GradeWithStudentAndCriterion> realMarks;
		if (timeMark.getPoints() < 0d) {
			final AnonymousGrade usingCommitOnTime = grade(coord, deadline);
			final double lastCommitPoints = usingLastCommit.getPoints();
			final double onTimePoints = usingCommitOnTime.getPoints();
			if (onTimePoints > lastCommitPoints) {
				final GradeWithStudentAndCriterion originalMark = usingCommitOnTime.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(), originalMark.getComment()
						+ " (Using commit on time rather than last commit because it brings more points.)");
				realMarks = usingCommitOnTime.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			} else {
				final GradeWithStudentAndCriterion originalMark = usingLastCommit.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(), originalMark.getComment()
						+ " (Using last commit rather than commit on time because it brings at least as much points.)");
				realMarks = usingLastCommit.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			}
		} else {
			realMarks = usingLastCommit.getMarks().values();
		}
		return realMarks;
	}

	public AnonymousGrade grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableSet.Builder<CriterionAndMark> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/jpa");

		final GitFullContext fullContext = FullContextInitializer.withPathAndIgnore(coord, projectsBaseDir,
				ignoreAfter);
		final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final MavenProjectMarker mavenProjectMarker = MavenProjectMarker.given(fullContext);

		final FilesSource filesReader = fullContext.getFilesReader(fullContext.getMainCommit());
		final FilesSource testFiles = mavenProjectMarker.getTestFiles();

		timeMark = Marks.timeMark(ON_TIME, fullContext, deadline, this::getPenalty);
		gradeBuilder.add(timeMark);
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final PomSupplier pomSupplier = mavenProjectMarker.getPomSupplier();
		final FilesSource anySourcer = filesReader.filterOnPath((p) -> p.startsWith(pomSupplier.getSrcFolder()));
		final FilesSource mainSourcer = filesReader
				.filterOnPath((p) -> p.startsWith(pomSupplier.getSrcMainJavaFolder()));
		final Path mavenRelativeRoot = pomSupplier.getForcedMavenRelativeRoot();
		final String pomContent = pomSupplier.getContent();

		gradeBuilder.add(mavenProjectMarker.atRootMark(AT_ROOT));

		final PomContexter pomContexter = mavenProjectMarker.getPomContexter();

		gradeBuilder.add(mavenProjectMarker.groupIdMark(GROUP_ID));
		gradeBuilder.add(CriterionAndMark.binary(UTF,
				MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
						+ Utils.ANY_REG_EXP + "</properties>")).test(pomContent)));
		gradeBuilder
				.add(CriterionAndMark.binary(SOURCE,
						MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + Utils.ANY_REG_EXP
								+ "</properties>")).test(pomContent)));
		gradeBuilder.add(CriterionAndMark.binary(NO_MISLEADING_URL,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate().test(pomContent)));
		gradeBuilder.add(CriterionAndMark.binary(WAR,
				MarkingPredicates.containsOnce(Pattern.compile("<packaging>war</packaging>")).test(pomContent)));
		gradeBuilder.add(Marks.packageGroupId(PREFIX, filesReader, pomSupplier, pomContexter));
		LOGGER.debug("Compiling");
		gradeBuilder.add(Marks.mavenCompile(COMPILE, fullContext, pomSupplier));
		LOGGER.debug("Compiled");

		final FilesSource getServletSourcer = mainSourcer
				.filterOnPath((p) -> p.getFileName().equals(Paths.get("GetCommentsServlet.java")));
		final FilesSource addServletSourcer = mainSourcer
				.filterOnPath((p) -> p.getFileName().equals(Paths.get("AddCommentServlet.java")));

		gradeBuilder.add(CriterionAndMark.binary(NO_JSP, JavaEEMarkers.getNoJsp(filesReader)));
		gradeBuilder.add(CriterionAndMark.binary(NO_WEB_XML, JavaEEMarkers.getNoWebXml(filesReader)));
		final Predicate<CharSequence> containsGet = Predicates.containsPattern("@GET");
		final Predicate<CharSequence> containsNoGet = containsGet.negate();
		final Predicate<CharSequence> containsNoPut = Predicates.containsPattern("@PUT").negate();
		final Predicate<CharSequence> containsPost = Predicates.containsPattern("@POST");
		final Predicate<CharSequence> containsNoPost = containsPost.negate();
		gradeBuilder.add(CriterionAndMark.binary(GET,
				getServletSourcer.existsAndAllMatch(containsGet.and(containsNoPut).and(containsNoPost))));
		gradeBuilder.add(CriterionAndMark.binary(POST,
				addServletSourcer.existsAndAllMatch(containsNoGet.and(containsNoPut).and(containsPost))));

		gradeBuilder.add(CriterionAndMark.binary(NOT_POLLUTED,
				anySourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()))));
		gradeBuilder.add(CriterionAndMark.binary(EXC, anySourcer.noneMatch(
				Predicates.contains(Pattern.compile("printStackTrace")).or(Predicates.containsPattern("catch\\(")))));

		/** TODO stream a handful of file sources! */
		final ImmutableList<FilesSource> servletSourcers = ImmutableList.of(getServletSourcer, addServletSourcer);
		gradeBuilder.add(CriterionAndMark.proportional(MTYPE,
				(int) servletSourcers.stream()
						.filter((mc) -> mc.existsAndAllMatch(
								Predicates.containsPattern("@Produces\\(.*MediaType.TEXT_PLAIN.*\\)")))
						.count(),
				servletSourcers.size()));
		gradeBuilder.add(CriterionAndMark.proportional(PATH_ANNOT,
				(int) servletSourcers.stream().filter(
						(mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@Path.*\\(.*\"comments\".*\\)")))
						.count(),
				servletSourcers.size()));
//		gradeBuilder.add(Mark.proportional(CDI_SCOPE,
//				(int) servletSourcers.stream()
//						.filter((mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@.*Scoped"))).count(),
//				servletSourcers.size()));

		final FilesSource applicationClasses = mainSourcer.filter(
				(f) -> f.getContent().contains("@ApplicationPath") && f.getContent().contains("extends Application"));
		gradeBuilder.add(CriterionAndMark.binary(JAX_RS_APP, applicationClasses.asFileContents().size() == 1));
		gradeBuilder.add(Marks.noDerivedFiles(ONLY_ORIG, filesReader));

		final FilesSource multiPersistence = filesReader
				.filterOnPath((p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("persistence.xml"));
		gradeBuilder.add(CriterionAndMark.binary(PERSISTENCE, multiPersistence.getContents()
				.containsKey(mavenRelativeRoot.resolve(Paths.get("src/main/resources/META-INF/persistence.xml")))));

		gradeBuilder.add(CriterionAndMark.proportional(PERSISTENCE_CONTENTS,
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("persistence version=\"2.1\"")),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("RESOURCE_LOCAL")),
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("drop-and-create")),
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("hibernate.show_sql")
						.or(Predicates.containsPattern("eclipselink.logging.level"))),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jta-data-source")),
				multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jdbc.driver"))));

		gradeBuilder.add(CriterionAndMark.binary(USING_EM, !mainSourcer.asFileContents().isEmpty() && mainSourcer
				.anyMatch(Predicates.contains(Pattern.compile("@PersistenceContext" + "[^;]+" + "EntityManager ")))));
		gradeBuilder.add(CriterionAndMark.binary(TRANSACTIONS, anySourcer.anyMatch(Predicates.containsPattern("@Transactional"))));
		gradeBuilder.add(CriterionAndMark.binary(ADD_LIMIT,
				addServletSourcer.existsAndAllMatch(Predicates.containsPattern("getContentLength"))));

		gradeBuilder.add(CriterionAndMark.binary(TEST_EXISTS, testFiles.getContents().size() >= 1 && testFiles.getContents()
				.keySet().stream().allMatch((p) -> p.startsWith(pomSupplier.getSrcTestJavaFolder()))));
		gradeBuilder.add(generalTestMark(GENERAL_TEST, mavenProjectMarker));
		final String travisContent = fullContext.getFilesReader(fullContext.getMainCommit())
				.getContent(Paths.get(".travis.yml"));
		gradeBuilder.add(CriterionAndMark.binary(TRAVIS_CONF, !travisContent.isEmpty()));
		gradeBuilder.add(Marks.travisConfMark(TRAVIS_OK, travisContent));
		gradeBuilder.add(JavaMarks.travisBadgeMark(TRAVIS_BADGE, filesReader, coord.getRepositoryName()));
		final String manifestContent = fullContext.getFilesReader(fullContext.getMainCommit())
				.getContent(Paths.get("manifest.yml"));
		gradeBuilder
				.add(CriterionAndMark.binary(IBM_MANIFEST,
						Pattern.compile("- " + Utils.ANY_REG_EXP + "name: ").matcher(manifestContent).find() && Pattern
								.compile("path: " + Utils.ANY_REG_EXP + "target/" + Utils.ANY_REG_EXP + "\\.war")
								.matcher(manifestContent).find()));
		/**
		 * −0,5 for incorrectly formatted but quite correct (e.g. puts id as well). Do
		 * not penalize when incorrect file name because already considered elsewhere.
		 */
		gradeBuilder.add(CriterionAndMark.binary(GET_COMMENTS, !getServletSourcer.asFileContents().isEmpty()));
		gradeBuilder.add(CriterionAndMark.binary(ADD_COMMENTS, !addServletSourcer.asFileContents().isEmpty()));

		final ImmutableSet<CriterionAndMark> grade = gradeBuilder.build();
		final Set<CriterionAndPoints> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExJpaCriterion.values()),
				grade.stream().map(CriterionAndMark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.isEmpty() : diff;
		return GradeWithStudentAndCriterion.anonymous(grade);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExJpaGrader.class);
	private CriterionAndMark timeMark;

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	private CriterionAndMark generalTestMark(CriterionAndPoints criterion, MavenProjectMarker mavenProjectMarker) {
		if (mavenProjectMarker.getPomSupplier().asMultiContent().asFileContents().isEmpty()) {
			return CriterionAndMark.min(criterion, "No POM");
		}

		final boolean containsJUnit5 = mavenProjectMarker.getPomSupplier().hasJunit5();
		final boolean arquillian = mavenProjectMarker.getTestFiles()
				.anyMatch(Predicates.containsPattern("Arquillian.class"));
		final boolean assertEquals = mavenProjectMarker.getTestFiles()
				.anyMatch(Predicates.containsPattern("assertEquals"));
		mavenProjectMarker.doTestsExistAndPass();
		String comment = "";
		if (containsJUnit5) {
			comment += "Using recent version of JUnit 5; ";
			if (arquillian) {
				comment += "Using arquillian; ";
			}
		} else {
			if (arquillian) {
				comment += "Not using recent version of JUnit 5 but using Arquillian instead, ok; ";
			} else {
				comment += "Not using recent version of JUnit 5; ";
			}
		}
		if (!assertEquals) {
			comment += "Not using expected assertEquals (or similar) construct; ";
		}
		if (mavenProjectMarker.doTestsExistAndPass()) {
			comment += "Maven tests pass";
		} else {
			if (mavenProjectMarker.getTestFiles().getContents().keySet().stream()
					.anyMatch(MarkHelper::isSurefireTestFile)) {
				comment += "Maven tests do not pass";
			} else {
				comment += "No Surefire tests found among " + mavenProjectMarker.getTestFiles().getContents().keySet();
			}
		}
		LOGGER.info("General test mark comment {}.", comment);
		return CriterionAndMark.of(criterion,
				mavenProjectMarker.doTestsExistAndPass() && assertEquals && (containsJUnit5 || arquillian)
						? criterion.getMaxPoints()
						: criterion.getMinPoints(),
				comment);
	}

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExJpaCriterion.values())
				.collect(Collectors.summingDouble(CriterionAndPoints::getMaxPoints));

		final long hoursLate = tardiness.toHours() + 1;
		return -3d / 20d * maxGrade * hoursLate;
	}

	public static void main(String[] args) throws Exception {
		/**
		 * TODO 1) no need of history. Repo is cloned locally and set at right commit.
		 * Then, only need a path, no git client.
		 *
		 * 2) Need navigation through history. Use plumbing API, no work space is
		 * necessary; a main commit and a plumbing client are required. Get everything
		 * including the main commit from API. The main commit should be provided by a
		 * distinct object as it is useful in both cases (author, date…)?
		 */
		final String prefix = "jpa";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java SITN, app, concept°/");
		orch.readUsernames(srcDir.resolve("usernames.json"));

		orch.readRepositories();
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExJpaGrader grader = new ExJpaGrader();

		final ImmutableSet<GradeWithStudentAndCriterion> grades = repositories.entrySet().stream()
				.map((e) -> GradeWithStudentAndCriterion.of(e.getKey(), grader.grade(e.getValue()))).collect(ImmutableSet.toImmutableSet());

		/** TODO make it flush after each line. */
		Files.writeString(srcDir.resolve("grades again " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(srcDir.resolve("grades again " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExJpaGrader() {
		deadline = ZonedDateTime.parse("2019-03-08T23:59:59+01:00").toInstant();
		timeMark = null;
	}
}
