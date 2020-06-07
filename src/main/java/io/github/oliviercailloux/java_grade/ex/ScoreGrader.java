package io.github.oliviercailloux.java_grade.ex;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.bytecode.Compiler;
import io.github.oliviercailloux.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.exceptions.Try;
import io.github.oliviercailloux.exceptions.TryVoid;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.samples.scorers.ScoreKeeper;
import io.github.oliviercailloux.samples.scorers.ScoreManager;
import io.github.oliviercailloux.utils.Utils;

public class ScoreGrader {
	public static final double COMPILE_POINTS = 0.5d / 20d;

	static enum LocalCriterion implements Criterion {
		IMPL, FACTORY, STARTS_AT_TEN, INCREMENT, MULTIPLIES, MULTIPLIES_REALLY, LISTENER;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ScoreKeeper.class);

	private static final String PREFIX = "scorers";

	private static final boolean FAKE = false;

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-05-25T00:00:00+02:00").toInstant();

	/**
	 * NB for this to work, we need to have the interfaces in the class path with
	 * the same name as the one used by the student’s projects, namely, …samples.…
	 *
	 */
	public static void main(String[] args) throws Exception {
		final Path outDir = Paths.get("../../Java L3/");

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final ScoreGrader grader = new ScoreGrader();
		if (FAKE) {
			grader.deadline = Instant.MAX;
		}

		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		final Path projectsBaseDir = Utils.getTempDirectory().resolve(PREFIX);
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final Path projectDir = projectsBaseDir.resolve(repository.getRepositoryName());
			gradesB.put(repository.getUsername(), grader.grade(repository, projectDir));
			Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir);
		}
	}

	Instant deadline;

	ScoreGrader() {
		deadline = DEADLINE;
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord, Path projectDir) throws IOException {
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		checkArgument(gitHubHistory.getPatchedKnowns().nodes().isEmpty());

		final Optional<Instant> lastCommitInstantBeforeDeadlineOpt = gitHubHistory.getCommitDates().values().stream()
				.filter(i -> i.isBefore(deadline)).max(Comparator.naturalOrder());
		if (lastCommitInstantBeforeDeadlineOpt.isEmpty()) {
			return Mark.zero("Found no master commit.");
		}

		final Instant lastCommitGitHubInstantBeforeDeadline = lastCommitInstantBeforeDeadlineOpt.get();
		final ImmutableSet<ObjectId> lastCommitsBeforeDeadline = gitHubHistory.getCommitDates().asMultimap().inverse()
				.get(lastCommitGitHubInstantBeforeDeadline);
		verify(!lastCommitsBeforeDeadline.isEmpty());
		final GitLocalHistory history = fs.getHistory();
		final Optional<ObjectId> lastCommitBeforeDeadlineOpt = lastCommitsBeforeDeadline.stream()
				.max(Comparator.comparing(o -> history.getCommitDateById(o)));
		verify(lastCommitBeforeDeadlineOpt.isPresent());
		final ObjectId lastCommitBeforeDeadline = lastCommitBeforeDeadlineOpt.get();
		final Instant lastCommitInstantBeforeDeadline = history.getCommitDateById(lastCommitBeforeDeadline);
		verify(lastCommitsBeforeDeadline.stream().allMatch(o -> o.equals(lastCommitBeforeDeadline)
				|| history.getCommitDateById(o).isBefore(lastCommitInstantBeforeDeadline)));

		final GitPath gitSourcePath = fs.getAbsolutePath(lastCommitBeforeDeadline.getName());

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		{
			final RevCommit master = fs.getCachedHistory().getCommit(lastCommitBeforeDeadline);
			gradeBuilder.put(JavaCriterion.ID, Mark.binary(JavaMarkHelper.committerAndAuthorIs(master, owner)));
		}

		final Path fileSourcePath = Utils.getTempUniqueDirectory("src-" + owner);
		Utils.copyRecursively(gitSourcePath, fileSourcePath, StandardCopyOption.REPLACE_EXISTING);

		final MavenManager mavenManager = new MavenManager();
		final boolean compiled = mavenManager.compile(fileSourcePath);
		if (compiled) {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled));

			final ImmutableList<Path> classPath = mavenManager.getClassPath(fileSourcePath);
			checkArgument(classPath.size() >= 2);
			final Path java = fileSourcePath.resolve("src/main/java/");
			final ImmutableList<Path> depsAndItself = ImmutableList.<Path>builder().add(java).addAll(classPath).build();

			final Path effectiveSourcePath = java
					.resolve("io/github/oliviercailloux/samples/scorers/MyScoreManager.java");
			final boolean suppressed = Files.readString(effectiveSourcePath).contains("@SuppressWarnings");
			final CompilationResult eclipseResult = Compiler.eclipseCompile(depsAndItself,
					ImmutableSet.of(effectiveSourcePath));
			verify(eclipseResult.compiled, eclipseResult.err);
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.binary(!suppressed && (eclipseResult.countWarnings() == 0),
					"", eclipseResult.err.replaceAll(fileSourcePath.toAbsolutePath().toString() + "/", "")));

			final IGrade implGrade = JavaGradeUtils.gradeSecurely(fileSourcePath.resolve(Path.of("target/classes/")),
					ScoreManager.class, this::grade);
			gradeBuilder.put(LocalCriterion.IMPL, implGrade);
		} else {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled, "", mavenManager.getCensoredOutput()));
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.zero());
			gradeBuilder.put(LocalCriterion.IMPL, Mark.zero());
		}

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(JavaCriterion.ID, 0.5d);
		builder.put(JavaCriterion.COMPILE, COMPILE_POINTS * 20d);
		builder.put(JavaCriterion.NO_WARNINGS, 1.5d);
		builder.put(LocalCriterion.IMPL, 17.5d);
		return WeightingGrade.from(subGrades, builder.build(), "Using commit " + lastCommitBeforeDeadline.getName());
	}

	private IGrade grade(Supplier<ScoreManager> factory) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
			{
				final ScoreManager instance = factory.get();
				final Try<Integer> startScore = Try.of(() -> instance.getCurrentScore());
				gradeBuilder.put(LocalCriterion.STARTS_AT_TEN, Mark.binary(startScore.equals(Try.success(10))));
			}
			{
				final ScoreManager instance = factory.get();
				final Try<Integer> startScore = Try.of(() -> instance.getCurrentScore());
				for (int i = 0; i < 22; ++i) {
					TryVoid.run(() -> instance.incrementScore());
				}
				final Try<Integer> endScore = Try.of(() -> instance.getCurrentScore());
				gradeBuilder.put(LocalCriterion.INCREMENT,
						Mark.binary(startScore.isSuccess() && endScore.equals(Try.success(startScore.get() + 22))));
			}
			{
				final ScoreManager instance = factory.get();
				final Try<Integer> startScore = Try.of(() -> instance.getCurrentScore());
				Try.of(() -> instance.getScoreMultiplier());
				final Try<ScoreKeeper> scoreMultiplierBy2 = Try.of(() -> instance.getScoreMultiplier());
				for (int i = 0; i < 147; ++i) {
					Try.of(() -> instance.getScoreMultiplier());
				}
				final Try<ScoreKeeper> scoreMultiplierBy150 = Try.of(() -> instance.getScoreMultiplier());
				for (int i = 0; i < 12; ++i) {
					TryVoid.run(() -> instance.incrementScore());
				}
				final Try<Integer> scoreMultipliedBy2 = scoreMultiplierBy2.map(m -> m.getCurrentScore());
				final Try<Integer> scoreMultipliedBy150 = scoreMultiplierBy150.map(m -> m.getCurrentScore());
				final boolean multiplied = startScore.isSuccess()
						&& scoreMultipliedBy150.equals(Try.success((startScore.get() + 12) * 150));
				gradeBuilder.put(LocalCriterion.MULTIPLIES, Mark.binary(multiplied));
				gradeBuilder.put(LocalCriterion.MULTIPLIES_REALLY,
						Mark.binary(multiplied && scoreMultipliedBy2.equals(Try.success((startScore.get() + 12) * 2))));
			}
			{
				final ScoreManager instance = factory.get();
				TryVoid.run(() -> instance.incrementScore());
				final ScoreIntegerListener listener1 = ScoreIntegerListener.newInstance();
				final ScoreIntegerListener listener2 = ScoreIntegerListener.newInstance();
				TryVoid.run(() -> instance.addListener(listener1));
				final int initCount = listener1.getCountCalled();
				TryVoid.run(() -> instance.incrementScore());
				TryVoid.run(() -> instance.addListener(listener2));
				for (int i = 0; i < 99; ++i) {
					TryVoid.run(() -> instance.incrementScore());
				}
				final int count100 = listener1.getCountCalled();
				final int count99 = listener2.getCountCalled();
				gradeBuilder.put(LocalCriterion.LISTENER,
						Mark.binary(initCount == 0 && count100 == 100 && count99 == 99));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		final ImmutableMap<Criterion, IGrade> grade = gradeBuilder.build();

		final ImmutableMap.Builder<Criterion, Double> weightsBuilder = ImmutableMap.builder();
		weightsBuilder.put(LocalCriterion.STARTS_AT_TEN, 2.5d);
		weightsBuilder.put(LocalCriterion.INCREMENT, 4d);
		weightsBuilder.put(LocalCriterion.MULTIPLIES, 4d);
		weightsBuilder.put(LocalCriterion.MULTIPLIES_REALLY, 1d);
		weightsBuilder.put(LocalCriterion.LISTENER, 6d);
		return WeightingGrade.from(grade, weightsBuilder.build());
	}

}
