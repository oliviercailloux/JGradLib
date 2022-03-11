package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGitHistoryGrader<X extends Exception> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchGitHistoryGrader.class);

	public static final ZonedDateTime MAX_DEADLINE = Instant.ofEpochMilli(Long.MAX_VALUE).atZone(ZoneOffset.UTC);

	public static <X extends Exception> BatchGitHistoryGrader<X> given(
			Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		return new BatchGitHistoryGrader<>(fetcherFactory);
	}

	private static final Criterion C_USER_NAME = Criterion.given("user.name");
	private static final Criterion C_GRADE = Criterion.given("Grade");
	private static final Criterion C_MAIN = Criterion.given("Main");
	private static final Criterion C_LATENESS = Criterion.given("Timing");
	private final Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory;

	private BatchGitHistoryGrader(Throwing.Supplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		this.fetcherFactory = checkNotNull(fetcherFactory);
	}

	private GradeAggregator getUserNamedAggregator(Grader<?> grader, double userGradeWeight) {
		final GradeAggregator basis = grader.getAggregator();
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(C_USER_NAME, userGradeWeight, C_GRADE, 1d - userGradeWeight),
				ImmutableMap.of(C_GRADE, basis));
	}

	private GradeAggregator getComplexStructure(Grader<?> grader, double userGradeWeight, boolean withTimePenalty) {
		final GradeAggregator main = getUserNamedAggregator(grader, userGradeWeight);
		final GradeAggregator penalized;
		if (withTimePenalty) {
			penalized = GradeAggregator.parametric(C_MAIN, C_LATENESS, main);
		} else {
			penalized = main;
		}
		final GradeAggregator maxAmongAttempts = GradeAggregator.max(penalized);
		return maxAmongAttempts;
	}

	public <Y extends Exception> Exam getGrades(Grader<Y> grader, double userGradeWeight) throws X, Y, IOException {
		return getGrades(MAX_DEADLINE, Duration.ofMinutes(0), grader, userGradeWeight, TOptional.empty(), "");
	}

	public <Y extends Exception> Exam getGrades(ZonedDateTime deadline, Duration durationForZero, Grader<Y> grader,
			double userGradeWeight) throws X, Y, IOException {
		return getGrades(deadline, durationForZero, grader, userGradeWeight, TOptional.empty(), "");
	}

	public <Y extends Exception> Exam getAndWriteGrades(Grader<Y> grader, double userGradeWeight,
			Path outWithoutExtension, String docTitle) throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(BatchGitHistoryGrader.MAX_DEADLINE, Duration.ofMinutes(0), grader, userGradeWeight,
				TOptional.of(outWithoutExtension), docTitle);
	}

	public <Y extends Exception> Exam getAndWriteGrades(ZonedDateTime deadline, Duration durationForZero,
			Grader<Y> grader, double userGradeWeight, Path outWithoutExtension, String docTitle)
			throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(deadline, durationForZero, grader, userGradeWeight, TOptional.of(outWithoutExtension),
				docTitle);
	}

	private <Y extends Exception> Exam getGrades(ZonedDateTime deadline, Duration durationForZero, Grader<Y> grader,
			double userGradeWeight, TOptional<Path> outWithoutExtensionOpt, String docTitle) throws X, Y, IOException {
		checkArgument(deadline.equals(MAX_DEADLINE) == (durationForZero.getSeconds() == 0l));
		checkArgument(userGradeWeight > 0d);
		checkArgument(userGradeWeight < 1d);
		verify(outWithoutExtensionOpt.isPresent() == !docTitle.isEmpty());

		final LinkedHashMap<GitHubUsername, MarksTree> builder = new LinkedHashMap<>();
		final boolean withTimePenalty = !deadline.equals(MAX_DEADLINE);
		final GradeAggregator whole = getComplexStructure(grader, userGradeWeight, withTimePenalty);
		try (GitFileSystemWithHistoryFetcher fetcher = fetcherFactory.get()) {

			for (GitHubUsername author : fetcher.getAuthors()) {
				final ImmutableBiMap<Instant, MarksTree> byTime;

				final String commentGeneralCapped;
				final GitFileSystemHistory beforeCommitByGitHub;
				{
					final GitFileSystemHistory history = fetcher.goTo(author);
					LOGGER.info("Found {} commits (total).", history.getGraph().nodes().size());

					/* GitHub creates the very first commit when importing from a template. */
					final Optional<Instant> earliestTimeCommitByGitHub = history
							.filter(JavaMarkHelper::committerIsGitHub).filter(c -> !history.getRoots().contains(c))
							.asGitHistory().getTimestamps().values().stream().min(Comparator.naturalOrder());
					beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
							.map(t -> history.filter(
									c -> history.asGitHistory().getTimestamp(c.getCommit().getId()).isBefore(t), t))
							.orElse(history);

					commentGeneralCapped = earliestTimeCommitByGitHub.map(t -> "; ignored commits after "
							+ t.atZone(deadline.getZone()).toString() + ", sent by GitHub").orElse("");
				}

				final ImmutableSortedSet<Instant> consideredTimestamps = getTimestamps(beforeCommitByGitHub,
						deadline.toInstant());

				final ImmutableBiMap.Builder<Instant, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
				for (Instant timeCap : consideredTimestamps) {
					final GitFileSystemHistory capped = beforeCommitByGitHub
							.filter(r -> !beforeCommitByGitHub.getCommitDate(r).isAfter(timeCap), timeCap);

					final MarksTree grade = grader.grade(capped);

					final Mark userGrade = DeadlineGrader.getUsernameGrade(beforeCommitByGitHub, author).asNew();
					final MarksTree gradeWithUser = MarksTree
							.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

					final MarksTree perhapsPenalized;
					if (!withTimePenalty) {
						perhapsPenalized = gradeWithUser;
					} else {
						final Duration lateness = Duration.between(deadline.toInstant(), timeCap);
						final LinearPenalizer penalizer = LinearPenalizer.proportionalToLateness(durationForZero);
						final Mark remaining = penalizer.getFractionRemaining(lateness);
						LOGGER.debug("Lateness from {} to {} equal to {}; remaining {}.", deadline.toInstant(), timeCap,
								lateness, remaining);

						perhapsPenalized = MarksTree
								.composite(ImmutableMap.of(C_MAIN, gradeWithUser, C_LATENESS, remaining));
					}
					byTimeBuilder.put(timeCap, perhapsPenalized);
				}
				byTime = byTimeBuilder.build();

				final MarksTree byTimeGrade;
				if (byTime.isEmpty()) {
					byTimeGrade = Mark.zero(String.format("No commit found%s", commentGeneralCapped));
				} else {
					final ImmutableMap<Criterion, MarksTree> subsByTime = byTime.keySet().stream()
							.collect(ImmutableMap.toImmutableMap(i -> {
								final String cappingAt = i.equals(Instant.MAX) ? "No capping"
										: i.equals(Instant.MIN) ? "Capping at MIN"
												: ("Capping at " + i.atZone(deadline.getZone()).toString());
								return Criterion.given(cappingAt + commentGeneralCapped);
							}, byTime::get));
					byTimeGrade = MarksTree.composite(subsByTime);
				}
				builder.put(author, byTimeGrade);
				try {
					Grade.given(whole, byTimeGrade);
				} catch (AggregatorException e) {
//					Files.writeString(Path.of("Marks.json"), JsonSimpleGrade.toJson(byTimeGrade));
//					Files.writeString(Path.of("Aggregator.json"), JsonSimpleGrade.toJson(whole));
					LOGGER.info("Failed aggregating at {}, obtained {} which fails with {}.", author, byTimeGrade,
							whole);
					throw e;
				}

				outWithoutExtensionOpt
						.ifPresent(o -> write(new Exam(whole, ImmutableMap.copyOf(builder)), o, docTitle));
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

	private void write(Exam exam, Path outWithoutExtension, String docTitle) throws IOException {
		Files.writeString(outWithoutExtension.resolveSibling(outWithoutExtension.getFileName() + ".json"),
				JsonSimpleGrade.toJson(exam));

		LOGGER.debug("Reading usernames.");
		final JsonStudents studentsReader = JsonStudents.from(Files.readString(Path.of("usernames.json")));
		final ImmutableBiMap<GitHubUsername, StudentOnGitHub> students = studentsReader.getStudentsByGitHubUsername();

		final ImmutableMap<StudentOnGitHub, MarksTree> trees = CollectionUtils.transformKeys(exam.grades(),
				u -> Optional.ofNullable(students.get(u)).orElseThrow(
						() -> new NoSuchElementException(u.getUsername() + " among " + students.keySet())));
		final String csv = CsvGrades.newInstance(CsvGrades.STUDENT_IDENTITY_FUNCTION, 20).gradesToCsv(exam.aggregator(),
				trees);
		Files.writeString(outWithoutExtension.resolveSibling(outWithoutExtension.getFileName() + ".csv"), csv);

		final ImmutableMap<String, Grade> grades = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(GitHubUsername::getUsername, exam::getGrade));
		final String html = XmlUtils.asString(HtmlGrades.asHtml(grades, docTitle, 20d));
		Files.writeString(outWithoutExtension.resolveSibling(outWithoutExtension.getFileName() + ".html"), html);
	}
}
