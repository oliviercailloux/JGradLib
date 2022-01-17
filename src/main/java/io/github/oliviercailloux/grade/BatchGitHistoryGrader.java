package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarizer;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGitHistoryGrader<X extends Exception> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchGitHistoryGrader.class);
	private static final double USER_GRADE_WEIGHT = 0.5d / 20d;
	private final String prefix;
	private final ZonedDateTime deadline;
	private final Function<GitFileSystemHistory, IGrade, X> grader;

	public BatchGitHistoryGrader(String prefix, ZonedDateTime deadline,
			Throwing.Function<GitFileSystemHistory, IGrade, X> grader) {
		this.prefix = checkNotNull(prefix);
		this.deadline = checkNotNull(deadline);
		this.grader = checkNotNull(grader);
	}

	public ImmutableMap<GitHubUsername, IGrade> getGrades() throws X, IOException {
		return getGrades(TOptional.empty());
	}

	public ImmutableMap<GitHubUsername, IGrade> getAndWriteGrades(Path out) throws X, IOException {
		return getGrades(TOptional.of(out));
	}

	private ImmutableMap<GitHubUsername, IGrade> getGrades(TOptional<Path> outOpt) throws X, IOException {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(prefix);
		final ImmutableSet<RepositoryCoordinatesWithPrefix> coordinatess = fetcher.fetch();
		final LinearPenalizer penalizer = LinearPenalizer.DEFAULT_PENALIZER;
		final GitCloner cloner = GitCloner.create().setCheckCommonRefsAgree(false);

		final LinkedHashMap<GitHubUsername, IGrade> builder = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix coordinates : coordinatess) {
			final Path dir = Utils.getTempDirectory().resolve(coordinates.getRepositoryName());
			final GitHubUsername author = GitHubUsername.given(coordinates.getUsername());
			final ImmutableBiMap<Instant, IGrade> byTime;
			final String commentGeneralCapped;
			try (FileRepository repository = cloner.download(coordinates.asGitUri(), dir);
					GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository);
					GitHubFetcherQL fetcherQl = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {

				final GitHubHistory gitHubHistory = fetcherQl.getReversedGitHubHistory(coordinates);
				final GitHistory pushHistory = gitHubHistory.getConsistentPushHistory();

				final GitFileSystemHistory beforeCommitByGitHub;
				{
					final GitFileSystemHistory history = GitFileSystemHistory.create(gitFs, pushHistory);

					final Optional<Instant> earliestTimeCommitByGitHub = history
							.filter(JavaMarkHelper::committerIsGitHub).asGitHistory().getTimestamps().values().stream()
							.min(Comparator.naturalOrder());
					beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
							.map(t -> history.filter(
									c -> history.asGitHistory().getTimestamp(c.getCommit().getId()).isBefore(t)))
							.orElse(history);

					commentGeneralCapped = earliestTimeCommitByGitHub
							.map(t -> "Ignored commits after " + t.toString() + ", sent by GitHub.").orElse("");
				}

				final ImmutableSortedSet<Instant> consideredTimestamps = getTimestamps(beforeCommitByGitHub);

				final ImmutableBiMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableBiMap.builder();
				for (Instant timeCap : consideredTimestamps) {
					final GitFileSystemHistory capped = beforeCommitByGitHub
							.filter(r -> !beforeCommitByGitHub.getCommitDate(r).isAfter(timeCap));

					final IGrade grade = grader.apply(capped);

					final Mark userGrade = DeadlineGrader.getUsernameGrade(beforeCommitByGitHub, author);
					final WeightingGrade gradeWithUser = WeightingGrade.from(ImmutableSet.of(
							CriterionGradeWeight.from(Criterion.given("user.name"), userGrade, USER_GRADE_WEIGHT),
							CriterionGradeWeight.from(Criterion.given("main"), grade, 1d - USER_GRADE_WEIGHT)));

					final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
					final IGrade penalizedForTimeGrade = penalizer.penalize(lateness, gradeWithUser);
					byTimeBuilder.put(timeCap, penalizedForTimeGrade);
				}
				byTime = byTimeBuilder.build();
			}

			final IGrade bestGrade = byTime.values().stream().max(Comparator.comparing(IGrade::getPoints))
					.orElse(Mark.zero("No commit found."));
			final String separator = bestGrade.getComment().isEmpty() || commentGeneralCapped.isEmpty() ? "" : "; ";
			final IGrade bestGradeCommented = bestGrade
					.withComment(bestGrade.getComment() + separator + commentGeneralCapped);

			final IGrade integratedGrade;
			if (byTime.size() <= 1) {
				integratedGrade = bestGradeCommented;
			} else {
				integratedGrade = DeadlineGrader.getBestAndSub(bestGradeCommented, byTime, deadline);
			}
			builder.put(author, integratedGrade);

			outOpt.ifPresent(o -> write(builder, o));
		}

		return ImmutableMap.copyOf(builder);
	}

	/**
	 * @param history
	 * @return the latest commit time that is on time, that is, the latest commit
	 *         time within [MIN, deadline], if it exists, and the late commit times,
	 *         that is, every times of commits falling in the range (deadline, MAX].
	 */
	ImmutableSortedSet<Instant> getTimestamps(GitFileSystemHistory history) {
		final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getTimestamps().values().stream()
				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
		final Instant latestCommitTimeOnTime = Optional.ofNullable(timestamps.floor(deadline.toInstant()))
				.orElse(Instant.MIN);
		final ImmutableSortedSet<Instant> consideredTimestamps = timestamps.subSet(latestCommitTimeOnTime, true,
				Instant.MAX, true);
		verify(consideredTimestamps.isEmpty() == history.getGraph().nodes().isEmpty());
		return consideredTimestamps;
	}

	private void write(Map<GitHubUsername, IGrade> grades, Path out) throws IOException {
		final ImmutableMap<String, IGrade> gradesString = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey().toString(), Entry::getValue));
		Files.writeString(out, JsonbUtils.toJsonObject(gradesString, JsonGrade.asAdapter()).toString());
		final Summarizer summarizer = Summarizer.create().setInputPath(out)
				.setCsvOutputPath(Path.of("grades " + prefix + ".csv"))
				.setHtmlOutputPath(Path.of("grades " + prefix + ".html"));
		summarizer.summarize();
	}
}
