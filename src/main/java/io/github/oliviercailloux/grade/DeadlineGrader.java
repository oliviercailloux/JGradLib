package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is given a deadline. And a penalizer. Function that grades a git work. Cap at
 * various points (obtain pairs of git work and lateness); ask penalizer, obtain
 * grade. Then take the best grade and aggregates.
 * <p>
 * TODO refactor the whole thing into a unique grader that delegates; rather
 * than a chain. Make sure we can grade as well the github-committed bits, with
 * a penalty (supplemental to the time penalty!).
 */
public class DeadlineGrader {
	@SuppressWarnings("unused")
	static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGrader.class);

	private static class PathToGitGrader {

		private static final double USER_GRADE_WEIGHT = 0.5d / 20d;
		private final Throwing.Function<Path, IGrade, IOException> simpleWorkGrader;

		private PathToGitGrader(Throwing.Function<Path, IGrade, IOException> simpleWorkGrader) {
			this.simpleWorkGrader = checkNotNull(simpleWorkGrader);
		}

		public IGrade grade(GitWork work) throws IOException {
			final GitFileSystemHistory history = work.getHistory();
			final Mark userGrade = getUsernameGrade(history, work.getAuthor());
			final ImmutableSet<GitPathRootSha> latestTiedPathsOnTime = PathToGitGrader.getLatest(history);
			checkArgument(!latestTiedPathsOnTime.isEmpty());
			LOGGER.debug("Considering {}.", latestTiedPathsOnTime);
			final IGrade mainGrade = CheckedStream.<GitPathRootSha, IOException>wrapping(latestTiedPathsOnTime.stream())
					.map(simpleWorkGrader).min(Throwing.Comparator.comparing(IGrade::getPoints)).get();
			return WeightingGrade.from(ImmutableSet.of(
					CriterionGradeWeight.from(Criterion.given("user.name"), userGrade, USER_GRADE_WEIGHT),
					CriterionGradeWeight.from(Criterion.given("main"), mainGrade, 1d - USER_GRADE_WEIGHT)));
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
					.comparing(c -> IO_UNCHECKER.getUsing(() -> c.getCommit()).authorDate());
			final Comparator<GitPathRootSha> byCommitDate = Comparator
					.comparing(c -> IO_UNCHECKER.getUsing(() -> c.getCommit()).committerDate());
			final Throwing.Comparator<GitPathRootSha, IOException> byDate = (t1, t2) -> byAuthorDate
					.thenComparing(byCommitDate).compare(t1, t2);
			return Utils.<GitPathRootSha, IOException>getMaximalElements(leaves, byDate);
		}

	}

	private static class InstanciatorToPathGrader {

		private final Function<Instanciator, IGrade> grader;

		private InstanciatorToPathGrader(Function<Instanciator, IGrade> grader) {
			this.grader = checkNotNull(grader);
		}

		public IGrade grade(Path work) throws IOException {
			final ImmutableSet<Path> poms = PathUtils.getMatchingChildren(work, p -> p.endsWith("pom.xml"));
			LOGGER.debug("Poms: {}.", poms);
			final ImmutableSet<Path> pomsWithJava = CheckedStream.<Path, IOException>wrapping(poms.stream())
					.filter(p -> !PathUtils
							.getMatchingChildren(p, s -> String.valueOf(s.getFileName()).endsWith(".java")).isEmpty())
					.collect(ImmutableSet.toImmutableSet());
			LOGGER.debug("Poms with java: {}.", pomsWithJava);
			final ImmutableSet<Path> possibleDirs;
			if (pomsWithJava.isEmpty()) {
				possibleDirs = ImmutableSet.of(work);
			} else {
				possibleDirs = pomsWithJava.stream().map(Path::getParent).collect(ImmutableSet.toImmutableSet());
			}
			final ImmutableMap<Path, IGrade> gradedProjects = CollectionUtils.toMap(possibleDirs, this::gradeProject);
			final IGrade grade;
			verify(!gradedProjects.isEmpty());
			if (gradedProjects.size() == 1) {
				grade = Iterables.getOnlyElement(gradedProjects.values());
			} else {
				final ImmutableSet<CriterionGradeWeight> cgws = gradedProjects.keySet().stream()
						.map(p -> CriterionGradeWeight.from(Criterion.given("Using project dir " + p.toString()),
								gradedProjects.get(p), gradedProjects.get(p).getPoints() > 0d ? 1d : 0d))
						.collect(ImmutableSet.toImmutableSet());
				grade = WeightingGrade.from(cgws);
			}
			return grade;
		}

		private IGrade gradeProject(Path projectDirectory) throws IOException {
			final Path compiledDir = Utils.getTempUniqueDirectory("compile");
			final Path srcDir;
			final boolean hasPom = Files.exists(projectDirectory.resolve("pom.xml"));
			if (hasPom) {
				srcDir = projectDirectory.resolve("src/main/java/");
			} else {
//				srcDir = projectDirectory.resolve("src/main/java/");
				srcDir = projectDirectory;
			}
			final ImmutableSet<Path> javaPaths = Files.exists(srcDir)
					? PathUtils.getMatchingChildren(srcDir, p -> String.valueOf(p.getFileName()).endsWith(".java"))
					: ImmutableSet.of();
			final CompilationResult eclipseResult = Compiler.eclipseCompileUsingOurClasspath(javaPaths, compiledDir);
			final Pattern pathPattern = Pattern.compile("/tmp/sources[0-9]*/");
			final String eclipseStr = pathPattern.matcher(eclipseResult.err).replaceAll("/…/");
			final IGrade projectGrade;
			if (eclipseResult.countErrors() > 0) {
				projectGrade = Mark.zero(eclipseStr);
			} else if (javaPaths.isEmpty()) {
				LOGGER.debug("No java files at {}.", srcDir);
				projectGrade = Mark.zero("No java files found");
			} else {
				final int nbSuppressed = (int) CheckedStream.<Path, IOException>wrapping(javaPaths.stream())
						.map(p -> Files.readString(p))
						.flatMap(s -> Pattern.compile("@SuppressWarnings").matcher(s).results()).count();

				final int nbWarningsTot = eclipseResult.countWarnings() + nbSuppressed;
				final boolean hasWarnings = nbWarningsTot > 0;
				final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, grader);
				if (!hasWarnings) {
					projectGrade = codeGrade;
				} else {
					final ImmutableSet.Builder<String> commentsBuilder = ImmutableSet.builder();
					if (eclipseResult.countWarnings() > 0) {
						commentsBuilder.add(eclipseStr);
					}
					if (nbSuppressed > 0) {
						commentsBuilder.add("Found " + nbSuppressed + " suppressed warnings");
					}
					final String comment = commentsBuilder.build().stream().collect(Collectors.joining(". "));
					final Mark warningsGrade = Mark.zero(comment);
					final double lowWeightWarnings = 0.05d;
					final double highWeightWarnings = 0.10d;
					final double weightWarnings = nbWarningsTot == 1 ? lowWeightWarnings : highWeightWarnings;
					projectGrade = WeightingGrade
							.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("Code"), codeGrade, 1d),
									CriterionGradeWeight.from(Criterion.given("Warnings"), warningsGrade,
											weightWarnings / (1d - weightWarnings))));
				}
			}
			return projectGrade;
		}

	}

	private static interface Penalizer {
		public IGrade penalize(Duration lateness, IGrade grade);

		public io.github.oliviercailloux.grade.Mark getAbsolutePenality(Duration lateness, Grade grade);
	}

	public static class LinearPenalizer implements Penalizer {
		public static LinearPenalizer DEFAULT_PENALIZER = new LinearPenalizer(300);

		public static LinearPenalizer proportionalToLateness(Duration durationForZero) {
			return new LinearPenalizer(Math.toIntExact(durationForZero.getSeconds()));
		}

		private int nbSecondsZero;

		private LinearPenalizer(int nbSecondsZero) {
			this.nbSecondsZero = nbSecondsZero;
			checkArgument(nbSecondsZero > 0);
		}

		public int getNbSecondsZero() {
			return nbSecondsZero;
		}

		public void setNbSecondsZero(int nbSecondsZero) {
			this.nbSecondsZero = nbSecondsZero;
		}

		@Override
		public IGrade penalize(Duration lateness, IGrade grade) {
			final IGrade penalizedGrade;
			if (!lateness.isNegative() && !lateness.isZero()) {
				final double fractionPenalty = Math.min(lateness.getSeconds() / (double) nbSecondsZero, 1d);
				verify(0d < fractionPenalty);
				verify(fractionPenalty <= 1d);
				final WeightingGrade global = WeightingGrade.from(ImmutableSet.of(
						CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d - fractionPenalty),
						CriterionGradeWeight.from(Criterion.given("Time penalty"), Mark.zero("Lateness: " + lateness),
								fractionPenalty)));
				penalizedGrade = global.withDissolved(Criterion.given("Time penalty")).withoutTopLayer();
			} else {
				penalizedGrade = grade;
			}
			return penalizedGrade;
		}

		@Override
		public io.github.oliviercailloux.grade.Mark getAbsolutePenality(Duration lateness, Grade grade) {
			final io.github.oliviercailloux.grade.Mark penalty;
			if (!lateness.isNegative() && !lateness.isZero()) {
				final double fractionPenalty = Math.min(lateness.getSeconds() / (double) nbSecondsZero, 1d);
				verify(0d < fractionPenalty);
				verify(fractionPenalty <= 1d);
				final double currentPoints = grade.mark().getPoints();
				final double penaltyPoints = currentPoints * fractionPenalty;
				penalty = io.github.oliviercailloux.grade.Mark.given(-1d * penaltyPoints, "Lateness: " + lateness);
			} else {
				penalty = io.github.oliviercailloux.grade.Mark.zero();
			}
			return penalty;
		}

		public io.github.oliviercailloux.grade.Mark getFractionRemaining(Duration lateness) {
			final io.github.oliviercailloux.grade.Mark remaining;
			if (!lateness.isNegative() && !lateness.isZero()) {
				final double fractionPenalty = Math.min(lateness.getSeconds() / (double) nbSecondsZero, 1d);
				verify(0d < fractionPenalty);
				verify(fractionPenalty <= 1d);
				remaining = io.github.oliviercailloux.grade.Mark.given(1d - fractionPenalty, "Lateness: " + lateness);
			} else {
				remaining = io.github.oliviercailloux.grade.Mark.one();
			}
			return remaining;
		}
	}

	public static DeadlineGrader usingGitGrader(GitGrader grader, ZonedDateTime deadline) {
		return new DeadlineGrader(grader::grade, deadline, LinearPenalizer.DEFAULT_PENALIZER);
	}

	public static DeadlineGrader usingPathGrader(Throwing.Function<Path, IGrade, IOException> grader,
			ZonedDateTime deadline) {
		return new DeadlineGrader(new PathToGitGrader(grader)::grade, deadline, LinearPenalizer.DEFAULT_PENALIZER);
	}

	/**
	 * This should go in another package as it is Java specific.
	 */
	public static DeadlineGrader usingInstantiatorGrader(Function<Instanciator, IGrade> grader,
			ZonedDateTime deadline) {

		return new DeadlineGrader(new PathToGitGrader(new InstanciatorToPathGrader(grader)::grade)::grade, deadline,
				LinearPenalizer.DEFAULT_PENALIZER);
	}

	/**
	 * @return a weighting grade that shows the best grade with weight 1, and the
	 *         other grades with weight 0, and indicates for each of them where they
	 *         have been capped.
	 */
	public static IGrade getBestAndSub(IGrade best, ImmutableBiMap<Instant, IGrade> byTime, ZonedDateTime deadline) {
		final IGrade finalGrade;
		final Instant mainInstant = byTime.inverse().get(best);
		final ImmutableSet<CriterionGradeWeight> grades = byTime.entrySet().stream()
				.map(e -> CriterionGradeWeight.from(
						Criterion.given("Cap at " + e.getKey().atZone(deadline.getZone()).toString()), e.getValue(),
						e.getKey().equals(mainInstant) ? 1d : 0d))
				.collect(ImmutableSet.toImmutableSet());
		finalGrade = WeightingGrade.from(grades,
				"Using best grade, from " + mainInstant.atZone(deadline.getZone()).toString());
		return finalGrade;
	}

	public static Mark getUsernameGrade(GitFileSystemHistory history, GitHubUsername expectedUsername)
			throws IOException {
		final CheckedStream<GitPathRoot, IOException> checkedCommits = CheckedStream
				.wrapping(history.getGraph().nodes().stream());
		final ImmutableSet<String> authors = checkedCommits.map(GitPathRoot::getCommit).map(Commit::authorName)
				.filter(s -> !s.equals("github-classroom[bot]")).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<String> authorsShow = authors.stream().map(s -> "‘" + s + "’")
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Authors: {}.", authors);
		final String authorExpected = expectedUsername.getUsername();
		return Mark.binary(authors.equals(ImmutableSet.of(authorExpected)), "",
				"Expected ‘" + authorExpected + "’, seen " + authorsShow);
	}

	private final Throwing.Function<GitWork, IGrade, IOException> grader;
	private final ZonedDateTime deadline;
	private Penalizer penalizer;

	private DeadlineGrader(Throwing.Function<GitWork, IGrade, IOException> gitWorkGrader, ZonedDateTime deadline,
			Penalizer penalizer) {
		this.grader = checkNotNull(gitWorkGrader);
		this.deadline = checkNotNull(deadline);
		this.penalizer = checkNotNull(penalizer);
	}

	private static Instant getLatestCommit(GitFileSystemHistory history) {
		checkArgument(!history.isEmpty());
		final GitHistory asGitHistory = history.asGitHistory();
		return asGitHistory.getLeaves().stream().map(asGitHistory::getTimestamp).max(Comparator.naturalOrder())
				.orElseThrow();
	}

	/**
	 * @return the latest instant weakly before the cap, or the cap itself if there
	 *         are none such instants.
	 */
	private static Instant getLatestBefore(ImmutableSortedSet<Instant> timestamps, Instant cap) {
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

	private static ImmutableSet<GitFileSystemHistory> fromJustBeforeDeadline(GitFileSystemHistory history,
			ZonedDateTime deadline) throws IOException {
		final ImmutableSortedSet<Instant> toConsider;
		final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getTimestamps().values().stream()
				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
		{
			final Instant latestAll = getLatestBefore(timestamps, deadline.toInstant());
			final ImmutableSortedSet<Instant> fromJustBefore = timestamps.tailSet(latestAll);

			final ImmutableSortedSet<Instant> timestampsNonGitHub = history
					.filter(p -> !JavaMarkHelper.committerIsGitHub(p)).asGitHistory().getTimestamps().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final Instant latestNonGitHub = getLatestBefore(timestampsNonGitHub, deadline.toInstant());
			final ImmutableSortedSet.Builder<Instant> builder = ImmutableSortedSet.naturalOrder();
			if (!timestamps.headSet(latestNonGitHub, true).isEmpty()) {
				builder.add(latestNonGitHub);
			}
			toConsider = builder.addAll(fromJustBefore).build();
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
		verify(toConsider.isEmpty() == history.getGraph().nodes().isEmpty(),
				toConsider.toString() + history.getGraph().nodes());

		verify(timestamps.containsAll(adjustedConsider));

		return CheckedStream.<Instant, IOException>from(adjustedConsider)
				.map(timeCap -> history.filter(r -> !history.getCommitDate(r).isAfter(timeCap)))
				.collect(ImmutableSet.toImmutableSet());
	}

	public Penalizer getPenalizer() {
		return penalizer;
	}

	public DeadlineGrader setPenalizer(Penalizer penalizer) {
		this.penalizer = checkNotNull(penalizer);
		return this;
	}

	public IGrade grade(GitWork work) throws IOException {

		final ImmutableBiMap<Instant, IGrade> byTime = getPenalizedGradesByCap(work);

		final Optional<IGrade> bestGrade = byTime.values().stream().max(Comparator.comparing(IGrade::getPoints));
		final IGrade finalGrade;
		if (bestGrade.isEmpty()) {
			finalGrade = Mark.zero("No commit found.");
		} else if (byTime.size() == 1) {
			finalGrade = bestGrade.get();
		} else {
			finalGrade = getBestAndSub(bestGrade.get(), byTime, deadline);
		}
		return finalGrade;
	}

	/**
	 * Is a BiMap because only one grade has no penalty, the other ones have
	 * different penalties, so they are unique. (Il multiple grades have no penalty,
	 * this basically means that the student can try several times without
	 * drawback.)");
	 *
	 */
	private ImmutableBiMap<Instant, IGrade> getPenalizedGradesByCap(GitWork work) throws IOException {
		final ImmutableSet<GitFileSystemHistory> toConsider = fromJustBeforeDeadline(work.getHistory(), deadline);

		final ImmutableBiMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableBiMap.builder();
		for (GitFileSystemHistory timeCapped : toConsider) {
			final IGrade penalizedGrade = getPenalized(work.getAuthor(), timeCapped);
			byTimeBuilder.put(getLatestCommit(timeCapped), penalizedGrade);
		}
		final ImmutableBiMap<Instant, IGrade> byTime = byTimeBuilder.build();
		verify(toConsider.isEmpty() == work.getHistory().getGraph().nodes().isEmpty());
		return byTime;
	}

	private IGrade getPenalized(GitHubUsername author, GitFileSystemHistory capped) throws IOException {
		final IGrade grade = grader.apply(GitWork.given(author, capped));
		final Instant latest = getLatestCommit(capped);
		final Duration lateness = Duration.between(deadline.toInstant(), latest);
		final IGrade penalizedForTimeGrade = penalizer.penalize(lateness, grade);
		if (!capped.filter(r -> JavaMarkHelper.committerIsGitHub(r)).isEmpty()) {
			return penalizeForGitHub(penalizedForTimeGrade);
		}
		return penalizedForTimeGrade;
	}

	private IGrade penalizeForGitHub(IGrade grade) {
		final double fractionPenalty = 0.7d;
		return WeightingGrade.from(ImmutableSet.of(
				CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d - fractionPenalty),
				CriterionGradeWeight.from(Criterion.given("Penalty: commit by GitHub"), Mark.zero(), fractionPenalty)));
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
