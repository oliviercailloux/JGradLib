package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.google.common.collect.ImmutableList;
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
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class Commit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Commit.class);

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-11T14:10:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", "commit");
			// .stream().filter(r ->
			// r.getUsername().equals("")).collect(ImmutableList.toImmutableList());
		}

		final ImmutableMap.Builder<String, IGrade> builder = ImmutableMap.builder();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final String username = repository.getUsername();
			final IGrade grade = grade(repository, DEADLINE);
			builder.put(username, grade);
		}
		final ImmutableMap<String, IGrade> grades = builder.build();
		Files.writeString(Path.of("all grades commit.json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());
		LOGGER.info("Grades: {}.", grades);
	}

	public static IGrade grade(RepositoryCoordinatesWithPrefix coordinates, ZonedDateTime deadline) throws IOException {
		final FileRepository repository = GitCloner.create().setCheckCommonRefsAgree(false)
				.download(coordinates.asGitUri(), Utils.getTempDirectory().resolve(coordinates.getRepositoryName()));

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

		return grade(history, deadline, coordinates.getUsername());
	}

	public static IGrade grade(GitFileSystemHistory history, ZonedDateTime deadline, String gitHubUsername)
			throws IOException {
		final ZonedDateTime tooLate = deadline.plus(Duration.ofMinutes(5));
		final ImmutableSortedSet<Instant> toConsider;
		{
			final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getCommitDates().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final ImmutableSortedSet<Instant> toDeadline = timestamps.headSet(deadline.toInstant(), true);
			if (toDeadline.isEmpty()) {
				toConsider = ImmutableSortedSet.of();
			} else {
				final Instant lastOnTime = toDeadline.last();
				toConsider = timestamps.tailSet(lastOnTime).headSet(tooLate.toInstant());
			}
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
		LOGGER.debug("To consider: {}.", adjustedConsider);

		final ImmutableMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableMap.builder();
		for (Instant timeCap : adjustedConsider) {
			final Predicate<ObjectId> onTime = o -> !history.getCommitDate(o).isAfter(timeCap);
			final GitFileSystemHistory filteredHistory = history.filter(onTime.and(IO_UNCHECKER
					.wrapPredicate(o -> !JavaMarkHelper.committerIsGitHub(history.getGitFilesystem().getPathRoot(o)))));
			final Commit grader = new Commit(filteredHistory, gitHubUsername);
			final IGrade grade = grader.grade();

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
		final Optional<IGrade> bestGrade = byTime.values().stream().max(Comparator.comparing(IGrade::getPoints));
		final IGrade finalGrade;
		if (bestGrade.isEmpty()) {
			final String beforeTooLate;
			if (history.getGraph().nodes().isEmpty()) {
				beforeTooLate = "";
			} else {
				beforeTooLate = " before " + tooLate.toString();
			}
			finalGrade = Mark.zero("No commit found" + beforeTooLate);
		} else if (byTime.size() == 1) {
			finalGrade = bestGrade.get();
		} else {
			final IGrade main = bestGrade.get();
			final Instant mainInstant = byTime.entrySet().stream().filter(e -> e.getValue().equals(main))
					.map(Map.Entry::getKey).collect(MoreCollectors.onlyElement());
			final ImmutableSet<CriterionGradeWeight> grades = byTime.entrySet().stream()
					.map(e -> CriterionGradeWeight.from(
							Criterion.given("Cap at " + e.getKey().atZone(deadline.getZone()).toString()), e.getValue(),
							e.getValue().equals(main) ? 1d : 0d))
					.collect(ImmutableSet.toImmutableSet());
			finalGrade = WeightingGrade.from(grades,
					"Using best grade, from " + mainInstant.atZone(deadline.getZone()).toString());
		}
		return finalGrade;
	}

	private final GitFileSystemHistory filteredHistory;

	private final String gitHubUsername;

	public Commit(GitFileSystemHistory filteredHistory, String gitHubUsername) {
		this.filteredHistory = checkNotNull(filteredHistory);
		this.gitHubUsername = checkNotNull(gitHubUsername);
	}

	public WeightingGrade grade() throws UncheckedIOException, IOException {
		final GitFileSystem gitFs = filteredHistory.getGitFilesystem();
		/** Just to avoid warning about IOE. */
		if (gitFs == null) {
			throw new IOException();
		}

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
		// final Graph<GitPathRoot> filteredCommitsGraph;
		// {
		// final ImmutableGraph<GitPathRoot> commitsGraph = gitFs.getCommitsGraph();
		// final Function<GitPathRoot, ? extends ObjectId> getId = IO_UNCHECKER
		// .wrapFunction(p -> p.getCommit().getId());
		// filteredCommitsGraph = Graphs.inducedSubgraph(commitsGraph,
		// commitsGraph.nodes().stream()
		// .filter(r ->
		// onTime.test(getId.apply(r))).collect(ImmutableSet.toImmutableSet()));
		// }
		// final GitHistory filteredPushHistory = pushHistory.filter(onTime);

		{
			final Mark hasCommit = Mark.binary(!filteredHistory.getGraph().nodes().isEmpty());
			final Predicate<GitPathRoot> rightIdent = IO_UNCHECKER
					.wrapPredicate(c -> JavaMarkHelper.committerAndAuthorIs(c, gitHubUsername));
			final Mark allCommitsRightName = Mark
					.binary(filteredHistory.getGraph().nodes().stream().allMatch(rightIdent)
							&& !filteredHistory.getGraph().nodes().isEmpty());
			final WeightingGrade commitsGrade = WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d),
							CriterionGradeWeight.from(Criterion.given("Right identity"), allCommitsRightName, 3d)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Has commits"), commitsGrade, 2d));
		}

		final Pattern coucouPattern = Marks.extend("coucou");
		{
			final WeightingGrade coucouCommit = WeightingGrade.proportional(
					Criterion.given("'afile.txt' content (anywhere)"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("afile.txt"), coucouPattern))),
					Criterion.given("'coucou' content"),
					Mark.binary(matches(gitFs.getAbsolutePath("/refs/heads/coucou//afile.txt"), coucouPattern)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
		}
		{
			final Pattern digitPattern = Marks.extend("\\d+");
			final CriterionGradeWeight myIdContent = CriterionGradeWeight.from(Criterion.given("'myid.txt' content"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern))),
					1d);
			final CriterionGradeWeight myIdAndAFileContent = CriterionGradeWeight.from(
					Criterion.given("'myid.txt' and 'afile.txt' content (anywhere)"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern)
									&& matches(r.resolve("afile.txt"), coucouPattern))),
					1d);
			final CriterionGradeWeight mainContent = CriterionGradeWeight
					.from(Criterion.given("'main' content"),
							Mark.binary(matches(gitFs.getAbsolutePath("/refs/heads/main//myid.txt"), digitPattern)
									&& matches(gitFs.getAbsolutePath("/refs/heads/main//afile.txt"), coucouPattern)),
							2d);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'main'"),
					WeightingGrade.from(ImmutableSet.of(myIdContent, myIdAndAFileContent, mainContent)), 3d));
		}
		{
			final Pattern anything = Pattern.compile(".*");
			final WeightingGrade commit = WeightingGrade.proportional(Criterion.given("'another file.txt' exists"),
					Mark.binary(filteredHistory.getGraph().nodes().stream()
							.anyMatch(r -> Files.exists(r.resolve("sub/a/another file.txt")))),
					Criterion.given("'dev' content"),
					Mark.binary(matches(gitFs.getAbsolutePath("/refs/heads/dev//sub/a/another file.txt"), anything)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 2d));
		}

		return WeightingGrade.from(gradeBuilder.build());
	}

	boolean matches(GitPath path, Pattern regExp) throws UncheckedIOException {
		try {
			if (filteredHistory.asDirect(path.getRoot()).isEmpty()) {
				return false;
			}
			if (!Files.exists(path)) {
				return false;
			}
			final String content = Files.readString(path);
			return regExp.matcher(content).matches();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
