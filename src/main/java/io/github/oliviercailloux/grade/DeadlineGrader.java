package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.fs.Commit;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.fs.GitPathRootSha;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.utils.Utils;

/**
 * Is given a deadline. And a penalizer. Function that grades a git work. Cap at
 * various points (obtain pairs of git work and lateness); ask penalizer, obtain
 * grade. Then take the best grade and aggregates.
 */
public class DeadlineGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGrader.Penalizer.class);

	private static class SimpleToGitGrader {

		private final Throwing.Function<Path, IGrade, IOException> simpleWorkGrader;

		private SimpleToGitGrader(Throwing.Function<Path, IGrade, IOException> simpleWorkGrader) {
			this.simpleWorkGrader = checkNotNull(simpleWorkGrader);
		}

		public IGrade grade(GitWork work) throws IOException {
			final GitFileSystemHistory history = work.getHistory();
			final CheckedStream<GitPathRoot, IOException> checkedCommits = CheckedStream
					.wrapping(history.getGraph().nodes().stream());
			final Optional<String> author = checkedCommits.map(GitPathRoot::getCommit).map(Commit::getAuthorName)
					.collect(Utils.singleOrEmpty());
			final Mark userGrade = Mark
					.binary(author.isPresent() && author.get().equals(work.getAuthor().getUsername()));
			final ImmutableSet<GitPathRootSha> latestTiedPathsOnTime = SimpleToGitGrader.getLatest(history);
			checkArgument(!latestTiedPathsOnTime.isEmpty());
			final IGrade mainGrade = CheckedStream.<GitPathRootSha, IOException>wrapping(latestTiedPathsOnTime.stream())
					.map(simpleWorkGrader).min(Comparator.comparing(IGrade::getPoints)).get();
			return WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("user.name"), userGrade, 1d),
							CriterionGradeWeight.from(Criterion.given("main"), mainGrade, 19d)));
		}

		/**
		 * Returns all latest commits that have no children, have been authored the
		 * latest among the remaining ones, and have been committed the latest among the
		 * remaining ones.
		 */
		private static ImmutableSet<GitPathRootSha> getLatest(GitFileSystemHistory history) throws IOException {
			final ImmutableSet<GitPathRootSha> leaves = history.getLeaves();
			// final GitFileSystemHistory leavesHistory = history.filter(c ->
			// leaves.contains(c));

			// final Instant latestAuthorDate = CheckedStream.<GitPathRoot,
			// IOException>wrapping(leaves.stream())
			// .map(GitPathRoot::getCommit).map(Commit::getAuthorDate).map(ZonedDateTime::toInstant)
			// .max(Comparator.naturalOrder()).orElse(timeCap);
			// final GitFileSystemHistory latestAuthoredHistory = leavesHistory
			// .filter(c ->
			// c.getCommit().getAuthorDate().toInstant().equals(latestAuthorDate));
			//
			// final Instant latestCommittedDate = CheckedStream.<GitPathRoot,
			// IOException>wrapping(leaves.stream())
			// .map(GitPathRoot::getCommit).map(Commit::getCommitterDate).map(ZonedDateTime::toInstant)
			// .max(Comparator.naturalOrder()).orElse(timeCap);
			// final GitFileSystemHistory latestAuthoredThenCommittedHistory =
			// latestAuthoredHistory
			// .filter(c ->
			// c.getCommit().getCommitterDate().toInstant().equals(latestCommittedDate));

			final Comparator<GitPathRootSha> byAuthorDate = Comparator
					.comparing(c -> IO_UNCHECKER.getUsing(() -> c.getCommit()).getAuthorDate());
			final Comparator<GitPathRootSha> byCommitDate = Comparator
					.comparing(c -> IO_UNCHECKER.getUsing(() -> c.getCommit()).getCommitterDate());
			final Throwing.Comparator<GitPathRootSha, IOException> byDate = (t1, t2) -> byAuthorDate
					.thenComparing(byCommitDate).compare(t1, t2);
			return Utils.<GitPathRootSha, IOException>getMaximalElements(leaves, byDate);
		}

	}

	private static interface Penalizer {
		public IGrade penalize(Duration lateness, IGrade grade);
	}

	private static IGrade defaultPenalize(Duration lateness, IGrade grade) {
		final IGrade penalizedGrade;
		if (!lateness.isNegative() && !lateness.isZero()) {
			final double fractionPenalty = Math.min(lateness.getSeconds() / 300d, 1d);
			verify(0d < fractionPenalty);
			verify(fractionPenalty <= 1d);
			penalizedGrade = WeightingGrade.from(
					ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d - fractionPenalty),
							CriterionGradeWeight.from(Criterion.given("Time penalty"),
									Mark.zero("Lateness: " + lateness), fractionPenalty)));
		} else {
			penalizedGrade = grade;
		}
		return penalizedGrade;
	}

	public static DeadlineGrader usingGitGrader(GitGrader grader, ZonedDateTime deadline) {
		return new DeadlineGrader(grader::grade, deadline, DeadlineGrader::defaultPenalize);
	}

	public static DeadlineGrader usingPathGrader(Throwing.Function<Path, IGrade, IOException> grader,
			ZonedDateTime deadline) {
		return new DeadlineGrader(new SimpleToGitGrader(grader)::grade, deadline, DeadlineGrader::defaultPenalize);
	}

	private final Throwing.Function<GitWork, IGrade, IOException> grader;
	private final ZonedDateTime deadline;
	private final Penalizer penalizer;

	private DeadlineGrader(Throwing.Function<GitWork, IGrade, IOException> gitWorkGrader, ZonedDateTime deadline,
			Penalizer penalizer) {
		this.grader = checkNotNull(gitWorkGrader);
		this.deadline = checkNotNull(deadline);
		this.penalizer = checkNotNull(penalizer);
	}

	public IGrade grade(GitWork work) throws IOException {

		final ImmutableMap<Instant, IGrade> byTime = getPenalizedGradesByCap(work);

		final Optional<IGrade> bestGrade = byTime.values().stream().max(Comparator.comparing(IGrade::getPoints));
		final IGrade finalGrade;
		if (bestGrade.isEmpty()) {
			finalGrade = Mark.zero("No commit found.");
		} else if (byTime.size() == 1) {
			finalGrade = bestGrade.get();
		} else {
			finalGrade = getBestAndSub(bestGrade.get(), byTime);
		}
		return finalGrade;
	}

	/**
	 * @return a weighting grade that shows the best grade with weight 1, and the
	 *         other grades with weight 0, and indicates for each of them where they
	 *         have been capped.
	 */
	private IGrade getBestAndSub(final IGrade best, final ImmutableMap<Instant, IGrade> byTime) {
		final IGrade finalGrade;
		final Instant mainInstant = byTime.entrySet().stream().filter(e -> e.getValue().equals(best))
				.map(Map.Entry::getKey).collect(MoreCollectors.onlyElement());
		final ImmutableSet<CriterionGradeWeight> grades = byTime.entrySet().stream()
				.map(e -> CriterionGradeWeight.from(
						Criterion.given("Cap at " + e.getKey().atZone(deadline.getZone()).toString()), e.getValue(),
						e.getValue().equals(best) ? 1d : 0d))
				.collect(ImmutableSet.toImmutableSet());
		finalGrade = WeightingGrade.from(grades,
				"Using best grade, from " + mainInstant.atZone(deadline.getZone()).toString());
		return finalGrade;
	}

	private ImmutableMap<Instant, IGrade> getPenalizedGradesByCap(GitWork work) throws IOException {
		final ImmutableSortedSet<Instant> toConsider = fromJustBeforeDeadline(work.getHistory());

		final ImmutableMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableMap.builder();
		for (Instant timeCap : toConsider) {
			final IGrade penalizedGrade = getPenalized(work, timeCap);
			byTimeBuilder.put(timeCap, penalizedGrade);
		}
		final ImmutableMap<Instant, IGrade> byTime = byTimeBuilder.build();
		verify(toConsider.isEmpty() == work.getHistory().getGraph().nodes().isEmpty());
		return byTime;
	}

	private IGrade getPenalized(GitWork work, Instant timeCap) throws IOException {
		final GitHubUsername author = work.getAuthor();
		final GitFileSystemHistory history = work.getHistory();
		checkArgument(!history.isEmpty());
		final GitFileSystemHistory onTime = history.filter(r -> !history.getCommitDate(r).isAfter(timeCap));
		checkArgument(!onTime.isEmpty());
		final IGrade grade = grader.apply(GitWork.given(author, onTime));
		final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
		final IGrade penalizedGrade = penalizer.penalize(lateness, grade);
		return penalizedGrade;
	}

	/**
	 * @return the latest instant weakly before the cap, or the cap itself if there
	 *         are none such instants.
	 */
	private Instant getLatestBefore(ImmutableSortedSet<Instant> timestamps, Instant cap) {
		final ImmutableSortedSet<Instant> toCap = timestamps.headSet(cap, true);
		LOGGER.debug("All timestamps: {}, picking those before {} results in: {}.", timestamps, cap, toCap);
		final Instant considerFrom;
		if (toCap.isEmpty()) {
			considerFrom = cap;
		} else {
			considerFrom = toCap.last();
		}
		return considerFrom;
	}

	private ImmutableSortedSet<Instant> fromJustBeforeDeadline(GitFileSystemHistory history) {
		final ImmutableSortedSet<Instant> toConsider;
		{
			final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getCommitDates().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final Instant considerFrom = getLatestBefore(timestamps, deadline.toInstant());
			toConsider = timestamps.tailSet(considerFrom);
		}
		/** Temporary patch in wait for a better adjustment of GitHub push dates. */
		final ImmutableSortedSet<Instant> adjustedConsider;
		if (!toConsider.isEmpty() && toConsider.first().equals(Instant.MIN)) {
			verify(toConsider.size() >= 2);
			LOGGER.warn("Ignoring MIN.");
			adjustedConsider = toConsider.tailSet(Instant.MIN, false);
		} else {
			adjustedConsider = toConsider;
		}
		LOGGER.debug("Given {}, to consider: {}, adjusted: {}.", history, toConsider, adjustedConsider);
		verify(toConsider.isEmpty() == history.getGraph().nodes().isEmpty());
		return adjustedConsider;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof DeadlineGrader)) {
			return false;
		}
		final DeadlineGrader t2 = (DeadlineGrader) o2;
		return grader.equals(t2.grader) && deadline.equals(t2.deadline) && penalizer.equals(t2.penalizer);
	}

	@Override
	public int hashCode() {
		return Objects.hash(grader, deadline, penalizer);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Git grader", grader).add("Deadline", deadline)
				.add("Penalizer", penalizer).toString();
	}

}
