package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.DoubleMath;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.WarningsBehavior;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TSupplier;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompCust implements CodeGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CompCust.class);

	public static final String PREFIX = "computer-customer";

	public static final ZonedDateTime DEADLINE_ORIGINAL = LocalDateTime.parse("2023-06-19T15:50:00")
			.atZone(ZoneId.of("Europe/Paris"));
	public static final ZonedDateTime DEADLINE_SECOND_CHANCE = LocalDateTime.parse("2023-06-09T23:59:59")
			.atZone(ZoneId.of("Europe/Paris"));

	public static final double USER_WEIGHT = 0.0125d;

	public static Mark causeToMark(Throwable e) {
		final String messagePart = e.getMessage() == null ? "" : " with message ‘%s’".formatted(e.getMessage());
		return Mark.zero("Code failed with %s".formatted(e.getClass().getName()) + messagePart);
	}

	public static void main(String[] args) throws Exception {
//		original();
		second();
	}

	public static void original() throws IOException {
		final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
				.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader.given(() -> fetcher);

		final CompCust grader = new CompCust();
		final MavenCodeGrader<RuntimeException> m = MavenCodeGrader.penal(grader, UncheckedIOException::new,
				WarningsBehavior.DO_NOT_PENALIZE);

		batchGrader.getAndWriteGradesExp(DEADLINE_ORIGINAL, Duration.ofMinutes(30), GitFsGraderUsingLast.using(m),
//				USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
//				PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
				USER_WEIGHT, Path.of("grades " + PREFIX), PREFIX + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
		grader.close();
		LOGGER.info("Done original, closed.");
	}

	public static void second() throws IOException {
		final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
				.getRetrievingByPrefixAndFiltering(PREFIX, "Arvindesss");
//				.getRetrievingByPrefix(PREFIX);

		final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader.given(() -> fetcher);

		final CompCust grader = new CompCust();
		final MavenCodeGrader<RuntimeException> m = MavenCodeGrader.penal(grader, UncheckedIOException::new,
				WarningsBehavior.DO_NOT_PENALIZE);

		batchGrader.getAndWriteGrades(DEADLINE_SECOND_CHANCE, Duration.ofMinutes(120), GitFsGraderUsingLast.using(m),
				// USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
				// PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
				USER_WEIGHT, Path.of("grades " + PREFIX + " second"),
				PREFIX + Instant.now().atZone(DEADLINE_SECOND_CHANCE.getZone()));
		grader.close();
		LOGGER.info("Done original, closed.");
	}

	private static final String COMP = "io.github.oliviercailloux.exercices.computer.Computer";

	private static final Criterion C0 = Criterion.given("Anything committed");

	private static final Criterion CYCLE_ONE = Criterion.given("Cycle with one");
	private static final Criterion CYCLE_THREE = Criterion.given("Cycle with three");
	private static final Criterion CYCLE_MANY = Criterion.given("Cycle with many");

	private static final Criterion SIZE_ONE = Criterion.given("Size with one");
	private static final Criterion SIZE_THREE = Criterion.given("Size with three");
	private static final Criterion SIZE_MANY = Criterion.given("Size with many");

	private static final Criterion DUPL_ONE = Criterion.given("Dupl with one");
	private static final Criterion DUPL_THREE = Criterion.given("Dupl with three");
	private static final Criterion DUPL_MANY = Criterion.given("Dupl with many");

	private static final Criterion AS_SET_ONE = Criterion.given("AsSet with one");

	private static final Criterion AS_SET_THREE = Criterion.given("AsSet with three");

	private static final Criterion AS_SET_MANY = Criterion.given("AsSet with many");

	private static final Criterion SNAPSHOTS_ONE = Criterion.given("Snapshots with one");

	private static final Criterion SNAPSHOTS_THREE = Criterion.given("Snapshots with three");

	private static final Criterion SNAPSHOTS_MANY = Criterion.given("Snapshots with many");

	private static final Criterion EQUALS = Criterion.given("Equality");

	private final ExecutorService executors;

	private final SimpleTimeLimiter limiter;

	public CompCust() {
		executors = Executors.newCachedThreadPool();
		limiter = SimpleTimeLimiter.create(executors);
	}

	@Override
	public MarksTree gradeCode(Instanciator instanciator) {
		final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
		{
			builder.put(C0, Mark.one());
		}

		final ImmutableList<String> inputOne = ImmutableList.of("k", "k", "k");
		final ImmutableList<String> inputThree = ImmutableList.of("j", "kkk", "123");
		final ImmutableList<String> inputMany = IntStream.range(0, 30).boxed().map(i -> "elm" + i)
				.collect(ImmutableList.toImmutableList());

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputOne);

				return cycles(ImmutableSet.copyOf(inputOne), cycling);
			});
			builder.put(CYCLE_ONE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputThree);

				return cycles(ImmutableSet.copyOf(inputThree), cycling);
			});
			builder.put(CYCLE_THREE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputMany);

				return cycles(ImmutableSet.copyOf(inputMany), cycling);
			});
			builder.put(CYCLE_MANY, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputOne);
				final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
				return size == ImmutableSet.copyOf(inputOne).size();
			});
			builder.put(SIZE_ONE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputThree);
				final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
				return size == ImmutableSet.copyOf(inputThree).size();
			});
			builder.put(SIZE_THREE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputMany);
				final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
				return size == ImmutableSet.copyOf(inputMany).size();
			});
			builder.put(SIZE_MANY, mark(attempt));
		}

		{
			final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputOne);
				return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputOne), cycling));
			});
			builder.put(DUPL_ONE, attempt.orMapCause(CompCust::causeToMark));
		}

		{
			final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputThree);
				return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputThree), cycling));
			});
			builder.put(DUPL_THREE, attempt.orMapCause(CompCust::causeToMark));
		}

		{
			final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputMany);
				return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputMany), cycling));
			});
			builder.put(DUPL_MANY, attempt.orMapCause(CompCust::causeToMark));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputOne);
				return transformsToSet(ImmutableSet.copyOf(inputOne), cycling);
			});
			builder.put(AS_SET_ONE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputThree);
				return transformsToSet(ImmutableSet.copyOf(inputThree), cycling);
			});
			builder.put(AS_SET_THREE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputMany);
				return transformsToSet(ImmutableSet.copyOf(inputMany), cycling);
			});
			builder.put(AS_SET_MANY, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputOne);
				return snapshots(ImmutableSet.copyOf(inputOne), cycling);
			});
			builder.put(SNAPSHOTS_ONE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputThree);
				return snapshots(ImmutableSet.copyOf(inputThree), cycling);
			});
			builder.put(SNAPSHOTS_THREE, mark(attempt));
		}

		{
			final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
				final Iterable<?> cycling = init(instanciator, inputMany);
				return snapshots(ImmutableSet.copyOf(inputMany), cycling);
			});
			builder.put(SNAPSHOTS_MANY, mark(attempt));
		}

		{
			final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
				final ImmutableMultiset.Builder<ImmutableList<String>> inputsMultBuilder = ImmutableMultiset.builder();
				for (int i = 0; i < 59; ++i) {
					final ImmutableList<String> zero = ImmutableList.of("aa", "aa", "bb", "col" + i);
					final int shift = i % 3;
					final ImmutableList<String> part1 = zero.subList(shift, zero.size());
					final ImmutableList<String> part2 = zero.subList(0, shift);
					final ImmutableList<String> colors = ImmutableList.<String>builder().addAll(part1).addAll(part2)
							.build();
					verify(colors.size() == zero.size(), "Unexpected size.");

					final int occurrences = i < 50 ? 1 : (i - 48);

					inputsMultBuilder.addCopies(colors, occurrences);
				}
				final ImmutableMultiset<ImmutableList<String>> inputsMult = inputsMultBuilder.build();
				Mark mark = Mark.one();
				final ImmutableList<ImmutableList<String>> inputs = inputsMult.asList();
				for (ImmutableList<String> input1 : inputs) {
					final Iterable<?> cycling1 = init(instanciator, input1);
					for (ImmutableList<String> input2 : inputs) {
						final Iterable<?> cycling2 = init(instanciator, input2);
						final boolean obsEquals = cycling1.equals(cycling2);
						final boolean expectedEquals = ImmutableSet.copyOf(input1).equals(ImmutableSet.copyOf(input2));
						verify(mark.getPoints() == 1d, "Unexpected points state.");
						if (obsEquals != expectedEquals) {
							if (expectedEquals) {
								mark = Mark.zero(
										"Using equivalent inputs %s and %s, observed non equal resulting instances"
												.formatted(input1, input2));
							} else {
								mark = Mark.zero("Using different inputs %s and %s, observed equal resulting instances"
										.formatted(input1, input2));
							}
						} else if (expectedEquals) {
							final boolean equalH = cycling1.hashCode() == cycling2.hashCode();
							if (!equalH) {
								mark = Mark.zero(
										"Using equivalent inputs %s and %s, observed equal resulting instances but different hash codes"
												.formatted(input1, input2));
							}
						}
						if (mark.getPoints() == 0d) {
							break;
						}
					}
					if (mark.getPoints() == 0d) {
						break;
					}
				}
				return mark;
			});
			builder.put(EQUALS, attempt.orMapCause(CompCust::causeToMark));
		}

		return MarksTree.composite(builder.build());
	}

	@Override
	public MarksTree gradeComp(Instanciator instanciator) {
		final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
		{
			builder.put(C0, Mark.one());
		}

		{
			final Object comp = instanciator.invokeStatic(COMP, Object.class, "instance", ImmutableList.of()).orThrow();
			Instanciator.invoke(comp, Void.class, "addOperand", 1d);
			final Object comp2 = instanciator.invokeStatic(COMP, Object.class, "instance", ImmutableList.of())
					.orThrow();
			Instanciator.invoke(comp2, Void.class, "addOperand", 3d);
			Instanciator.invoke(comp, Void.class, "addOperand", 2d);
			Instanciator.invoke(comp2, Void.class, "addOperand", 17d);
			final TryCatchAll<Double> obs = Instanciator.invokeProducing(comp2, Double.class, "apply", "+");
			final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(20d, o, 1e-6d));
			builder.put(ADD, mapped);
		}

		{
			final Object comp = instanciator.invokeStatic(COMP, Object.class, "oneOp", ImmutableList.of(30d)).orThrow();
			Instanciator.invoke(comp, Void.class, "addOperand", 1d);
			final Object comp2 = instanciator.invokeStatic(COMP, Object.class, "oneOp", ImmutableList.of(3d)).orThrow();
			Instanciator.invoke(comp2, Void.class, "addOperand", 3d);
			final TryCatchAll<Optional<Void>> secondAdd = Instanciator.invoke(comp2, Void.class, "addOperand", 17d);
			final MarksTree mapped = secondAdd.map(r -> Mark.zero("Unexpected answer to spurious add."),
					c -> Mark.binary(c instanceof IllegalStateException));
			builder.put(ONE_THEN_SPURIOUS, mapped);
		}

		{
			final Object comp = instanciator.invokeStatic(COMP, Object.class, "oneOp", ImmutableList.of(30d)).orThrow();
			Instanciator.invoke(comp, Void.class, "addOperand", 1d);
			final Object comp2 = instanciator.invokeStatic(COMP, Object.class, "oneOp", ImmutableList.of(3d)).orThrow();
			Instanciator.invoke(comp2, Void.class, "addOperand", 3d);
			Instanciator.invoke(comp, Void.class, "addOperand", 5d);
			Instanciator.invoke(comp2, Void.class, "addOperand", 2d);
			final TryCatchAll<Double> obs = Instanciator.invokeProducing(comp2, Double.class, "apply", "/");
			final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(1.5d, o, 1e-6d));
			builder.put(ONE_THEN_DIV, mapped);
		}

		{
			final Object comp = instanciator.invokeStatic(COMP, Object.class, "duplOp", ImmutableList.of(30d))
					.orThrow();
			Instanciator.invoke(comp, Void.class, "addOperand", 1d);
			final Object comp2 = instanciator.invokeStatic(COMP, Object.class, "duplOp", ImmutableList.of(3d))
					.orThrow();
			final TryCatchAll<Double> obs = Instanciator.invokeProducing(comp2, Double.class, "apply", "*");
			final MarksTree mapped = markG(obs, o -> o);
			builder.put(DUPL_THEN_MULT, mapped);
		}

		{
			final Object comp = instanciator.invokeStatic(COMP, Object.class, "instance", ImmutableList.of()).orThrow();
			Instanciator.invoke(comp, Void.class, "addOperand", 1d);
			final Object comp2 = instanciator.invokeStatic(COMP, Object.class, "instance", ImmutableList.of())
					.orThrow();
			Instanciator.invoke(comp2, Void.class, "addOperand", 3d);
			Instanciator.invoke(comp, Void.class, "addOperand", 2d);
			Instanciator.invoke(comp2, Void.class, "addOperand", 17d);
			final TryCatchAll<Double> obs = Instanciator.invokeProducing(comp2, Double.class, "apply", "+");
			final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(20d, o, 1e-6d));
			builder.put(LOGS, mapped);
		}
		return MarksTree.composite(builder.build());
	}

	private <T> T callWithTimeout(TSupplier<? extends T, ?> callable) throws Throwable {
		return limiter.callWithTimeout(() -> TryCatchAll.get(callable), Duration.ofSeconds(5)).orThrow();
	}

	@SuppressWarnings("unused")
	private Mark mark(Throwable problem, boolean success) {
		final Mark mark;
		if (success) {
			mark = Mark.one();
		} else if (problem == null) {
			mark = Mark.zero();
		} else {
			mark = Mark.zero(problem.getMessage());
		}
		return mark;
	}

	private <T> MarksTree markG(TryCatchAll<T> attempt, Function<T, Boolean> grader) {
		return attempt.map(t -> Mark.binary(grader.apply(t)), e -> causeToMark(e));
	}

	private MarksTree mark(TryCatchAll<Boolean> attempt) {
		return attempt.map(s -> Mark.binary(s), e -> causeToMark(e));
	}

	@Override
	/**
	 * For up to three colors without asSetOfColors() and equality: 7pts. More than
	 * three colors without asSetOfColors() and equality: 6pts. More than three
	 * colors, asSetOfColors(): 4 pts. More than three colors, equality: 3 pts.
	 */
	public GradeAggregator getCodeAggregator() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C0, 0.25d);
		builder.put(CYCLE_ONE, 0.75d);
		builder.put(CYCLE_THREE, 1.25d);
		builder.put(SIZE_ONE, 0.75d);
		builder.put(SIZE_THREE, 0.75d);
		builder.put(DUPL_ONE, 0.75d);
		builder.put(DUPL_THREE, 0.75d);
		builder.put(SNAPSHOTS_ONE, 0.75d);
		builder.put(SNAPSHOTS_THREE, 0.75d);

		builder.put(CYCLE_MANY, 1.5d);
		builder.put(SIZE_MANY, 1.5d);
		builder.put(DUPL_MANY, 1.5d);
		builder.put(SNAPSHOTS_MANY, 1.5d);

		builder.put(AS_SET_ONE, 1d);
		builder.put(AS_SET_THREE, 1d);
		builder.put(AS_SET_MANY, 2d);
		builder.put(EQUALS, 3d);
		return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
	}

	public void close() {
		executors.shutdownNow();
	}
}
