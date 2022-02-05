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
import io.github.oliviercailloux.grade.format.json.JsonGrade;
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
	private static final Criterion C_USER_NAME = Criterion.given("user.name");
	private static final Criterion C_GRADE = Criterion.given("grade");
	private static final Criterion C_MAIN = Criterion.given("main");
	private static final Criterion C_LATENESS = Criterion.given("lateness");
	private final Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory;

	public BatchGitHistoryGrader(Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		this.fetcherFactory = checkNotNull(fetcherFactory);
	}

	private GradeStructure getUserNamedStructure(Grader<?> grader, double userGradeWeight) {
		final GradeStructure basis = grader.getStructure();
		return GradeStructure.givenWeights(ImmutableMap.of(C_USER_NAME, userGradeWeight, C_GRADE, 1d - userGradeWeight),
				ImmutableMap.of(C_GRADE, basis));
	}

	private GradeStructure getComplexStructure(Grader<?> grader, double userGradeWeight) {
		final GradeStructure main = getUserNamedStructure(grader, userGradeWeight);
		final GradeStructure penalized = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(C_LATENESS),
				ImmutableMap.of(C_MAIN, main));
		GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of());
	}

	public <Y extends Exception> Exam getGrades(String prefix, ZonedDateTime deadline, Duration durationForZero,
			Grader<Y> grader, double userGradeWeight) throws X, Y, IOException {
		return getGrades(prefix, deadline, durationForZero, grader, userGradeWeight, TOptional.empty());
	}

	public <Y extends Exception> Exam getAndWriteGrades(String prefix, ZonedDateTime deadline, Duration durationForZero,
			Grader<Y> grader, double userGradeWeight, Path out) throws X, Y, IOException {
		return getGrades(prefix, deadline, durationForZero, grader, userGradeWeight, TOptional.of(out));
	}

	private <Y extends Exception> Exam getGrades(String prefix, ZonedDateTime deadline, Duration durationForZero,
			Grader<Y> grader, double userGradeWeight, TOptional<Path> outOpt) throws X, Y, IOException {
		checkArgument(userGradeWeight < 1d);
		final LinearPenalizer penalizer = LinearPenalizer.proportionalToLateness(durationForZero);

		final LinkedHashMap<GitHubUsername, Grade> builder = new LinkedHashMap<>();
		try (GitFileSystemWithHistoryFetcher fetcher = fetcherFactory.get()) {

			for (GitHubUsername author : fetcher.getAuthors()) {
				final ImmutableBiMap<Instant, Grade> byTime;

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

				final ImmutableBiMap.Builder<Instant, Grade> byTimeBuilder = ImmutableBiMap.builder();
				for (Instant timeCap : consideredTimestamps) {
					final GitFileSystemHistory capped = beforeCommitByGitHub
							.filter(r -> !beforeCommitByGitHub.getCommitDate(r).isAfter(timeCap));

					final Grade grade = grader.grade(capped);

					final Mark userGrade = DeadlineGrader.getUsernameGrade(beforeCommitByGitHub, author).asNew();
					final Grade gradeWithUser = Grade
							.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

					final GradeStructure userNamedStructure = getUserNamedStructure(grader, userGradeWeight);

					final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
					LOGGER.debug("Lateness from {} to {} equal to {}.", deadline.toInstant(), timeCap, lateness);
					final Mark penalty = penalizer.getAbsolutePenality(lateness,
							StructuredGrade.given(gradeWithUser, userNamedStructure));

					final Grade penalized = Grade
							.composite(ImmutableMap.of(C_MAIN, gradeWithUser, C_LATENESS, penalty));
					byTimeBuilder.put(timeCap, penalized);
				}
				byTime = byTimeBuilder.build();

				final Grade byTimeGrade;
				if (byTime.isEmpty()) {
					byTimeGrade = Mark.zero("No commit found.");
				} else {
					final ImmutableMap<Criterion, Grade> subsByTime = byTime.keySet().stream().collect(ImmutableMap
							.toImmutableMap(i -> Criterion.given("Capping at " + i.toString()), byTime::get));
					byTimeGrade = Grade.composite(subsByTime);
				}
				builder.put(author, byTimeGrade);

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

	private void write(Map<GitHubUsername, Grade> grades, Path out, String prefix) throws IOException {
		final ImmutableMap<String, IGrade> gradesString = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey().getUsername(), Entry::getValue));
		Files.writeString(out, JsonbUtils.toJsonObject(gradesString, JsonGrade.asAdapter()).toString());
		final Summarizer summarizer = Summarizer.create().setInputPath(out)
				.setCsvOutputPath(Path.of("grades " + prefix + ".csv"))
				.setHtmlOutputPath(Path.of("grades " + prefix + ".html"));
		summarizer.summarize();
	}
}
