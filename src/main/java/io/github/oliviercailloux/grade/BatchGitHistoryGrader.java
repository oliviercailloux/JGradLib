package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.xml.XmlUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

	private GradeAggregator getUserNamedAggregator(Grader<?> grader, double userGradeWeight) {
		final GradeAggregator basis = grader.getAggregator();
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(C_USER_NAME, userGradeWeight, C_GRADE, 1d - userGradeWeight),
				ImmutableMap.of(C_GRADE, basis));
	}

	private GradeAggregator getComplexStructure(Grader<?> grader, double userGradeWeight) {
		final GradeAggregator main = getUserNamedAggregator(grader, userGradeWeight);
		final GradeAggregator penalized = GradeAggregator.parametric(C_MAIN, C_LATENESS, main);
		final GradeAggregator maxAmongAttempts = GradeAggregator.max(penalized);
		return maxAmongAttempts;
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

		final LinkedHashMap<GitHubUsername, MarksTree> builder = new LinkedHashMap<>();
		final GradeAggregator whole = getComplexStructure(grader, userGradeWeight);
		try (GitFileSystemWithHistoryFetcher fetcher = fetcherFactory.get()) {

			for (GitHubUsername author : fetcher.getAuthors()) {
				final ImmutableBiMap<Instant, MarksTree> byTime;

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

				final ImmutableBiMap.Builder<Instant, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
				for (Instant timeCap : consideredTimestamps) {
					final GitFileSystemHistory capped = beforeCommitByGitHub
							.filter(r -> !beforeCommitByGitHub.getCommitDate(r).isAfter(timeCap));

					final MarksTree grade = grader.grade(capped);

					final Mark userGrade = DeadlineGrader.getUsernameGrade(beforeCommitByGitHub, author).asNew();
					final MarksTree gradeWithUser = MarksTree
							.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

					final GradeAggregator userNamedAggregator = getUserNamedAggregator(grader, userGradeWeight);

					final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
					LOGGER.debug("Lateness from {} to {} equal to {}.", deadline.toInstant(), timeCap, lateness);
					final Mark penalty = penalizer.getAbsolutePenality(lateness,
							Grade.given(userNamedAggregator, gradeWithUser));

					final MarksTree penalized = MarksTree
							.composite(ImmutableMap.of(C_MAIN, gradeWithUser, C_LATENESS, penalty));
					byTimeBuilder.put(timeCap, penalized);
				}
				byTime = byTimeBuilder.build();

				final MarksTree byTimeGrade;
				if (byTime.isEmpty()) {
					byTimeGrade = Mark.zero("No commit found.");
				} else {
					final ImmutableMap<Criterion, MarksTree> subsByTime = byTime.keySet().stream()
							.collect(ImmutableMap.toImmutableMap(
									i -> Criterion.given(
											String.format("Capping at %s.%s", i.toString(), commentGeneralCapped)),
									byTime::get));
					byTimeGrade = MarksTree.composite(subsByTime);
				}
				builder.put(author, byTimeGrade);

				outOpt.ifPresent(o -> write(new Exam(whole, ImmutableMap.copyOf(builder)), o, prefix));
			}
		}
		return new Exam(whole, ImmutableMap.copyOf(builder));
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

	private void write(Exam exam, Path out, String prefix) throws IOException {
//		Files.writeString(out, JsonSimpleGrade.toJson(exam));

		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader studentsReader = JsonStudentsReader.from(Files.readString(Path.of("usernames.json")));
		final ImmutableBiMap<GitHubUsername, StudentOnGitHubKnown> students = studentsReader
				.getStudentsKnownByGitHubUsername();

		final ImmutableMap<StudentOnGitHubKnown, MarksTree> trees = CollectionUtils.transformKeys(exam.grades(),
				students::get);
		final String csv = CsvGrades.newInstance(CsvGrades.STUDENT_KNOWN_IDENTITY_FUNCTION, 20)
				.gradesToCsv(exam.aggregator(), trees);
		Files.writeString(Path.of("grades " + prefix + ".csv"), csv);

		final ImmutableMap<String, Grade> grades = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(GitHubUsername::getUsername, exam::getGrade));
		final String html = XmlUtils.asString(HtmlGrades.asHtml(grades, prefix, 20d));
		Files.writeString(Path.of("grades " + prefix + ".html"), html);
	}
}
