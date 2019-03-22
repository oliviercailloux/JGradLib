package io.github.oliviercailloux.java_grade.ex_jpa;

import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_EM;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.ADD_LIMIT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.AT_ROOT;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.CDI_SCOPE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.COMPILE;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.EXC;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GENERAL_TEST;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET_COMMENTS;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.GET_EM;
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
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.UTF;
import static io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion.WAR;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.markers.JavaEEMarkers;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.java_grade.testers.TestFileRecognizer;
import io.github.oliviercailloux.utils.Utils;

public class ExJpaGrader {

	private Instant deadline;
	private Instant ignoreAfter;

	public ImmutableSet<Mark> grade(RepositoryCoordinates coord) {
		final ImmutableSet.Builder<Mark> gradeBuilder = ImmutableSet.builder();
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

		final FilesSource filesReader = fullContext.getMainFilesReader();
		final double maxGrade = Stream.of(ExJpaCriterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));
		final FilesSource testFiles = mavenProjectMarker.getTestFiles();

		gradeBuilder.add(Marks.timeMark(ON_TIME, fullContext, deadline, maxGrade, false));
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final PomSupplier pomSupplier = mavenProjectMarker.getPomSupplier();
		final Path mavenRelativeRoot = pomSupplier.getForcedMavenRelativeRoot();
		final String pomContent = pomSupplier.getContent();

		gradeBuilder.add(mavenProjectMarker.atRootMark(AT_ROOT));

		final PomContexter pomContexter = mavenProjectMarker.getPomContexter();

		gradeBuilder.add(mavenProjectMarker.groupIdMark(GROUP_ID));
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
		gradeBuilder.add(Marks.packageGroupId(PREFIX, filesReader, pomSupplier, pomContexter));
		LOGGER.debug("Compiling");
		gradeBuilder.add(Marks.mavenCompile(COMPILE, fullContext, pomSupplier));
		LOGGER.debug("Compiled");

		final FilesSource anySourcer = filesReader.filterOnPath((p) -> p.startsWith(pomSupplier.getSrcFolder()));
		final Predicate<Path> srcMainJavaPredicate = (p) -> p.startsWith(pomSupplier.getSrcMainJavaFolder());
		final FilesSource getServletSourcer = filesReader.filterOnPath(
				srcMainJavaPredicate.and((p) -> p.getFileName().equals(Paths.get("GetCommentsServlet.java"))));
		final FilesSource addServletSourcer = filesReader.filterOnPath(
				srcMainJavaPredicate.and((p) -> p.getFileName().equals(Paths.get("AddCommentServlet.java"))));

		gradeBuilder.add(Mark.binary(NO_JSP, JavaEEMarkers.getNoJsp(filesReader)));
		gradeBuilder.add(Mark.binary(NO_WEB_XML, JavaEEMarkers.getNoWebXml(filesReader)));
		final Predicate<CharSequence> containsGet = Predicates.containsPattern("@GET");
		final Predicate<CharSequence> containsNoGet = containsGet.negate();
		final Predicate<CharSequence> containsNoPut = Predicates.containsPattern("@PUT").negate();
		final Predicate<CharSequence> containsPost = Predicates.containsPattern("@POST");
		final Predicate<CharSequence> containsNoPost = containsPost.negate();
		gradeBuilder.add(Mark.binary(GET,
				getServletSourcer.existsAndAllMatch(containsGet.and(containsNoPut).and(containsNoPost))));
		gradeBuilder.add(Mark.binary(POST,
				addServletSourcer.existsAndAllMatch(containsNoGet.and(containsNoPut).and(containsPost))));

