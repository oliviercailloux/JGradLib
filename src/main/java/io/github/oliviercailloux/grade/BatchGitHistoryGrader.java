package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.format.json.JsonbGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarizer;
import io.github.oliviercailloux.json.JsonbUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGitHistoryGrader<X extends Exception> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchGitHistoryGrader.class);
	private final Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory;

	public BatchGitHistoryGrader(Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		this.fetcherFactory = checkNotNull(fetcherFactory);
	}

	public <Y extends Exception> ImmutableMap<GitHubUsername, IGrade> getGrades(String prefix, ZonedDateTime deadline,
			Duration durationForZero, Throwing.Function<GitFileSystemHistory, IGrade, Y> grader, double userGradeWeight)
			throws X, Y, IOException {
		return getGrades(prefix, deadline, durationForZero, grader, userGradeWeight, TOptional.empty());
	}

	public <Y extends Exception> ImmutableMap<GitHubUsername, IGrade> getAndWriteGrades(String prefix,
			ZonedDateTime deadline, Duration durationForZero, Throwing.Function<GitFileSystemHistory, IGrade, Y> grader,
			double userGradeWeight, Path out) throws X, Y, IOException {
		return getGrades(prefix, deadline, durationForZero, grader, userGradeWeight, TOptional.of(out));
	}

	private <Y extends Exception> ImmutableMap<GitHubUsername, IGrade> getGrades(String prefix, ZonedDateTime deadline,
			Duration durationForZero, Throwing.Function<GitFileSystemHistory, IGrade, Y> grader, double userGradeWeight,
			TOptional<Path> outOpt) throws X, Y, IOException {
		checkArgument(userGradeWeight < 1d);
		final LinearPenalizer penalizer = LinearPenalizer.proportionalToLateness(durationForZero);

		final LinkedHashMap<GitHubUsername, IGrade> builder = new LinkedHashMap<>();
		try (GitFileSystemWithHistoryFetcher fetcher = fetcherFactory.get()) {

			for (GitHubUsername author : fetcher.getAuthors()) {
				final ImmutableBiMap<Instant, IGrade> byTime;

				final String commentGeneralCapped;
				final GitFileSystemHistory beforeCommitByGitHub;
				{
					final GitFileSystemHistory history = fetcher.goTo(author);

					final Optional<Instant> earliestTimeCommitByGitHub = history
							.filter(JavaMarkHelper::committerIsGitHub).asGitHistory().getTimestamps().values().stream()
							.min(Comparator.naturalOrder());
					beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
							.map(t -> history.filter(
									c -> history.asGitHistory().getTimestamp(c.getCommit().getId()).isBefore(t)))
							.orElse(history);

					commentGeneralCapped = earliestTimeCommitByGitHub
							.map(t -> "Ignored commits after " + t.toString() + ", sent by GitHub.").orElse("");
				}

				final ImmutableSortedSet<Instant> consideredTimestamps = getTimestamps(beforeCommitByGitHub,
						deadline.toInstant());

				final ImmutableBiMap.Builder<Instant, IGrade> byTimeBuilder = ImmutableBiMap.builder();
				for (Instant timeCap : consideredTimestamps) {
					final GitFileSystemHistory capped = beforeCommitByGitHub
							.filter(r -> !beforeCommitByGitHub.getCommitDate(r).isAfter(timeCap));

					final IGrade grade = grader.apply(capped);

					final Mark userGrade = DeadlineGrader.getUsernameGrade(beforeCommitByGitHub, author);
					final ImmutableSet<CriterionGradeWeight> subGrades = grade.getSubGradesAsSet();
					final ImmutableSet<CriterionGradeWeight> newSubGrades = ImmutableSet.<CriterionGradeWeight>builder()
							.add(CriterionGradeWeight.from(Criterion.given("user.name"), userGrade,
									userGradeWeight / (1d - userGradeWeight)))
							.addAll(subGrades).build();
//					final WeightingGrade gradeWithUser = WeightingGrade.from(ImmutableSet.of(
//							CriterionGradeWeight.from(Criterion.given("user.name"), userGrade, userGradeWeight),
//							CriterionGradeWeight.from(Criterion.given("main"), grade, 1d - userGradeWeight)));
					final WeightingGrade gradeWithUser = WeightingGrade.from(newSubGrades);

					final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
					LOGGER.debug("Lateness from {} to {} equal to {}.", deadline.toInstant(), timeCap, lateness);
					final IGrade penalizedForTimeGrade = penalizer.penalize(lateness, gradeWithUser);
					byTimeBuilder.put(timeCap, penalizedForTimeGrade);
				}
				byTime = byTimeBuilder.build();

				final IGrade bestGrade = byTime.values().stream().max(Comparator.comparing(IGrade::getPoints))
						.orElse(Mark.zero("No commit found."));
				final String separator = bestGrade.getComment().isEmpty() || commentGeneralCapped.isEmpty() ? "" : "; ";
				final IGrade bestGradeCommented = bestGrade
						.withComment(bestGrade.getComment() + separator + commentGeneralCapped);

				final IGrade integratedGrade;
				if (byTime.size() <= 1) {
					integratedGrade = bestGradeCommented;
				} else {
					integratedGrade = DeadlineGrader.getBestAndSub(bestGradeCommented, byTime, deadline);
				}
				builder.put(author, integratedGrade);

				outOpt.ifPresent(o -> write(builder, o, prefix));
			}
		}

		return ImmutableMap.copyOf(builder);
	}

	/**
	 * @param history
	 * @return the latest commit time that is on time, that is, the latest commit
	 *         time within [MIN, deadline], if it exists, and the late commit times,
	 *         that is, every times of commits falling in the range (deadline, MAX].
	 */
	ImmutableSortedSet<Instant> getTimestamps(GitFileSystemHistory history, Instant deadline) {
		final ImmutableSortedSet<Instant> timestamps = history.asGitHistory().getTimestamps().values().stream()
				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
		final Instant latestCommitTimeOnTime = Optional.ofNullable(timestamps.floor(deadline)).orElse(Instant.MIN);
		final ImmutableSortedSet<Instant> consideredTimestamps = timestamps.subSet(latestCommitTimeOnTime, true,
				Instant.MAX, true);
		verify(consideredTimestamps.isEmpty() == history.getGraph().nodes().isEmpty());
		return consideredTimestamps;
	}

	private void write(Map<GitHubUsername, IGrade> grades, Path out, String prefix) throws IOException {
		final ImmutableMap<String, IGrade> gradesString = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey().getUsername(), Entry::getValue));
		Files.writeString(out, JsonbUtils.toJsonObject(gradesString, JsonbGrade.asAdapter()).toString());
		final Summarizer summarizer = Summarizer.create().setInputPath(out)
				.setCsvOutputPath(Path.of("grades " + prefix + ".csv"))
				.setHtmlOutputPath(Path.of("grades " + prefix + ".html"));
		summarizer.summarize();
	}
}
