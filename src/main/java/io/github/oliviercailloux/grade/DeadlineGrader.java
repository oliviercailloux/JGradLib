package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import io.github.oliviercailloux.git.fs.Commit;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.fs.GitPathRootSha;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.io.IoUtils;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
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
 */
public class DeadlineGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGrader.Penalizer.class);

	private static class PathToGitGrader {

		private final Throwing.Function<Path, IGrade, IOException> simpleWorkGrader;

		private PathToGitGrader(Throwing.Function<Path, IGrade, IOException> simpleWorkGrader) {
			this.simpleWorkGrader = checkNotNull(simpleWorkGrader);
		}

		public IGrade grade(GitWork work) throws IOException {
			final GitFileSystemHistory history = work.getHistory();
			final CheckedStream<GitPathRoot, IOException> checkedCommits = CheckedStream
					.wrapping(history.getGraph().nodes().stream());
			final ImmutableSet<String> authors = checkedCommits.map(GitPathRoot::getCommit).map(Commit::getAuthorName)
					.collect(ImmutableSet.toImmutableSet());
			LOGGER.debug("Authors: {}.", authors);
			final Optional<String> author = authors.stream().collect(Utils.singleOrEmpty());
			final Mark userGrade = Mark
					.binary(author.isPresent() && author.get().equals(work.getAuthor().getUsername()));
			final ImmutableSet<GitPathRootSha> latestTiedPathsOnTime = PathToGitGrader.getLatest(history);
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

	private static class InstanciatorToPathGrader {

		private final Function<Instanciator, IGrade> grader;

		private InstanciatorToPathGrader(Function<Instanciator, IGrade> grader) {
			this.grader = checkNotNull(grader);
		}

		public IGrade grade(Path work) throws IOException {
			final ImmutableSet<Path> poms = IoUtils.getMatchingChildren(work, p -> p.endsWith("pom.xml"));
			final ImmutableMap<Path, IGrade> gradedPoms = CollectionUtils.toMap(poms,
					p -> gradeProject(p.getParent() == null ? work : p.getParent()));
			if (gradedPoms.size() == 1) {
				return Iterables.getOnlyElement(gradedPoms.values());
			}
			final ImmutableSet<CriterionGradeWeight> cgws = gradedPoms.keySet().stream()
					.map(p -> CriterionGradeWeight.from(Criterion.given(p.toString()), gradedPoms.get(p), 1d))
					.collect(ImmutableSet.toImmutableSet());
			return WeightingGrade.from(cgws);
		}

		private IGrade gradeProject(Path projectDirectory) throws IOException {
			final Path compiledDir = Path.of("out/");
			final Path srcDir = projectDirectory.resolve("src/main/java/");
			final ImmutableSet<Path> javaPaths = IoUtils.getMatchingChildren(srcDir,
					p -> String.valueOf(p.getFileName()).endsWith(".java"));
			final CompilationResult eclipseResult = Compiler.eclipseCompileUsingOurClasspath(javaPaths, compiledDir);
			final int nbSuppressed = (int) CheckedStream.<Path, IOException>wrapping(javaPaths.stream())
					.map(p -> Files.readString(p))
					.flatMap(s -> Pattern.compile("@SuppressWarnings").matcher(s).results()).count();
			final String eclipseStr = eclipseResult.err.replaceAll(projectDirectory.toAbsolutePath().toString() + "/",
					"");
			final IGrade pomGrade;
			if (eclipseResult.countErrors() > 0) {
				pomGrade = Mark.zero(eclipseStr);
			} else {
				final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, grader);
				final int nbWarningsTot = eclipseResult.countWarnings() + nbSuppressed;
				final boolean hasWarnings = nbWarningsTot > 0;
				if (!hasWarnings) {
					pomGrade = codeGrade;
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
					pomGrade = WeightingGrade
							.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("Code"), codeGrade, 1d),
									CriterionGradeWeight.from(Criterion.given("Warnings"), warningsGrade,
											weightWarnings / (1d - weightWarnings))));
				}
			}
			return pomGrade;
		}

	}

	private static interface Penalizer {
		public IGrade penalize(Duration lateness, IGrade grade);
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
				penalizedGrade = WeightingGrade.from(ImmutableSet.of(
						CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d - fractionPenalty),
						CriterionGradeWeight.from(Criterion.given("Time penalty"), Mark.zero("Lateness: " + lateness),
								fractionPenalty)));
			} else {
				penalizedGrade = grade;
			}
			return penalizedGrade;
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

	private final Throwing.Function<GitWork, IGrade, IOException> grader;
	private final ZonedDateTime deadline;
	private Penalizer penalizer;

	private DeadlineGrader(Throwing.Function<GitWork, IGrade, IOException> gitWorkGrader, ZonedDateTime deadline,
			Penalizer penalizer) {
		this.grader = checkNotNull(gitWorkGrader);
		this.deadline = checkNotNull(deadline);
		this.penalizer = checkNotNull(penalizer);
	}

	public Penalizer getPenalizer() {
		return penalizer;
	}

	public DeadlineGrader setPenalizer(Penalizer penalizer) {
		this.penalizer = checkNotNull(penalizer);
		return this;
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
	private IGrade getBestAndSub(IGrade best, ImmutableMap<Instant, IGrade> byTime) {
		final IGrade finalGrade;
		final Instant mainInstant = byTime.entrySet().stream().filter(e -> e.getValue().equals(best))
				.map(Map.Entry::getKey).collect(MoreCollectors.onlyElement());
		final ImmutableSet<CriterionGradeWeight> grades = byTime.entrySet().stream()
				.map(e -> CriterionGradeWeight.from(
						Criterion.given("Cap at " + e.getKey().atZone(deadline.getZone()).toString()), e.getValue(),
						e.getValue().getPoints() == best.getPoints() ? 1d : 0d))
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
