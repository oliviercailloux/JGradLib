package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.internal.storage.file.FileRepository;
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
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class GitGeneralGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitGeneralGrader.class);

	public static void grade(String prefix, ZonedDateTime deadline, GitGrader grader) throws IOException {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", prefix);
//			.stream().filter(r -> r.getUsername().equals("")).collect(ImmutableList.toImmutableList());
		}

		final ImmutableMap.Builder<String, IGrade> builder = ImmutableMap.builder();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final String username = repository.getUsername();
			final IGrade grade = grade(repository, deadline, grader);
			builder.put(username, grade);
		}
		final ImmutableMap<String, IGrade> grades = builder.build();
		Files.writeString(Path.of("grades " + prefix + ".json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());
		LOGGER.info("Grades: {}.", grades);
	}

	public static IGrade grade(RepositoryCoordinatesWithPrefix coordinates, ZonedDateTime deadline, GitGrader grader)
			throws IOException {
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
			LOGGER.debug("Push history: {}.", pushHistory);
		}

		final GitFileSystemHistory history = GitFileSystemHistory.create(gitFs, pushHistory);

		return grade(history, deadline, coordinates.getUsername(), grader);
	}

	public static IGrade grade(GitFileSystemHistory history, ZonedDateTime deadline, String username, GitGrader grader)
			throws IOException {
		final ZonedDateTime tooLate = deadline.plus(Duration.ofMinutes(5));
		final ImmutableSortedSet<Instant> toConsider;
		{
			final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getCommitDates().values().stream()
					.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
			final ImmutableSortedSet<Instant> toDeadline = timestamps.headSet(deadline.toInstant(), true);
			LOGGER.debug("All timestamps: {}, picking those before {} results in: {}.", timestamps,
					deadline.toInstant(), toDeadline);
			final Instant considerFrom;
			if (toDeadline.isEmpty()) {
				considerFrom = deadline.toInstant();
			} else {
				considerFrom = toDeadline.last();
			}
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
		LOGGER.debug("To consider: {}, adjusted: {}.", toConsider, adjustedConsider);

		final ImmutableMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableMap.builder();
		for (Instant timeCap : adjustedConsider) {
			final GitFileSystemHistory filteredHistory = history
					.filter(r -> !history.getCommitDate(r).isAfter(timeCap) && !JavaMarkHelper.committerIsGitHub(r));
			final IGrade grade = grader.grade(filteredHistory, username);

			final IGrade penalizedGrade;
			final Duration lateness = Duration.between(timeCap, deadline);
			if (lateness.isNegative()) {
				final double fractionPenalty = Math.min(-lateness.getSeconds() / 300d, 1d);
				verify(0d < fractionPenalty);
				verify(fractionPenalty <= 1d);
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

}
