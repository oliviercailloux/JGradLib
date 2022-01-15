package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
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
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGitHistoryGrader<X extends Exception> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchGitHistoryGrader.class);
	private final String prefix;
	private final ZonedDateTime deadline;
	private final Function<GitFileSystemHistory, IGrade, X> grader;

	public BatchGitHistoryGrader(String prefix, ZonedDateTime deadline,
			Throwing.Function<GitFileSystemHistory, IGrade, X> grader) {
		this.prefix = checkNotNull(prefix);
		this.deadline = checkNotNull(deadline);
		this.grader = checkNotNull(grader);
	}

	public void proceed() throws X, IOException {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(prefix);
		final ImmutableSet<RepositoryCoordinatesWithPrefix> coordinatess = fetcher.fetch();
		final LinearPenalizer penalizer = LinearPenalizer.DEFAULT_PENALIZER;
		final GitCloner cloner = GitCloner.create().setCheckCommonRefsAgree(false);

		for (RepositoryCoordinatesWithPrefix coordinates : coordinatess) {
			final Path dir = Utils.getTempDirectory().resolve(coordinates.getRepositoryName());
			final GitHubUsername author = GitHubUsername.given(coordinates.getUsername());
			try (FileRepository repository = cloner.download(coordinates.asGitUri(), dir);
					GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository);
					GitHubFetcherQL fetcherQl = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {

				final GitHubHistory gitHubHistory = fetcherQl.getReversedGitHubHistory(coordinates);
				final GitHistory pushHistory = gitHubHistory.getConsistentPushHistory();

//		final GitWork work = GitWork.given(author, history);
				final GitFileSystemHistory history = GitFileSystemHistory.create(gitFs, pushHistory);

				final Optional<Instant> earliestTimeCommitByGitHub = history.filter(JavaMarkHelper::committerIsGitHub)
						.asGitHistory().getTimestamps().values().stream().min(Comparator.naturalOrder());
				final GitFileSystemHistory filtered;
				if (earliestTimeCommitByGitHub.isPresent()) {
					filtered = history.filter(c -> history.asGitHistory().getTimestamp(c.getCommit().getId())
							.isBefore(earliestTimeCommitByGitHub.orElseThrow()));
				} else {
					filtered = history;
				}

				final String commentGeneralCapped = earliestTimeCommitByGitHub
						.map(t -> "Ignored commits after " + t.toString() + ", sent by GitHub.").orElse("");

				final ImmutableBiMap<Instant, IGrade> byTime = getGradesByTime(filtered, penalizer);

				final Optional<IGrade> bestGrade = byTime.values().stream()
						.max(Comparator.comparing(IGrade::getPoints));
				final IGrade integratedGrade;
				if (bestGrade.isEmpty()) {
					integratedGrade = Mark.zero("No commit found.");
				} else if (byTime.size() == 1) {
					integratedGrade = bestGrade.get();
				} else {
					integratedGrade = DeadlineGrader.getBestAndSub(bestGrade.get(), byTime, deadline);
				}

				final String separator = integratedGrade.getComment().isEmpty() || commentGeneralCapped.isEmpty() ? ""
						: "; ";
				integratedGrade.withComment(integratedGrade.getComment() + separator + commentGeneralCapped);

			}
		}
	}

	public ImmutableBiMap<Instant, IGrade> getGradesByTime(GitFileSystemHistory history, LinearPenalizer penalizer)
			throws IOException, X {
		final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getTimestamps().values().stream()
				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
		/*
		 * Consider the latest commit time that is on time, that is, the latest commit
		 * time within [MIN, deadline], if it exists. Also consider the late commit
		 * times, that is, every times of commits falling in the range (deadline, MAX].
		 */
		final Instant latestCommitTimeOnTime = Optional.ofNullable(timestamps.floor(deadline.toInstant()))
				.orElse(Instant.MIN);
		final ImmutableSortedSet<Instant> consideredTimestamps = timestamps.subSet(latestCommitTimeOnTime, true,
				Instant.MAX, true);
		verify(consideredTimestamps.isEmpty() == history.getGraph().nodes().isEmpty());

		final ImmutableBiMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableBiMap.builder();
		for (Instant timeCap : consideredTimestamps) {
			final GitFileSystemHistory capped = history.filter(r -> !history.getCommitDate(r).isAfter(timeCap));
			final IGrade grade = grader.apply(capped);
			final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
			final IGrade penalizedForTimeGrade = penalizer.penalize(lateness, grade);
			byTimeBuilder.put(timeCap, penalizedForTimeGrade);
		}
		return byTimeBuilder.build();
	}
}
