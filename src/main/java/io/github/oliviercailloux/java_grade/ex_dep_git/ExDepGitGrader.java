package io.github.oliviercailloux.java_grade.ex_dep_git;

import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.COMMIT;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.ON_TIME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;

public class ExDepGitGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExDepGitGrader.class);
	private static final Instant DEADLINE = ZonedDateTime.parse("2019-05-20T16:07:00+02:00").toInstant();
	private static final Instant START = DEADLINE.minus(40, ChronoUnit.MINUTES);

	public static void main(String[] args) throws Exception {
		final String prefix = "dep-git";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java L3/");
		orch.readUsernames(srcDir.resolve("usernamesGH-manual.json"));

		orch.readRepositories();
		orch.setSingleRepo("elbaylot");

		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExDepGitGrader grader = new ExDepGitGrader();

		final ImmutableSet<Grade> grades = repositories.entrySet().stream()
				.map((e) -> Grade.of(e.getKey(), grader.grade(e.getValue()).getMarks().values()))
				.collect(ImmutableSet.toImmutableSet());

		LOGGER.info("Grades: {}.", grades);

		Files.writeString(srcDir.resolve("all grades " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(srcDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExDepGitGrader() {
		timeMark = null;
		mavenAbsoluteRoot = null;
		fullContext = null;
		commitsReceptionTime = null;
	}

	private Mark timeMark;
	private Path mavenAbsoluteRoot;
	private GitFullContext fullContext;
	private ImmutableMap<ObjectId, Instant> commitsReceptionTime;

	public AnonymousGrade grade(RepositoryCoordinates coord) {
		mavenAbsoluteRoot = null;

		final ImmutableSet.Builder<Mark> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/dep-git");

		final FullContextInitializer spec = (FullContextInitializer) FullContextInitializer.withPathAndIgnore(coord,
				projectsBaseDir, DEADLINE.plusSeconds(60));
		commitsReceptionTime = spec.getCommitsReceptionTime();
		fullContext = spec;
		final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final FilesSource filesReader = fullContext.getFilesReader(fullContext.getMainCommit());
		final MavenProjectMarker mavenMarker = MavenProjectMarker.given(fullContext);

		timeMark = Marks.timeMark(ON_TIME, fullContext, DEADLINE, this::getPenalty);
		gradeBuilder.add(timeMark);
		gradeBuilder.add(commitMark());

		final ImmutableSet<Mark> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExExtractorCriterion.values()),
				grade.stream().map(Mark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
//		assert diff.isEmpty() : diff;
		return Grade.anonymous(grade);
	}

	Mark commitMark() {
		final Client client = fullContext.getClient();
		final Set<RevCommit> commits;
		try {
			commits = client.getAllCommits();
		} catch (IOException | GitAPIException e) {
			throw new IllegalStateException(e);
		}
		final ImmutableList<ZonedDateTime> commitDeclaredTimes = commits.stream().map(GitUtils::getCreationTime)
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Times: {}.", commitDeclaredTimes);
		LOGGER.debug("Real times: {}.", commitsReceptionTime.values());
		final ImmutableList<RevCommit> commitsOwn = commits.stream()
				.filter((c) -> !c.getAuthorIdent().getName().equals("Olivier Cailloux"))
				.collect(ImmutableList.toImmutableList());
		LOGGER.info("All: {}; own: {}.", toOIds(commits), toOIds(commitsOwn));
		final Predicate<? super RevCommit> byGH = MarkHelper::committerIsGitHub;
		final ImmutableList<RevCommit> commitsManual = commitsOwn.stream().filter(byGH.negate())
				.collect(ImmutableList.toImmutableList());
		final String comment = (!commitsManual.isEmpty()
				? "Using command line: " + commitsManual.iterator().next().getName()
				: "No commits using command line");
		final double points = (!commitsManual.isEmpty()) ? COMMIT.getMaxPoints() : COMMIT.getMinPoints();
		final Mark commitMark = Mark.of(COMMIT, points, comment);
		return commitMark;
	}

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExExtractorCriterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));

		LOGGER.debug("Tardiness: {}.", tardiness);
		final long secondsLate = tardiness.toSeconds();
		return -0.05d / 20d * maxGrade * secondsLate;
	}

	private ImmutableList<String> toOIds(Collection<RevCommit> commits) {
		return commits.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList());
	}
}
