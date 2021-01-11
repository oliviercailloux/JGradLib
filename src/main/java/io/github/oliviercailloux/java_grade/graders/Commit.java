package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.utils.Utils;

public class Commit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Commit.class);

	public IGrade grade(RepositoryCoordinates coordinates, ZonedDateTime deadline) throws IOException {
		final FileRepository repository = GitCloner.create().download(coordinates.asGitUri(),
				Utils.getTempDirectory().resolve(coordinates.getRepositoryName()));

		final GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository);

		final GitHistory pushHistory;
		{
			final GitHubHistory gitHubHistory;
			try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
				gitHubHistory = fetcher.getReversedGitHubHistory(coordinates);
			}
			if (!gitHubHistory.getPatchedPushCommits().nodes().isEmpty()) {
				LOGGER.warn("Patched: {}.", gitHubHistory.getPatchedPushCommits());
			}
			pushHistory = gitHubHistory.getConsistentPushHistory();
			verify(pushHistory.getGraph().equals(Utils.asImmutableGraph(gitFs.getCommitsGraph(),
					IO_UNCHECKER.wrapFunction(r -> r.getCommit().getId()))));
		}

		final GitFileSystemHistory history = GitFileSystemHistory.create(gitFs, pushHistory);

		return grade(history, deadline);
	}

	public IGrade grade(GitFileSystemHistory history, ZonedDateTime deadline) {
		final Instant tooLate = deadline.toInstant().plus(Duration.ofMinutes(5));
		final ImmutableSet<Instant> toConsider;
		{
			final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getCommitDates().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final Instant lastOnTime = timestamps.headSet(deadline.toInstant(), true).last();
			toConsider = timestamps.tailSet(lastOnTime).headSet(tooLate);
		}

		final ImmutableMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableMap.builder();
		for (Instant timeCap : toConsider) {
			final Predicate<ObjectId> onTime = o -> !history.getCommitDate(o).isAfter(timeCap);
			final GitFileSystemHistory filteredHistory = history.filter(onTime);
			final IGrade grade = grade(filteredHistory);

			final IGrade penalizedGrade;
			final Duration lateness = Duration.between(timeCap, deadline);
			if (lateness.isNegative()) {
				final double fractionPenalty = -lateness.getSeconds() / 300d;
				verify(0d < fractionPenalty);
				verify(fractionPenalty < 1d);
				penalizedGrade = WeightingGrade.from(ImmutableSet.of(
						CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d - fractionPenalty),
						CriterionGradeWeight.from(Criterion.given("Time penalty"), Mark.zero("Lateness: " + lateness),
								fractionPenalty)));
			} else {
				penalizedGrade = grade;
			}
			byTimeBuilder.put(timeCap, penalizedGrade);
		}
		final ImmutableMap<Instant, IGrade> byTime = byTimeBuilder.build();
		final Optional<IGrade> bestGrade = byTime.values().stream().min(Comparator.comparing(IGrade::getPoints));
		final IGrade finalGrade;
		if (bestGrade.isEmpty()) {
			finalGrade = Mark.zero("No commit found before " + tooLate.toString());
		} else {
			final IGrade main = bestGrade.get();
			final Instant mainInstant = byTime.entrySet().stream().filter(e -> e.getValue().equals(main))
					.map(Map.Entry::getKey).collect(MoreCollectors.onlyElement());
			final ImmutableSet<CriterionGradeWeight> grades = byTime.entrySet().stream().map(e -> CriterionGradeWeight
					.from(Criterion.given("Cap at " + e.getKey()), e.getValue(), e.getValue().equals(main) ? 1d : 0d))
					.collect(ImmutableSet.toImmutableSet());
			finalGrade = WeightingGrade.from(grades, "Using best grade, from " + mainInstant.toString());
		}
		return finalGrade;
	}

	/**
	 * @param gitFs
	 * @param pushHistory
	 * @param timeCap     ignore commits strictly after that time.
	 * @return
	 * @throws IOException
	 */
	private WeightingGrade grade(GitFileSystemHistory filteredHistory) throws UncheckedIOException {
		final GitFileSystem gitFs = filteredHistory.getGitFilesystem();

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
//		final Graph<GitPathRoot> filteredCommitsGraph;
//		{
//			final ImmutableGraph<GitPathRoot> commitsGraph = gitFs.getCommitsGraph();
//			final Function<GitPathRoot, ? extends ObjectId> getId = IO_UNCHECKER
//					.wrapFunction(p -> p.getCommit().getId());
//			filteredCommitsGraph = Graphs.inducedSubgraph(commitsGraph, commitsGraph.nodes().stream()
//					.filter(r -> onTime.test(getId.apply(r))).collect(ImmutableSet.toImmutableSet()));
//		}
//		final GitHistory filteredPushHistory = pushHistory.filter(onTime);

		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Not empty"),
				Mark.binary(!filteredHistory.getGraph().nodes().isEmpty()), 1d));

		final Pattern coucouPattern = Pattern.compile("(\\h\\v)*coucou(\\h\\v)*");
		{
			final WeightingGrade coucouCommit = WeightingGrade.proportional(Criterion.given("'afile.txt' content"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("afile.txt"), coucouPattern))),
					Criterion.given("'coucou' content"),
					Mark.binary(matches(gitFs.getAbsolutePath("/refs/heads/coucou//afile.txt"), coucouPattern)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
		}
		{
			final Pattern digitPattern = Pattern.compile("(\\h\\v)*\\d+(\\h\\v)*");
			final CriterionGradeWeight myIdContent = CriterionGradeWeight.from(Criterion.given("'myid.txt' content"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern))),
					1d);
			final CriterionGradeWeight myIdAndAFileContent = CriterionGradeWeight.from(
					Criterion.given("'myid.txt' and 'afile.txt' content"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern)
									&& matches(r.resolve("afile.txt"), coucouPattern))),
					1d);
			final CriterionGradeWeight masterContent = CriterionGradeWeight
					.from(Criterion.given("'master' content"),
							Mark.binary(matches(gitFs.getAbsolutePath("/refs/heads/master//myid.txt"), digitPattern)
									&& matches(gitFs.getAbsolutePath("/refs/heads/master//afile.txt"), coucouPattern)),
							2d);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'master'"),
					WeightingGrade.from(ImmutableSet.of(myIdContent, myIdAndAFileContent, masterContent)), 3d));
		}
		{
			final WeightingGrade commit = WeightingGrade.proportional(Criterion.given("'another file.txt' exists"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> Files.exists(r.resolve("sub/a/another file.txt")))),
					Criterion.given("'dev' content"),
					Mark.binary(Files.exists(gitFs.getAbsolutePath("/refs/heads/dev//sub/a/another file.txt"))));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 3d));
		}

		return WeightingGrade.from(gradeBuilder.build());
	}

	private boolean matches(GitPath path, Pattern regExp) throws UncheckedIOException {
		return Files.exists(path)
				&& regExp.matcher(IO_UNCHECKER.getUsing(() -> Files.readString(path)).strip()).matches();
	}
}
