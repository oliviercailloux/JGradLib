package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
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

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;

/**
 * Is given a deadline. And a penalizer. Function that grades a git work. Cap at
 * various points (obtain pairs of git work and lateness); ask penalizer, obtain
 * grade. Then take the best grade and aggregates.
 */
public class DeadlineGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGrader.Penalizer.class);

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

	public static DeadlineGrader given(GitGrader grader, ZonedDateTime deadline) {
		return new DeadlineGrader(grader, deadline, DeadlineGrader::defaultPenalize);
	}

	private final GitGrader grader;
	private final ZonedDateTime deadline;
	private final Penalizer penalizer;

	public DeadlineGrader(GitGrader grader, ZonedDateTime deadline, Penalizer penalizer) {
		this.grader = checkNotNull(grader);
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
		final GitFileSystemHistory onTimeAndManual = history.filter(r -> !history.getCommitDate(r).isAfter(timeCap));
		final IGrade grade = grader.grade(GitWork.given(author, onTimeAndManual));
		final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
		final IGrade penalizedGrade = penalizer.penalize(lateness, grade);
		return penalizedGrade;
	}

	/**
	 * @return the latest instant weakly before the deadline, or the deadline if
	 *         there are none such instants.
	 */
	private Instant getLatestBeforeDeadline(ImmutableSortedSet<Instant> timestamps) {
		final ImmutableSortedSet<Instant> toDeadline = timestamps.headSet(deadline.toInstant(), true);
		LOGGER.debug("All timestamps: {}, picking those before {} results in: {}.", timestamps, deadline.toInstant(),
				toDeadline);
		final Instant considerFrom;
		if (toDeadline.isEmpty()) {
			considerFrom = deadline.toInstant();
		} else {
			considerFrom = toDeadline.last();
		}
		return considerFrom;
	}

	private ImmutableSortedSet<Instant> fromJustBeforeDeadline(GitFileSystemHistory history) {
		final ImmutableSortedSet<Instant> toConsider;
		{
			final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getCommitDates().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final Instant considerFrom = getLatestBeforeDeadline(timestamps);
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
		return MoreObjects.toStringHelper(this).add("Grader", grader).add("Deadline", deadline)
				.add("Penalizer", penalizer).toString();
	}

}
