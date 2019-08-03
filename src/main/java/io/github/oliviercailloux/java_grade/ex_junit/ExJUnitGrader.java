package io.github.oliviercailloux.java_grade.ex_junit;

import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.BRANCH;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.CLASS_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.CLASS_IN_TEST;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.CLASS_NAME;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.GIT;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.PDF_IN_TEST;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.REPO_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion.TEST_TESTS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.ContextInitializer;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;

public class ExJUnitGrader {
	private GitHistory history;

	public ExJUnitGrader() {
		history = null;
	}

	public Map<Criterion, IGrade> grade(RepositoryCoordinates coord) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/junit");
		final Instant deadline = ZonedDateTime.parse("2019-06-04T17:42:00+02:00").toInstant();

		GitFullContext fullContext;
		GitContext context;
		try {
			fullContext = FullContextInitializer.withPath(coord, projectsBaseDir);
			context = fullContext;
		} catch (IllegalStateException exc) {
			LOGGER.error("No FCI.", exc);
			fullContext = null;
			context = ContextInitializer.withPath(coord, projectsBaseDir);
		}

		final Client client = context.getClient();
		try {
			history = client.getWholeHistory();
		} catch (IOException e) {
			throw new GradingException(e);
		} catch (GitAPIException e) {
			LOGGER.warn("No history.", e);
			history = GitHistory.from(ImmutableSet.of());
		}

		final Set<RevCommit> noChildren = history.getGraph().nodes().stream()
				.filter((n) -> history.getGraph().predecessors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
		final Optional<RevCommit> lastCommitOpt;
		if (fullContext == null) {
			lastCommitOpt = noChildren.stream().collect(MoreCollectors.toOptional());
		} else {
			final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
			if (noChildren.size() <= 1) {
				lastCommitOpt = noChildren.stream().collect(MoreCollectors.toOptional());
				/**
				 * Says: “org.eclipse.jgit.api.errors.NoHeadException: No HEAD exists and no
				 * explicit starting revision was specified”. (I think GitHub fork has crashed
				 * and created a strange, more or less empty, repository. In the command line,
				 * seems to behave as a just initialized repository with no commit.)
				 */
				Verify.verify(lastCommitOpt.equals(mainCommit));
			} else {
				LOGGER.warn("Multiple no children: {}", toString(noChildren));
				lastCommitOpt = mainCommit;
			}
		}

		if (fullContext != null) {
			gradeBuilder.put(ON_TIME, Marks.timeGrade(fullContext, deadline, this::getPenalty));
			gradeBuilder.put(REPO_EXISTS, Marks.gitRepoGrade(fullContext));
		} else {
			gradeBuilder.put(ON_TIME, Mark.one());
			gradeBuilder.put(REPO_EXISTS, Mark.given(Booleans.countTrue(lastCommitOpt.isPresent()), ""));
		}

		final FilesSource filesReader = context.getFilesReader(lastCommitOpt);
		final MavenProjectMarker mavenMarker = MavenProjectMarker.given(filesReader, client.getProjectDirectory());

		if (lastCommitOpt.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(lastCommitOpt.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final Predicate<RevCommit> gitHub = MarkHelper::committerIsGitHub;
		final Predicate<RevCommit> cail = MarkHelper::committerIsCailloux;
		final ImmutableSet<RevCommit> byOwn = history.getGraph().nodes().stream().filter(gitHub.or(cail).negate())
				.collect(ImmutableSet.toImmutableSet());
		{
			final Mark mark;
			if (!byOwn.isEmpty()) {
				mark = Mark.given(1d, String.format("Own commits: %s.", toString(byOwn)));
			} else {
				mark = Mark.zero();
			}
			gradeBuilder.put(GIT, mark);
		}

		final Optional<RevCommit> devOpt = tryParseSpec(client, "refs/remotes/origin/testing");
		gradeBuilder.put(BRANCH, Mark.given(Booleans.countTrue(devOpt.isPresent()), ""));

		final Path srcMainJavaFolder = mavenMarker.getPomSupplier().getSrcMainJavaFolder();
		final Path srcTestJavaFolder = mavenMarker.getPomSupplier().getSrcTestJavaFolder();
		final FilesSource extractorTestsFiles = filesReader
				.filterOnPath((p) -> p.toString().contains("ExtractorTests.java"));

		LOGGER.info("ETF: {}.", extractorTestsFiles.getContents().keySet());

		gradeBuilder.put(CLASS_EXISTS,
				Mark.given(Booleans.countTrue(!extractorTestsFiles.asFileContents().isEmpty()), ""));
		final Path expectedName = Paths.get("io/github/oliviercailloux/extractor/ExtractorTests.java");
		final Path expectedPdfName = mavenMarker.getPomSupplier().getSrcFolder()
				.resolve("test/resources/io/github/oliviercailloux/extractor/hello-world.pdf");
		gradeBuilder.put(CLASS_NAME,
				Mark.given(Booleans.countTrue(
						!extractorTestsFiles.asFileContents().isEmpty() && extractorTestsFiles.asFileContents().stream()
								.allMatch((fc) -> fc.getPath().equals(srcMainJavaFolder.resolve(expectedName))
										|| fc.getPath().equals(srcTestJavaFolder.resolve(expectedName)))),
						""));

		gradeBuilder
				.put(CLASS_IN_TEST,
						Mark.given(
								Booleans.countTrue(!extractorTestsFiles.asFileContents().isEmpty()
										&& extractorTestsFiles.asFileContents().stream().allMatch(
												(fc) -> fc.getPath().equals(srcTestJavaFolder.resolve(expectedName)))),
								""));
		gradeBuilder.put(PDF_IN_TEST,
				Mark.given(Booleans.countTrue(
						filesReader.asFileContents().stream().anyMatch((fc) -> fc.getPath().equals(expectedPdfName))),
						""));
		gradeBuilder.put(TEST_TESTS, Mark.zero());

		return gradeBuilder.build();
	}

	private String toString(Set<RevCommit> commits) {
		return "[" + commits.stream().map(RevCommit::getName).collect(Collectors.joining(", ")) + "]";
	}

	private Optional<RevCommit> tryParseSpec(Client client, String revSpec) {
		final Optional<RevCommit> devOpt;
		try {
			devOpt = client.tryResolve(revSpec).map(t -> {
				try {
					return client.getCommit(t);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		} catch (IOException e) {
			throw new GradingException(e);
		}
		return devOpt;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExJUnitGrader.class);

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExJUnitCriterion.values())
				.collect(Collectors.summingDouble(CriterionAndPoints::getMaxPoints));

		LOGGER.debug("Tardiness: {}.", tardiness);
		final long secondsLate = tardiness.toSeconds();
		return -0.05d / 20d * maxGrade * secondsLate;
	}
}
