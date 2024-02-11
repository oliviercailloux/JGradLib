package io.github.oliviercailloux.java_grade.graders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.g421.ConstantDiceRoller;
import io.github.oliviercailloux.g421.Game421;
import io.github.oliviercailloux.g421.PredictedDiceRoller;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.DoubleGrader;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gradr421 implements CodeGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Gradr421.class);

	public static final String PREFIX = "421";

	public static final ZonedDateTime DEADLINE_ORIGINAL =
			ZonedDateTime.parse("2022-04-08T14:17:00+02:00[Europe/Paris]");
	public static final Instant CAP_ORIGINAL =
			DEADLINE_ORIGINAL.toInstant().plus(Duration.ofMinutes(10));

	public static final ZonedDateTime DEADLINE_SECOND_CHANCE =
			ZonedDateTime.parse("2022-06-17T00:00:00+01:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.025d;

	public static void main(String[] args) throws Exception {
		// original();
		second();
	}

	public static void original() throws IOException {
		final GitFileSystemWithHistoryFetcher fetcher =
				GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader =
				BatchGitHistoryGrader.given(() -> fetcher);

		final Gradr421 grader421 = new Gradr421();
		final MavenCodeGrader<RuntimeException> m =
				MavenCodeGrader.basic(grader421, UncheckedIOException::new);

		batchGrader.getAndWriteGrades(DEADLINE_ORIGINAL, Duration.ofMinutes(5),
				GitFsGraderUsingLast.using(m), USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
				PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
		grader421.close();
		LOGGER.info("Done original, closed.");
	}

	public static void second() throws IOException {
		final GitFileSystemWithHistoryFetcher fetcher =
				GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader =
				BatchGitHistoryGrader.given(() -> fetcher);

		final Gradr421 grader421 = new Gradr421();
		final MavenCodeGrader<RuntimeException> m =
				MavenCodeGrader.basic(grader421, UncheckedIOException::new);
		final DoubleGrader doubleGrader = new DoubleGrader(m, DEADLINE_ORIGINAL.toInstant(),
				DEADLINE_SECOND_CHANCE.toInstant(), DEADLINE_ORIGINAL.getZone(), CAP_ORIGINAL, USER_WEIGHT);

		batchGrader.getAndWriteGrades(doubleGrader, Path.of("grades " + PREFIX + " second"),
				PREFIX + " second " + Instant.now().atZone(DEADLINE_SECOND_CHANCE.getZone()));
		grader421.close();
		LOGGER.info("Done second, closed.");
	}

	private static final Criterion C_STATE_0 = Criterion.given("Illegal state (0)");

	private static final Criterion C_STATE_1 = Criterion.given("Illegal state (1)");

	private static final Criterion C_ARG = Criterion.given("Illegal argument");
	private static final Criterion C0 = Criterion.given("Zero");

	private static final Criterion C1 = Criterion.given("One");

	private static final Criterion C60 = Criterion.given("Sixty");

	private final ExecutorService executors;

	public Gradr421() {
		executors = Executors.newCachedThreadPool();
	}

	private TryCatchAll<Game421> newGame(Instanciator instanciator) {
		final TryCatchAll<Game421> tryTarget =
				TryCatchAll.get(() -> instanciator.getInstanceOrThrow(Game421.class));
		final TryCatchAll<Game421> game = tryTarget.andApply(target -> SimpleTimeLimiter
				.create(executors).newProxy(target, Game421.class, Duration.ofSeconds(5)));
		return game;
	}

	@Override
	public MarksTree gradeCode(Instanciator instanciator) {
		final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();

		final ImmutableList<Integer> roll421 = ImmutableList.of(4, 2, 1);
		final ImmutableList<Integer> roll422 = ImmutableList.of(4, 2, 2);
		final ConstantDiceRoller constant421 = new ConstantDiceRoller(roll421);
		final ConstantDiceRoller constant422 = new ConstantDiceRoller(roll422);

		final TryCatchAll<Game421> game0 = newGame(instanciator);
		final boolean invocationFailed =
				game0.map(r -> false, c -> c instanceof InvocationTargetException);
		if (invocationFailed) {
			return Mark.zero("Invocation failed: " + game0.toString());
		}

		{
			final TryCatchAll<Game421> game = newGame(instanciator);
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(0));
			final boolean pass = got.map(r -> false, c -> c instanceof IllegalStateException);

			builder.put(Criterion.given("Illegal state (0)"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Game421> game = newGame(instanciator);
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(1));
			final boolean pass = got.map(r -> false, c -> c instanceof IllegalStateException);

			builder.put(Criterion.given("Illegal state (1)"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Game421> game =
					newGame(instanciator).andConsume(g -> g.setRoller(constant422));
			final boolean initSuccessful = game.isSuccess();
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(-14));
			final boolean pass =
					initSuccessful && got.map(r -> false, c -> c instanceof IllegalArgumentException);

			builder.put(Criterion.given("Illegal argument"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Game421> game =
					newGame(instanciator).andConsume(g -> g.setRoller(constant422));
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(0));
			final boolean pass = got.map(b -> !b, c -> false);

			builder.put(Criterion.given("Zero"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Boolean> got421;
			{
				final TryCatchAll<Game421> game =
						newGame(instanciator).andConsume(g -> g.setRoller(constant421));
				got421 = game.andApply(g -> g.tryGet421(1));
			}
			final TryCatchAll<Boolean> got422;
			{
				final TryCatchAll<Game421> game =
						newGame(instanciator).andConsume(g -> g.setRoller(constant422));
				got422 = game.andApply(g -> g.tryGet421(1));
			}

			final boolean pass421 = got421.map(b -> b, c -> false);
			final boolean pass422 = got422.map(b -> !b, c -> false);
			builder.put(Criterion.given("One"), Mark.binary(pass421 && pass422, "", String.format(
					"On successful roll: %s; on failed roll: %s.", got421.toString(), got422.toString())));
		}

		{

			final TryCatchAll<Boolean> got421;
			{
				final Stream<ImmutableList<Integer>> streamFirst = Stream.generate(() -> roll422);
				final Stream<ImmutableList<Integer>> streamSecond = Stream.generate(() -> roll421);
				final ImmutableList<ImmutableList<Integer>> rolls =
						Stream.concat(streamFirst.limit(50), streamSecond.limit(50))
								.collect(ImmutableList.toImmutableList());
				final PredictedDiceRoller roller = new PredictedDiceRoller(rolls);
				final TryCatchAll<Game421> game =
						newGame(instanciator).andConsume(g -> g.setRoller(roller));
				got421 = game.andApply(g -> g.tryGet421(60));
			}
			final TryCatchAll<Boolean> got422;
			{
				final Stream<ImmutableList<Integer>> streamFirst = Stream.generate(() -> roll422);
				final Stream<ImmutableList<Integer>> streamSecond = Stream.generate(() -> roll421);
				final ImmutableList<ImmutableList<Integer>> rolls =
						Stream.concat(streamFirst.limit(70), streamSecond.limit(30))
								.collect(ImmutableList.toImmutableList());
				final PredictedDiceRoller roller = new PredictedDiceRoller(rolls);
				final TryCatchAll<Game421> game =
						newGame(instanciator).andConsume(g -> g.setRoller(roller));
				got422 = game.andApply(g -> g.tryGet421(60));
			}

			final boolean pass421 = got421.map(b -> b, c -> false);
			final boolean pass422 = got422.map(b -> !b, c -> false);
			builder.put(Criterion.given("Sixty"), Mark.binary(pass421 && pass422, "", String.format(
					"On successful roll: %s; on failed roll: %s.", got421.toString(), got422.toString())));
		}

		return MarksTree.composite(builder.build());
	}

	@Override
	public GradeAggregator getCodeAggregator() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_STATE_0, 1.5d);
		builder.put(C_STATE_1, 2d);
		builder.put(C_ARG, 4d);
		builder.put(C0, 4d);
		builder.put(C1, 4d);
		builder.put(C60, 4d);
		return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
	}

	public void close() {
		executors.shutdownNow();
	}
}
