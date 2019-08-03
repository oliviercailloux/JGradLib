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
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.JavaEEMarkers;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHub;
import io.github.oliviercailloux.java_grade.JavaMarks;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class ExJpaGrader {

	private Instant deadline;

	public WeightingGrade grade(RepositoryCoordinates coord) {
		/** TODO improve grading attempts by time. */
		final WeightingGrade usingLastCommit = grade(coord, Instant.MAX);
		final ImmutableSet<CriterionGradeWeight> realMarks;
		if (timeMark.getPoints() < 0d) {
			final WeightingGrade usingCommitOnTime = grade(coord, deadline);
//			final Optional<ObjectId> commitOnTime = mainCommit.map(RevCommit::copy);
			final double lastCommitPoints = usingLastCommit.getPoints();
			final double onTimePoints = usingCommitOnTime.getPoints();
			final WeightingGrade gradeToUse;
			final String newComment;
			if (onTimePoints > lastCommitPoints) {
				gradeToUse = usingCommitOnTime;
				newComment = " (Using commit on time rather than last commit because it brings more points.)";
			} else {
				gradeToUse = usingLastCommit;
				newComment = " (Using last commit rather than commit on time because it brings at least as much points.)";
			}
			final IGrade originalMark = gradeToUse.getSubGrades().get(ON_TIME);
			Verify.verify(originalMark.getSubGrades().isEmpty());
			final CriterionGradeWeight commentedMark = CriterionGradeWeight.from(ON_TIME,
					Mark.given(originalMark.getPoints(), originalMark.getComment() + newComment),
					gradeToUse.getWeights().get(ON_TIME));
			realMarks = gradeToUse.getSubGradesAsSet().stream()
					.map((cgw) -> cgw.getCriterion() != ON_TIME ? cgw : commentedMark)
					.collect(ImmutableSet.toImmutableSet());
		} else {
			realMarks = usingLastCommit.getSubGradesAsSet();
		}
		return WeightingGrade.from(realMarks);
	}

	public WeightingGrade grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
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

		timeMark = Marks.timeGrade(fullContext, deadline, this::getPenalty);
		gradeBuilder.put(ON_TIME, timeMark);
		gradeBuilder.put(REPO_EXISTS, Marks.gitRepoGrade(fullContext));

		final PomSupplier pomSupplier = mavenProjectMarker.getPomSupplier();
		final FilesSource anySourcer = filesReader.filterOnPath((p) -> p.startsWith(pomSupplier.getSrcFolder()));
		final FilesSource mainSourcer = filesReader
				.filterOnPath((p) -> p.startsWith(pomSupplier.getSrcMainJavaFolder()));
		final Path mavenRelativeRoot = pomSupplier.getForcedMavenRelativeRoot();
		final String pomContent = pomSupplier.getContent();

		gradeBuilder.put(AT_ROOT, mavenProjectMarker.atRootGrade());

		final PomContexter pomContexter = mavenProjectMarker.getPomContexter();

		gradeBuilder.put(GROUP_ID, mavenProjectMarker.groupIdGrade());
		gradeBuilder.put(UTF,
				Mark.ifPasses(MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
						+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
						+ Utils.ANY_REG_EXP + "</properties>")).test(pomContent)));
		gradeBuilder
				.put(SOURCE,
						Mark.ifPasses(MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>" + Utils.ANY_REG_EXP
								+ "</properties>")).test(pomContent)));
		gradeBuilder.put(NO_MISLEADING_URL, Mark.ifPasses(
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate().test(pomContent)));
		gradeBuilder.put(WAR, Mark.ifPasses(
				MarkingPredicates.containsOnce(Pattern.compile("<packaging>war</packaging>")).test(pomContent)));
		gradeBuilder.put(PREFIX, Marks.packageGroupIdGrade(filesReader, pomSupplier, pomContexter));
		LOGGER.debug("Compiling");
		gradeBuilder.put(COMPILE, Marks.mavenCompileGrade(fullContext, pomSupplier));
		LOGGER.debug("Compiled");

		final FilesSource getServletSourcer = mainSourcer
				.filterOnPath((p) -> p.getFileName().equals(Paths.get("GetCommentsServlet.java")));
		final FilesSource addServletSourcer = mainSourcer
				.filterOnPath((p) -> p.getFileName().equals(Paths.get("AddCommentServlet.java")));

		gradeBuilder.put(NO_JSP, Mark.ifPasses(JavaEEMarkers.getNoJsp(filesReader)));
		gradeBuilder.put(NO_WEB_XML, Mark.ifPasses(JavaEEMarkers.getNoWebXml(filesReader)));
		final Predicate<CharSequence> containsGet = Predicates.containsPattern("@GET");
		final Predicate<CharSequence> containsNoGet = containsGet.negate();
		final Predicate<CharSequence> containsNoPut = Predicates.containsPattern("@PUT").negate();
		final Predicate<CharSequence> containsPost = Predicates.containsPattern("@POST");
		final Predicate<CharSequence> containsNoPost = containsPost.negate();
		gradeBuilder.put(GET,
				Mark.ifPasses(getServletSourcer.existsAndAllMatch(containsGet.and(containsNoPut).and(containsNoPost))));
		gradeBuilder.put(POST,
				Mark.ifPasses(addServletSourcer.existsAndAllMatch(containsNoGet.and(containsNoPut).and(containsPost))));

		gradeBuilder.put(NOT_POLLUTED,
				Mark.ifPasses(anySourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("Auto-generated"))
						.negate().and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()))));
		gradeBuilder.put(EXC, Mark.ifPasses(anySourcer.noneMatch(
				Predicates.contains(Pattern.compile("printStackTrace")).or(Predicates.containsPattern("catch\\(")))));

		/** TODO stream a handful of file sources! */
		final ImmutableList<FilesSource> servletSourcers = ImmutableList.of(getServletSourcer, addServletSourcer);
		gradeBuilder.put(MTYPE,
				Mark.given((double) servletSourcers.stream()
						.filter((mc) -> mc.existsAndAllMatch(
								Predicates.containsPattern("@Produces\\(.*MediaType.TEXT_PLAIN.*\\)")))
						.count() / servletSourcers.size(), ""));
		gradeBuilder.put(PATH_ANNOT,
				Mark.given((double) servletSourcers.stream().filter(
						(mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@Path.*\\(.*\"comments\".*\\)")))
						.count() / servletSourcers.size(), ""));
