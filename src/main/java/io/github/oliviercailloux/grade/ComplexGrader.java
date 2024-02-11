package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexGrader<X extends Exception> implements Grader<X> {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ComplexGrader.class);

	private static final boolean IGNORE_FROM_GITHUB = true;

	public static final Criterion C_USER_NAME = Criterion.given("user.name");

	public static final Criterion C_GRADE = Criterion.given("Grade");

	public static <X extends Exception> ComplexGrader<X> using(GitFsGrader<X> grader,
			GradeModifier penalizerModifier, double userGradeWeight) {
		return new ComplexGrader<>(grader, penalizerModifier, userGradeWeight);
	}

	private final GitFsGrader<X> grader;
	private final GradeModifier penalizerModifier;

	private final double userGradeWeight;

	private ComplexGrader(GitFsGrader<X> grader, GradeModifier penalizerModifier,
			double userGradeWeight) {
		this.grader = checkNotNull(grader);
		this.penalizerModifier = checkNotNull(penalizerModifier);
		this.userGradeWeight = userGradeWeight;
	}

	@Override
	public MarksTree grade(GitHubUsername author, GitHistorySimple history) throws X {
		verify(!history.graph().nodes().isEmpty());

		final Instant timeCap = ByTimeGrader.cappedAt(history);

		final MarksTree grade1 = grader.grade(history);

		final Mark userGrade = DeadlineGrader.getUsernameGrade(history, author).asNew();
		final MarksTree gradeWithUser;
		if (userGradeWeight == 0d) {
			gradeWithUser = grade1;
		} else {
			gradeWithUser = MarksTree.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade1));
		}

		return penalizerModifier.modify(gradeWithUser, timeCap);
	}

	@Override
	public GradeAggregator getAggregator() {
		final GradeAggregator main = getUserNamedAggregator(grader, userGradeWeight);
		final GradeAggregator penalized;
		final boolean withTimePenalty = penalizerModifier instanceof GradePenalizer;
		if (withTimePenalty) {
			penalized =
					GradeAggregator.parametric(GradePenalizer.C_MAIN, GradePenalizer.C_LATENESS, main);
		} else {
			penalized = main;
		}
		return penalized;
	}

	private static GradeAggregator getUserNamedAggregator(GitFsGrader<?> grader,
			double userGradeWeight) {
		final GradeAggregator basis = grader.getAggregator();
		if (userGradeWeight == 0d) {
			return basis;
		}
		return GradeAggregator.staticAggregator(ImmutableMap.of(ComplexGrader.C_USER_NAME,
				userGradeWeight, ComplexGrader.C_GRADE, 1d - userGradeWeight),
				ImmutableMap.of(ComplexGrader.C_GRADE, basis));
	}

	private static void checkTimes(GitHistorySimple history, Map<GitPathRoot, Instant> pushDates,
			Instant deadline) {
		/*
		 * Check whether has put late branches on every ending commits: some leaf has a pointer that is
		 * not main or master. And it is late. OR rather simply use commit times and try to detect
		 * inconsistencies: commit time on time but push date late.
		 */
		final ImmutableSet<GitPathRootRef> refs = IO_UNCHECKER.getUsing(() -> history.fs().refs());
		final ImmutableMap<GitPathRoot, Instant> commitDates =
				refs.stream().collect(ImmutableMap.toImmutableMap(p -> p,
						IO_UNCHECKER.wrapFunction(p -> p.getCommit().committerDate().toInstant())));
		// final ImmutableMap<GitPathRootRef, Instant> pushDates =
		// refs.stream().collect(ImmutableMap.toImmutableMap(
		// p -> p,
		// IO_UNCHECKER.wrapFunction(p -> history.getPushDates().getOrDefault(p.getCommit().id(),
		// Instant.MIN))));
		final Map<GitPathRoot, Instant> pushDatesLate =
				Maps.filterValues(pushDates, i -> i.isAfter(deadline));
		final Map<GitPathRoot, Instant> commitsOnTime =
				Maps.filterValues(commitDates, i -> !i.isAfter(deadline));

		final Map<GitPathRoot, Instant> contradictory =
				Maps.filterKeys(pushDatesLate, r -> commitsOnTime.containsKey(r));
		if (!contradictory.isEmpty()) {
			LOGGER.info("Commit times: {}.", commitDates);
			LOGGER.info("Push times: {}.", pushDates);
			LOGGER.info("Pushed late but committed on time: {}; commit times {}.", contradictory,
					Maps.filterKeys(commitsOnTime, r -> pushDatesLate.containsKey(r)));
		}
	}
}
