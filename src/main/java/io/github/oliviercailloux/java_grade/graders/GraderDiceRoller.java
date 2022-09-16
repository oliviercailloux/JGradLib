package io.github.oliviercailloux.java_grade.graders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.g421.CyclicDiceRoller;
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
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraderDiceRoller implements CodeGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderDiceRoller.class);

	public static final String PREFIX = "dice-roller";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2022-05-13T14:25:00+02:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.025d;

	static record Pair(int first, int second) {

	}

	static record Triple(int first, int second, int third) {
		public Triple cycle() {
			return new Triple(third, first, second);
		}
	}

	public static void main(String[] args) throws Exception {
		final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
				.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader.given(() -> fetcher);

		final GraderDiceRoller grader421 = new GraderDiceRoller();
		final MavenCodeGrader<RuntimeException> m = MavenCodeGrader.basic(grader421, UncheckedIOException::new);

		batchGrader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(5), GitFsGraderUsingLast.using(m), USER_WEIGHT,
				Path.of("grades " + PREFIX), PREFIX + Instant.now().atZone(DEADLINE.getZone()));
		grader421.close();
		LOGGER.info("Done, closed.");
	}

	private static final Criterion C_STATE_0 = Criterion.given("Illegal state (not rolled)");

	private static final Criterion C_STATE_1 = Criterion.given("Illegal state (set; not rolled)");

	private static final Criterion C_ARG = Criterion.given("Illegal argument (set)");
	private static final Criterion C_JUST_ROLL = Criterion.given("Just roll");

	private static final Criterion C_SET_ROLL_ONCE = Criterion.given("Set then roll once");

	private static final Criterion C_SET_ROLL = Criterion.given("Set then roll");

	private final ExecutorService executors;

	public GraderDiceRoller() {
		executors = Executors.newCachedThreadPool();
	}

	private TryCatchAll<CyclicDiceRoller> newInstance(Instanciator instanciator) {
		final TryCatchAll<CyclicDiceRoller> tryTarget = TryCatchAll
				.get(() -> instanciator.getInstanceOrThrow(CyclicDiceRoller.class));
		final TryCatchAll<CyclicDiceRoller> instance = tryTarget.andApply(target -> SimpleTimeLimiter.create(executors)
				.newProxy(target, CyclicDiceRoller.class, Duration.ofSeconds(5)));
		return instance;
	}

	@Override
	public MarksTree gradeCode(Instanciator instanciator) {
		final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();

		final TryCatchAll<CyclicDiceRoller> roller0 = newInstance(instanciator);
		final boolean invocationFailed = roller0.map(r -> false, c -> c instanceof InvocationTargetException);
		if (invocationFailed) {
			return Mark.zero("Invocation failed: " + roller0.toString());
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final TryCatchAll<Integer> got = roller.andApply(CyclicDiceRoller::first);
			final boolean pass = got.map(r -> false, c -> c instanceof IllegalStateException);

			builder.put(C_STATE_0, Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final TryCatchAll<CyclicDiceRoller> rollerSet = roller.andConsume(r -> r.setResult(1, 2, 3));
			final TryCatchAll<Integer> got = rollerSet.andApply(CyclicDiceRoller::first);
			final boolean pass = got.map(r -> false, c -> c instanceof IllegalStateException);

			builder.put(C_STATE_1, Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final TryCatchAll<CyclicDiceRoller> got = roller.andConsume(r -> r.setResult(1, -2, 3));
			final boolean pass = got.map(r -> false, c -> c instanceof IllegalArgumentException);

			builder.put(C_ARG, Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final ImmutableList<TryCatchAll<Triple>> triples = IntStream.range(0, 4).boxed()
					.map(i -> current(roller.andConsume(CyclicDiceRoller::roll)))
					.collect(ImmutableList.toImmutableList());
			final ImmutableList<TryCatchAll<Triple>> expected = Stream.generate(() -> new Triple(1, 1, 1))
					.map(TryCatchAll::success).limit(4).collect(ImmutableList.toImmutableList());
			final boolean pass = triples.equals(expected);

			builder.put(C_JUST_ROLL, Mark.binary(pass, "", triples.toString()));
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final TryCatchAll<CyclicDiceRoller> rollerSet = roller.andConsume(r -> r.setResult(2, 4, 6));
			final TryCatchAll<Triple> triple = current(rollerSet.andConsume(CyclicDiceRoller::roll));
			final TryCatchAll<Triple> expected = TryCatchAll.success(new Triple(2, 4, 6));
			final boolean pass = triple.equals(expected);

			builder.put(C_SET_ROLL_ONCE, Mark.binary(pass, "", triple.toString()));
		}

		{
			final TryCatchAll<CyclicDiceRoller> roller = newInstance(instanciator);
			final TryCatchAll<CyclicDiceRoller> rollerSet = roller.andConsume(r -> r.setResult(2, 4, 6));
			final ImmutableList<TryCatchAll<Triple>> triples = IntStream.range(0, 4).boxed()
					.map(i -> current(rollerSet.andConsume(CyclicDiceRoller::roll)))
					.collect(ImmutableList.toImmutableList());
			final Triple t246 = new Triple(2, 4, 6);
			final ImmutableList<TryCatchAll<Triple>> expected = Stream.iterate(t246, Triple::cycle)
					.map(TryCatchAll::success).limit(4).collect(ImmutableList.toImmutableList());
			final boolean pass = triples.equals(expected);

			builder.put(C_SET_ROLL, Mark.binary(pass, "", triples.toString()));
		}

		return MarksTree.composite(builder.build());
	}

	private TryCatchAll<Triple> current(final TryCatchAll<CyclicDiceRoller> rolled) {
		/* TODO simplify. */
		final TryCatchAll<Integer> first = rolled.andApply(CyclicDiceRoller::first);
		final TryCatchAll<Integer> second = rolled.andApply(CyclicDiceRoller::second);
		final TryCatchAll<Pair> dbl = first.and(second, Pair::new);
		final TryCatchAll<Integer> third = rolled.andApply(CyclicDiceRoller::third);
		final TryCatchAll<Triple> triple = dbl.and(third, (d, t) -> new Triple(d.first, d.second, t));
//			final Optional<Throwable> failure = first.getCause().or(second::getCause).or(third::getCause);
//			final TryCatchAll<Triple> triple = failure.map(f -> TryCatchAll.<Triple>failure(f))
//					.orElse(TryCatchAll.get(() -> new Triple(first.orThrow(VerifyException::new),
//							second.orThrow(VerifyException::new), third.orThrow(VerifyException::new))));
		return triple;
	}

	@Override
	public GradeAggregator getCodeAggregator() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_STATE_0, 2d);
		builder.put(C_STATE_1, 2d);
		builder.put(C_ARG, 2.5d);
		builder.put(C_JUST_ROLL, 6.5d);
		builder.put(C_SET_ROLL_ONCE, 3.0d);
		builder.put(C_SET_ROLL, 3.5d);
		return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
	}

	public void close() {
		executors.shutdownNow();
	}
}