//		gradeBuilder.put(Mark.proportional(CDI_SCOPE,
//				(int) servletSourcers.stream()
//						.filter((mc) -> mc.existsAndAllMatch(Predicates.containsPattern("@.*Scoped"))).count(),
//				servletSourcers.size()));

		final FilesSource applicationClasses = mainSourcer.filter(
				(f) -> f.getContent().contains("@ApplicationPath") && f.getContent().contains("extends Application"));
		gradeBuilder.put(JAX_RS_APP, Mark.ifPasses(applicationClasses.asFileContents().size() == 1));
		gradeBuilder.put(ONLY_ORIG, Marks.noDerivedFilesGrade(filesReader));

		final FilesSource multiPersistence = filesReader
				.filterOnPath((p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("persistence.xml"));
		gradeBuilder.put(PERSISTENCE, Mark.ifPasses(multiPersistence.getContents()
				.containsKey(mavenRelativeRoot.resolve(Paths.get("src/main/resources/META-INF/persistence.xml")))));

		gradeBuilder.put(PERSISTENCE_CONTENTS, Mark.ifPasses(
				multiPersistence.existsAndAllMatch(Predicates.containsPattern("persistence version=\"2.1\""))));
//						multiPersistence.existsAndNoneMatch(Predicates.containsPattern("RESOURCE_LOCAL")),
//						multiPersistence.existsAndAllMatch(Predicates.containsPattern("drop-and-create")),
//						multiPersistence.existsAndAllMatch(Predicates.containsPattern("hibernate.show_sql")
//								.or(Predicates.containsPattern("eclipselink.logging.level"))),
//						multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jta-data-source")),
//						multiPersistence.existsAndNoneMatch(Predicates.containsPattern("jdbc.driver"))));

		gradeBuilder.put(USING_EM, Mark.ifPasses(!mainSourcer.asFileContents().isEmpty() && mainSourcer
				.anyMatch(Predicates.contains(Pattern.compile("@PersistenceContext" + "[^;]+" + "EntityManager ")))));
		gradeBuilder.put(TRANSACTIONS,
				Mark.ifPasses(anySourcer.anyMatch(Predicates.containsPattern("@Transactional"))));
		gradeBuilder.put(ADD_LIMIT,
				Mark.ifPasses(addServletSourcer.existsAndAllMatch(Predicates.containsPattern("getContentLength"))));

		gradeBuilder.put(TEST_EXISTS, Mark.ifPasses(testFiles.getContents().size() >= 1 && testFiles.getContents()
				.keySet().stream().allMatch((p) -> p.startsWith(pomSupplier.getSrcTestJavaFolder()))));
		gradeBuilder.put(GENERAL_TEST, generalTestMark(mavenProjectMarker));
		final String travisContent = fullContext.getFilesReader(fullContext.getMainCommit())
				.getContent(Paths.get(".travis.yml"));
		gradeBuilder.put(TRAVIS_CONF, Mark.ifPasses(!travisContent.isEmpty()));
		gradeBuilder.put(TRAVIS_OK, Marks.travisConfGrade(travisContent));
		gradeBuilder.put(TRAVIS_BADGE, JavaMarks.travisBadgeGrade(filesReader, coord.getRepositoryName()));
		final String manifestContent = fullContext.getFilesReader(fullContext.getMainCommit())
				.getContent(Paths.get("manifest.yml"));
		gradeBuilder.put(IBM_MANIFEST,
				Mark.ifPasses(Pattern.compile("- " + Utils.ANY_REG_EXP + "name: ").matcher(manifestContent).find()
						&& Pattern.compile("path: " + Utils.ANY_REG_EXP + "target/" + Utils.ANY_REG_EXP + "\\.war")
								.matcher(manifestContent).find()));
		/**
		 * −0,5 for incorrectly formatted but quite correct (e.g. puts id as well). Do
		 * not penalize when incorrect file name because already considered elsewhere.
		 */
		gradeBuilder.put(GET_COMMENTS, Mark.ifPasses(!getServletSourcer.asFileContents().isEmpty()));
		gradeBuilder.put(ADD_COMMENTS, Mark.ifPasses(!addServletSourcer.asFileContents().isEmpty()));

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap<Criterion, Double> weights = Arrays.stream(ExJpaCriterion.values())
				.collect(ImmutableMap.toImmutableMap(Functions.identity(),
						(c) -> c.getMaxPoints() == 0d ? c.getMinPoints() : c.getMaxPoints()));
		return WeightingGrade.from(subGrades, weights);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExJpaGrader.class);
	private IGrade timeMark;

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	private IGrade generalTestMark(MavenProjectMarker mavenProjectMarker) {
		/** TODO refine using sub-grades. */
		if (mavenProjectMarker.getPomSupplier().asMultiContent().asFileContents().isEmpty()) {
			return Mark.zero("No POM");
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
		return Mark.given(
				mavenProjectMarker.doTestsExistAndPass() && assertEquals && (containsJUnit5 || arquillian) ? 1d : 0d,
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

		final ImmutableMap<StudentOnGitHub, WeightingGrade> grades = repositories.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, (e) -> grader.grade(e.getValue())));

		/** TODO make it flush after each line. */
		Files.writeString(srcDir.resolve("grades newver " + prefix + ".json"),
				JsonbUtils.toJsonValue(grades, JsonGrade.asAdapter(), JsonStudentOnGitHub.asAdapter()).toString());
		Files.writeString(srcDir.resolve("grades newver " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExJpaGrader() {
		deadline = ZonedDateTime.parse("2019-03-08T23:59:59+01:00").toInstant();
		timeMark = null;
	}
}
