package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexGrader<X extends Exception> implements Grader<X> {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ComplexGrader.class);

	private static final boolean IGNORE_FROM_GITHUB = true;

	public static final Criterion C_USER_NAME = Criterion.given("user.name");

	public static final Criterion C_GRADE = Criterion.given("Grade");

	public static <X extends Exception> ComplexGrader<X> using(GitFsGrader<X> grader, GradeModifier penalizerModifier,
			double userGradeWeight) {
		return new ComplexGrader<>(grader, penalizerModifier, userGradeWeight);
	}

	private final GitFsGrader<X> grader;
	private final GradeModifier penalizerModifier;

	private final double userGradeWeight;

	private ComplexGrader(GitFsGrader<X> grader, GradeModifier penalizerModifier, double userGradeWeight) {
		this.grader = checkNotNull(grader);
		this.penalizerModifier = checkNotNull(penalizerModifier);
		this.userGradeWeight = userGradeWeight;
	}

	@Override
	public MarksTree grade(GitHubUsername author, GitHistorySimple history) throws X {
		try {
			return gradeExc(author, history);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private MarksTree gradeExc(GitHubUsername author, GitHistorySimple capped) throws IOException, X {
		verify(!capped.graph().nodes().isEmpty());

		final Instant timeCap = ByTimeGrader.cappedAt(capped);

		final MarksTree grade = grader.grade(capped);

		final Mark userGrade = DeadlineGrader.getUsernameGrade(capped, author).asNew();
		final MarksTree gradeWithUser = MarksTree.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

		return penalizerModifier.modify(gradeWithUser, timeCap);
	}

	@Override
	public GradeAggregator getAggregator() {
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
		return GradeAggregator.staticAggregator(ImmutableMap.of(ComplexGrader.C_USER_NAME, userGradeWeight,
				ComplexGrader.C_GRADE, 1d - userGradeWeight), ImmutableMap.of(ComplexGrader.C_GRADE, basis));
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
				IO_UNCHECKER.wrapFunction(p -> p.getCommit().committerDate().toInstant())));
		final ImmutableMap<GitPathRoot, Instant> pushDates = refs.stream().collect(ImmutableMap.toImmutableMap(p -> p,
				IO_UNCHECKER.wrapFunction(p -> history.getPushDates().getOrDefault(p.getCommit().id(), Instant.MIN))));
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
