package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.jaris.throwing.TSupplier;
import io.github.oliviercailloux.xml.XmlUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGitHistoryGrader<X extends Exception> {
	private static class IncrPenalizer {
		public static IncrPenalizer instance(Duration durationForZero) {
			return new IncrPenalizer(Math.toIntExact(durationForZero.getSeconds()));
		}

		private int nbSecondsZero;

		private IncrPenalizer(int nbSecondsZero) {
			this.nbSecondsZero = nbSecondsZero;
			checkArgument(nbSecondsZero > 0);
		}

		public io.github.oliviercailloux.grade.Mark getFractionRemaining(Duration lateness) {
			/*
			 * Let x in [0, 1] determine the fraction of lateness we are at (x = 1 when
			 * lateness == nbSecondsZero).
			 *
			 * Let f(x) = the penalty.
			 *
			 * We want f(0) = 0; f(1/2) = z (for example, z = 1/3); f(1) = 1.
			 *
			 * We want the function to be strictly increasing and convex.
			 *
			 * We opt for, letting α = 1/z − 1, f(x) = (α^(2x) − 1)/(α² − 1). (This is the
			 * function of the form (e^ax − b)/c that satisfies the above constraints.)
			 *
			 * Setting z = 1/3, we get α = 2 and f(x) = (4^x − 1)/3.
			 */
			final io.github.oliviercailloux.grade.Mark remaining;
			if (!lateness.isNegative() && !lateness.isZero()) {
				final double x = Math.min(lateness.getSeconds() / (double) nbSecondsZero, 1d);
				verify(0d < x);
				verify(x <= 1d);
				final double penalty = (Math.pow(4d, x) - 1d) / 3d;
				remaining = io.github.oliviercailloux.grade.Mark.given(1d - penalty, "Lateness: " + lateness);
			} else {
				remaining = io.github.oliviercailloux.grade.Mark.one();
			}
			return remaining;
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchGitHistoryGrader.class);

	public static final ZonedDateTime MAX_DEADLINE = Instant.ofEpochMilli(Long.MAX_VALUE).atZone(ZoneOffset.UTC);

	public static <X extends Exception> BatchGitHistoryGrader<X> given(
			TSupplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		return new BatchGitHistoryGrader<>(fetcherFactory);
	}

	private final TSupplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory;

	private BatchGitHistoryGrader(TSupplier<GitFileSystemWithHistoryFetcher, X> fetcherFactory) {
		this.fetcherFactory = checkNotNull(fetcherFactory);
	}

	public <Y extends Exception> Exam getGrades(GitFsGrader<Y> grader, double userGradeWeight)
			throws X, Y, IOException {
		return getGrades(MAX_DEADLINE, Duration.ofMinutes(0), grader, userGradeWeight, TOptional.empty(), "");
	}

	public <Y extends Exception> Exam getGrades(ZonedDateTime deadline, Duration durationForZero, GitFsGrader<Y> grader,
			double userGradeWeight) throws X, Y, IOException {
		return getGrades(deadline, durationForZero, grader, userGradeWeight, TOptional.empty(), "");
	}

	public <Y extends Exception> Exam getAndWriteGrades(GitFsGrader<Y> grader, double userGradeWeight,
			Path outWithoutExtension, String docTitle) throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(BatchGitHistoryGrader.MAX_DEADLINE, Duration.ofMinutes(0), grader, userGradeWeight,
				TOptional.of(outWithoutExtension), docTitle);
	}

	public <Y extends Exception> Exam getAndWriteGrades(ZonedDateTime deadline, Duration durationForZero,
			GitFsGrader<Y> grader, double userGradeWeight, Path outWithoutExtension, String docTitle)
			throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(deadline, durationForZero, grader, userGradeWeight, TOptional.of(outWithoutExtension),
				docTitle);
	}

	public <Y extends Exception> Exam getAndWriteGradesExp(ZonedDateTime deadline, Duration durationForZero,
			GitFsGrader<Y> grader, double userGradeWeight, Path outWithoutExtension, String docTitle)
			throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(deadline, durationForZero, false, grader, userGradeWeight, TOptional.of(outWithoutExtension),
				docTitle);
	}

	public <Y extends Exception> Exam getAndWriteGrades(Grader<Y> ext, Path outWithoutExtension, String docTitle)
			throws X, Y, IOException {
		checkArgument(!docTitle.isEmpty());
		return getGrades(ext, TOptional.of(outWithoutExtension), docTitle);
	}

	private <Y extends Exception> Exam getGrades(ZonedDateTime deadline, Duration durationForZero,
			GitFsGrader<Y> grader, double userGradeWeight, TOptional<Path> outWithoutExtensionOpt, String docTitle)
			throws X, Y, IOException {
		return getGrades(deadline, durationForZero, true, grader, userGradeWeight, outWithoutExtensionOpt, docTitle);
	}

	private <Y extends Exception> Exam getGrades(ZonedDateTime deadline, Duration durationForZero, boolean useLinear,
			GitFsGrader<Y> grader, double userGradeWeight, TOptional<Path> outWithoutExtensionOpt, String docTitle)
			throws X, Y, IOException {
		checkArgument(deadline.equals(MAX_DEADLINE) == (durationForZero.getSeconds() == 0l));
		checkArgument(userGradeWeight > 0d);
		checkArgument(userGradeWeight < 1d);
		verify(outWithoutExtensionOpt.isPresent() == !docTitle.isEmpty());

		final boolean withTimePenalty = !deadline.equals(MAX_DEADLINE);
		final GradeModifier penalizerModifier;
		if (!withTimePenalty) {
			penalizerModifier = new EmptyModifier();
		} else {
			if (useLinear) {
				final LinearPenalizer penalizer = LinearPenalizer.proportionalToLateness(durationForZero);
				penalizerModifier = GradePenalizer.using(penalizer, deadline.toInstant());
			} else {
				final IncrPenalizer p = IncrPenalizer.instance(durationForZero);
				penalizerModifier = GradePenalizer.usingFunction(p::getFractionRemaining, deadline.toInstant());
			}
		}
//		final ByTimeGrader<Y> byTimeGrader = ByTimeGrader.using(deadline, grader, penalizerModifier, userGradeWeight);
//		return getGrades(byTimeGrader, outWithoutExtensionOpt, docTitle);
		final ComplexGrader<Y> complexGrader = ComplexGrader.using(grader, penalizerModifier, userGradeWeight);
		final ByTimeAndGitHubIgnoringGrader<Y> internalGrader = new ByTimeAndGitHubIgnoringGrader<>(deadline,
				complexGrader);
		return getGrades(internalGrader, outWithoutExtensionOpt, docTitle);
	}

	private <Y extends Exception> Exam getGrades(Grader<Y> ext, TOptional<Path> outWithoutExtensionOpt, String docTitle)
			throws X, Y, IOException {
		final GradeAggregator whole = ext.getAggregator();

		final LinkedHashMap<GitHubUsername, MarksTree> builder = new LinkedHashMap<>();
		try (GitFileSystemWithHistoryFetcher fetcher = fetcherFactory.get()) {

			for (GitHubUsername author : fetcher.getAuthors()) {
				final GitHistorySimple history = fetcher.goToFs(author);
				final MarksTree byTimeGrade = ext.grade(author, history);
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
