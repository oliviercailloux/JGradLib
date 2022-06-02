package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ByTimeGrader<X extends Exception> implements Grader<X> {

	public static class PreparedGrader<X extends Exception> {
		private final GitFileSystemHistory whole;
		private GitFsGrader<X> grader;
		private GitHubUsername author;
		private final GradeModifier penalizerModifier;
		private String commentGeneralCapped;
		private ZonedDateTime deadline;

		public PreparedGrader(GitFsGrader<X> grader, GitHubUsername author, GradeModifier modifier,
				GitFileSystemHistory whole, String commentGeneralCapped, ZonedDateTime deadline) {
			this.grader = grader;
			this.author = author;
			this.penalizerModifier = modifier;
			this.whole = checkNotNull(whole);
			this.commentGeneralCapped = commentGeneralCapped;
			this.deadline = deadline;
		}

		public MarksTree grade(Instant timeCap) throws X {
			try {
				final GitFileSystemHistory capped = whole.filter(r -> !whole.getCommitDate(r).isAfter(timeCap),
						timeCap);

				final MarksTree grade = grader.grade(capped);

				final Mark userGrade = DeadlineGrader.getUsernameGrade(whole, author).asNew();
				final MarksTree gradeWithUser = MarksTree
						.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

				return penalizerModifier.modify(gradeWithUser, timeCap);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		public Criterion asCrit(Instant i) {
			return Criterion.given(asString(i));
		}

		public String asString(Instant i) {
			final String cappingAt = i.equals(Instant.MAX) ? "No capping"
					: i.equals(Instant.MIN) ? "Capping at MIN"
							: ("Capping at " + i.atZone(deadline.getZone()).toString());
			return cappingAt + commentGeneralCapped;
		}

		public String getCommentGeneralCapped() {
			return commentGeneralCapped;
		}

		public GitFileSystemHistory getWhole() {
			return whole;
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ByTimeGrader.class);

	private static final boolean IGNORE_FROM_GITHUB = true;

	public static final Criterion C_USER_NAME = Criterion.given("user.name");

	public static final Criterion C_GRADE = Criterion.given("Grade");

	public static <X extends Exception> ByTimeGrader<X> using(ZonedDateTime deadline, GitFsGrader<X> grader,
			GradeModifier penalizerModifier, double userGradeWeight) {
		return new ByTimeGrader<>(deadline, grader, penalizerModifier, userGradeWeight);
	}

	private final ZonedDateTime deadline;
	private final GitFsGrader<X> grader;
	private final GradeModifier penalizerModifier;

	private final double userGradeWeight;

	private ByTimeGrader(ZonedDateTime deadline, GitFsGrader<X> grader, GradeModifier penalizerModifier,
			double userGradeWeight) {
		this.deadline = checkNotNull(deadline);
		this.grader = checkNotNull(grader);
		this.penalizerModifier = checkNotNull(penalizerModifier);
		this.userGradeWeight = userGradeWeight;
	}

	@Override
	public MarksTree grade(GitHubUsername author, GitFileSystemHistory history) throws X {
		try {
			return gradeExc(author, history);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private MarksTree gradeExc(GitHubUsername author, GitFileSystemHistory history) throws IOException, X {
		final PreparedGrader<X> preparedGrader = prepared(author, history);

		final ImmutableSortedSet<Instant> consideredTimestamps = getTimestamps(preparedGrader.getWhole(),
				deadline.toInstant(), Instant.MAX);

		final ImmutableBiMap.Builder<Instant, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
		for (Instant timeCap : consideredTimestamps) {
			final MarksTree perhapsPenalized = preparedGrader.grade(timeCap);
			byTimeBuilder.put(timeCap, perhapsPenalized);
		}
		final ImmutableBiMap<Instant, MarksTree> byTime = byTimeBuilder.build();

		final MarksTree byTimeGrade;
		if (byTime.isEmpty()) {
			byTimeGrade = Mark.zero(String.format("No commit found%s", preparedGrader.getCommentGeneralCapped()));
		} else {
			final ImmutableMap<Criterion, MarksTree> subsByTime = byTime.keySet().stream()
					.collect(ImmutableMap.toImmutableMap(i -> preparedGrader.asCrit(i), byTime::get));
			byTimeGrade = MarksTree.composite(subsByTime);
		}
		return byTimeGrade;
	}

	public PreparedGrader<X> prepared(GitHubUsername author, GitFileSystemHistory history) throws IOException {
		final Optional<Instant> earliestTimeCommitByGitHub = earliestTimeCommitByGitHub(history);
		final GitFileSystemHistory beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub).map(
				t -> history.filter(c -> history.asGitHistory().getTimestamp(c.getCommit().getId()).isBefore(t), t))
				.orElse(history);
		final String commentGeneralCapped = earliestTimeCommitByGitHub
				.map(t -> "; ignored commits after " + t.atZone(deadline.getZone()).toString() + ", sent by GitHub")
				.orElse("");

		final PreparedGrader<X> preparedGrader = new PreparedGrader<>(grader, author, penalizerModifier,
				beforeCommitByGitHub, commentGeneralCapped, deadline);
		return preparedGrader;
	}

	private Optional<Instant> earliestTimeCommitByGitHub(GitFileSystemHistory history) throws IOException {
		LOGGER.debug("Found {} commits (total).", history.getGraph().nodes().size());
		checkTimes(history, deadline.toInstant());

		/* GitHub creates the very first commit when importing from a template. */
		final Optional<Instant> earliestTimeCommitByGitHub;
		if (IGNORE_FROM_GITHUB) {
			earliestTimeCommitByGitHub = history.filter(JavaMarkHelper::committerIsGitHub)
					.filter(c -> !history.getRoots().contains(c)).asGitHistory().getTimestamps().values().stream()
					.min(Comparator.naturalOrder());
		} else {
			earliestTimeCommitByGitHub = Optional.empty();
		}
		return earliestTimeCommitByGitHub;
	}

	@Override
	public GradeAggregator getAggregator() {
		final GradeAggregator penalized = getPenalizedAggregator();
		final GradeAggregator maxAmongAttempts = GradeAggregator.max(penalized);
		return maxAmongAttempts;
	}

	public GradeAggregator getPenalizedAggregator() {
		final GradeAggregator main = getUserNamedAggregator(grader, userGradeWeight);
		final GradeAggregator penalized;
		final boolean withTimePenalty = penalizerModifier instanceof GradePenalizer;
		if (withTimePenalty) {
			penalized = GradeAggregator.parametric(GradePenalizer.C_MAIN, GradePenalizer.C_LATENESS, main);
		} else {
			penalized = main;
		}
		return penalized;
	}

	private static GradeAggregator getUserNamedAggregator(GitFsGrader<?> grader, double userGradeWeight) {
		final GradeAggregator basis = grader.getAggregator();
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(ByTimeGrader.C_USER_NAME, userGradeWeight, ByTimeGrader.C_GRADE, 1d - userGradeWeight),
				ImmutableMap.of(ByTimeGrader.C_GRADE, basis));
	}

	private static void checkTimes(GitFileSystemHistory history, Instant deadline) {
		/*
		 * Check whether has put late branches on every ending commits: some leaf has a
		 * pointer that is not main or master. And it is late. OR rather simply use
		 * commit times and try to detect inconsistencies: commit time on time but push
		 * date late.
		 */
		final ImmutableSet<GitPathRoot> refs = IO_UNCHECKER.getUsing(history::getRefs);
		final ImmutableMap<GitPathRoot, Instant> commitDates = refs.stream().collect(ImmutableMap.toImmutableMap(p -> p,
				IO_UNCHECKER.wrapFunction(p -> p.getCommit().getCommitterDate().toInstant())));
		final ImmutableMap<GitPathRoot, Instant> pushDates = refs.stream()
				.collect(ImmutableMap.toImmutableMap(p -> p, IO_UNCHECKER
						.wrapFunction(p -> history.getPushDates().getOrDefault(p.getCommit().getId(), Instant.MIN))));
		final Map<GitPathRoot, Instant> pushDatesLate = Maps.filterValues(pushDates, i -> i.isAfter(deadline));
		final Map<GitPathRoot, Instant> commitsOnTime = Maps.filterValues(commitDates, i -> !i.isAfter(deadline));

		final Map<GitPathRoot, Instant> contradictory = Maps.filterKeys(pushDatesLate,
				r -> commitsOnTime.containsKey(r));
		if (!contradictory.isEmpty()) {
			LOGGER.info("Commit times: {}.", commitDates);
			LOGGER.info("Push times: {}.", pushDates);
			LOGGER.info("Pushed late but committed on time: {}; commit times {}.", contradictory,
					Maps.filterKeys(commitsOnTime, r -> pushDatesLate.containsKey(r)));
		}
	}

	/**
	 * Considers as “commit time” every times given by the git history AND by the
	 * push dates (something may be pushed at some date that does not appear in the
	 * git history, as it may have being overwritten by later commits: in case the
	 * commits were all pushed at some time but a branch was later put on some
	 * commit not ending the series, for example).
	 *
	 * @param history
	 * @return the latest commit time that is on time, that is, the latest commit
	 *         time within [MIN, deadline], if it exists, and the late commit times,
	 *         that is, every times of commits falling in the range (deadline, max].
	 */
	public static ImmutableSortedSet<Instant> getTimestamps(GitFileSystemHistory history, Instant deadline,
			Instant max) {
		final ImmutableSortedSet<Instant> timestamps = Streams
				.concat(history.asGitHistory().getTimestamps().values().stream(),
						history.getPushDates().values().stream())
				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
		final Instant latestCommitTimeOnTime = Optional.ofNullable(timestamps.floor(deadline)).orElse(Instant.MIN);
		final ImmutableSortedSet<Instant> consideredTimestamps = timestamps.subSet(latestCommitTimeOnTime, true, max,
				true);
		verify(consideredTimestamps.isEmpty() == history.getGraph().nodes().isEmpty());
		return consideredTimestamps;
	}

}