		gradeBuilder.add(Mark.binary(NOT_POLLUTED,
				anySourcer.existsAndAllMatch(Predicates.contains(Pattern.compile("Auto-generated")).negate()
						.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate()))));
		gradeBuilder
				.add(Mark.binary(EXC, anySourcer.noneMatch(Predicates.contains(Pattern.compile("printStackTrace")))));

		/** TODO stream a handful of file sources! */
		final ImmutableList<FilesSource> servletSourcers = ImmutableList.of(getServletSourcer, addServletSourcer);
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

		final FilesSource applicationClasses = filesReader.filter((f) -> srcMainJavaPredicate.test(f.getPath())
				&& f.getContent().contains("@ApplicationPath") && f.getContent().contains("extends.* Application"));
		gradeBuilder.add(Mark.binary(JAX_RS_APP, applicationClasses.getContents().size() == 1));
		gradeBuilder.add(Marks.noDerivedFiles(ONLY_ORIG, fullContext));

		final FilesSource multiPersistence = filesReader
				.filterOnPath((p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("persistence.xml"));
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

		gradeBuilder.add(Mark.binary(TEST_EXISTS, testFiles.getContents().size() >= 1 && testFiles.getContents()
				.keySet().stream().allMatch((p) -> p.startsWith(pomSupplier.getSrcTestJavaFolder()))));
		gradeBuilder.add(generalTestMark(GENERAL_TEST, mavenProjectMarker));
		final String travisContent = fullContext.getMainFilesReader().getContent(Paths.get(".travis.yml"));
		gradeBuilder.add(Mark.binary(TRAVIS_CONF, !travisContent.isEmpty()));
		gradeBuilder.add(Marks.travisConfMark(TRAVIS_OK, travisContent));
		final String readmeContent = fullContext.getMainFilesReader()
				.getContent(mavenRelativeRoot.resolve("README.adoc"));
		gradeBuilder.add(Mark.binary(TRAVIS_BADGE, Pattern.compile(
				"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + coord.getRepositoryName() + "\\.svg")
				.matcher(readmeContent).find()));
		final String manifestContent = fullContext.getMainFilesReader().getContent(Paths.get("manifest.yml"));
		gradeBuilder
				.add(Mark.binary(IBM_MANIFEST,
						Pattern.compile("- " + Utils.ANY_REG_EXP + "name: ").matcher(manifestContent).find() && Pattern
								.compile("path: " + Utils.ANY_REG_EXP + "target/" + Utils.ANY_REG_EXP + "\\.war")
								.matcher(manifestContent).find()));

		final ImmutableSet<Mark> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExJpaCriterion.values()),
				grade.stream().map(Mark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.equals(ImmutableSet.of(GET_COMMENTS, ADD_COMMENTS)) : diff;
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

	private Mark generalTestMark(Criterion criterion, MavenProjectMarker mavenProjectMarker) {
		if (mavenProjectMarker.getPomSupplier().asMultiContent().asFileContents().isEmpty()) {
			return Mark.min(criterion, "No POM");
		}

		final boolean containsJUnit5 = mavenProjectMarker.getPomSupplier().hasJunit5();
		final boolean arquillian = mavenProjectMarker.getTestFiles()
				.anyMatch(Predicates.containsPattern("Arquillian.class"));
		final boolean assertEquals = mavenProjectMarker.getTestFiles()
				.anyMatch(Predicates.containsPattern("assertEquals"));
		mavenProjectMarker.doTestsExistAndPass();
		String comment = "";
		if (containsJUnit5) {
			comment += "Using recent version of JUnit 5";
		} else {
			if (arquillian) {
				comment += "Not using recent version of JUnit 5 but using Arquillian instead, ok";
			} else {
				comment += "Not using recent version of JUnit 5";
			}
		}
		if (arquillian) {
			comment += "Using arquillian";
		}
		if (!assertEquals) {
			comment += "Not using expected assertEquals (or similar) construct";
		}
		if (mavenProjectMarker.doTestsExistAndPass()) {
			comment += "Maven tests pass";
		} else {
			if (mavenProjectMarker.getTestFiles().getContents().keySet().stream()
					.anyMatch(TestFileRecognizer::isSurefireTestFile)) {
				comment += "Maven tests do not pass";
			} else {
				comment += "No tests found";
			}
		}
		return Mark.of(criterion,
				mavenProjectMarker.doTestsExistAndPass() && assertEquals && (containsJUnit5 || arquillian)
						? criterion.getMaxPoints()
						: criterion.getMinPoints(),
				comment);
	}

	public ExJpaGrader() {
		deadline = ZonedDateTime.parse("2019-03-08T23:59:59+01:00").toInstant();
		ignoreAfter = Instant.MAX;
	}
}
