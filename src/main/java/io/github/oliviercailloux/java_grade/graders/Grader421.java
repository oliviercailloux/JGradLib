package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.g421.ConstantDiceRoller;
import io.github.oliviercailloux.g421.Game421;
import io.github.oliviercailloux.g421.PredictedDiceRoller;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.ByTimeGrader;
import io.github.oliviercailloux.grade.ByTimeGrader.PreparedGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.grade.ExtendedGrader;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GradeModifier;
import io.github.oliviercailloux.grade.GradePenalizer;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Grader421 implements CodeGrader<RuntimeException> {
	public static class DoubleGrader implements ExtendedGrader<IOException> {

		private static final Criterion C_DIFF = Criterion.given("diff");
		private static final Criterion C_OLD = Criterion.given("old");
		private static final Criterion C_SECOND = Criterion.given("second");
		private final Grader421 grader421;
		private final MavenCodeGrader<RuntimeException> m;
		private final LinearPenalizer penalizer;
		private final GradeModifier penalizerModifier;
		private final ByTimeGrader<RuntimeException> byTimeGraderOld;
		private final ByTimeGrader<RuntimeException> byTimeGraderSecond;

		public DoubleGrader(Grader421 g) {
			grader421 = g;
			m = new MavenCodeGrader<>(grader421);
			penalizer = LinearPenalizer.proportionalToLateness(Duration.ofMinutes(5));
			penalizerModifier = GradePenalizer.using(penalizer, DEADLINE_ORIGINAL.toInstant());
			byTimeGraderOld = ByTimeGrader.using(DEADLINE_ORIGINAL, m, penalizerModifier, USER_WEIGHT);
			byTimeGraderSecond = ByTimeGrader.using(DEADLINE_SECOND_CHANCE, m, penalizerModifier, USER_WEIGHT);
		}

		@Override
		public MarksTree grade(GitHubUsername author, GitFileSystemHistory data) throws IOException {
			final ImmutableSortedSet<Instant> timestampsOriginal = ByTimeGrader.getTimestamps(data,
					DEADLINE_ORIGINAL.toInstant(), CAP_ORIGINAL);
			final ImmutableSortedSet<Instant> timestampsSecond = ByTimeGrader.getTimestamps(data,
					DEADLINE_SECOND_CHANCE.toInstant(), Instant.MAX);
			final PreparedGrader<RuntimeException> preparedOld = byTimeGraderOld.prepared(author, data);
			final PreparedGrader<RuntimeException> preparedSecond = byTimeGraderSecond.prepared(author, data);

			final ImmutableBiMap.Builder<Criterion, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
			for (Instant capO : timestampsOriginal) {
				for (Instant capS : timestampsSecond) {
					final MarksTree old = preparedOld.grade(capO);
					final Path javaOld = java();

					final MarksTree second = preparedSecond.grade(capS);
					final Path javaSecond = java();
					final int diff = diff(javaOld, javaSecond);
					final double propOld = Double.max(20d, diff) / 20d;
					final String commentDiff = "Diff between '%s' and '%s' (%s ≠ lines / 20).".formatted(javaOld,
							javaSecond, propOld);

					final MarksTree merged = MarksTree.composite(
							ImmutableMap.of(C_DIFF, Mark.given(propOld, commentDiff), C_OLD, old, C_SECOND, second));

					byTimeBuilder.put(
							Criterion.given(preparedOld.asString(capO) + "; " + preparedSecond.asString(capS)), merged);
				}
			}
			final ImmutableBiMap<Criterion, MarksTree> byTime = byTimeBuilder.build();

			final MarksTree byTimeGrade;
			if (byTime.isEmpty()) {
				byTimeGrade = Mark.zero(String.format("No commit found%s", preparedSecond.getCommentGeneralCapped()));
			} else {
				byTimeGrade = MarksTree.composite(byTime);
			}

			return byTimeGrade;
		}

		private Path java() throws IOException {
			final ImmutableMap<Path, MarksTree> gradedProjectsOld = m.getGradedProjects();
			final Path oldPath = Iterables.getOnlyElement(gradedProjectsOld.keySet());
			final ImmutableSet<Path> javas = Files.find(oldPath, Integer.MAX_VALUE, (p, a) -> matches(p))
					.collect(ImmutableSet.toImmutableSet());
			final Path javaOld = Iterables.getOnlyElement(javas);
			return javaOld;
		}

		private boolean matches(Path p) {
			final Pattern pattern = Pattern.compile("implements([\\v\\h])+DiceRoller");
			verify(pattern.matcher("implements DiceRoller").find());
			verify(pattern.matcher("implements\n \t DiceRoller").find());
			verify(pattern.matcher("implements  DiceRoller").find());
			verify(!pattern.matcher("implements CyclicDiceRoller").find());
			verify(!pattern.matcher("implementsDiceRoller").find());
			return p.getFileName() != null && p.getFileName().toString().endsWith(".java")
					&& pattern.matcher(IO_UNCHECKER.getUsing(() -> Files.readString(p))).find();
		}

		private int diff(Path javaOld, Path javaSecond) {
			final ImmutableSet<String> contentOld = ImmutableSet
					.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaOld)));
			final ImmutableSet<String> contentSecond = ImmutableSet
					.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaSecond)));
			return Sets.symmetricDifference(contentOld, contentSecond).size();
		}

		@Override
		public GradeAggregator getAggregator() {
			final GradeAggregator oldAg = byTimeGraderOld.getPenalizedAggregator();
			final GradeAggregator secondAg = byTimeGraderSecond.getPenalizedAggregator();
			return GradeAggregator.parametric(C_OLD, C_DIFF, oldAg, secondAg);
		}

	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Grader421.class);

	public static final String PREFIX = "421";

	public static final ZonedDateTime DEADLINE_ORIGINAL = ZonedDateTime
			.parse("2022-04-08T14:17:00+01:00[Europe/Paris]");
	public static final Instant CAP_ORIGINAL = DEADLINE_ORIGINAL.toInstant().plus(Duration.ofDays(2));

	public static final ZonedDateTime DEADLINE_SECOND_CHANCE = ZonedDateTime
			.parse("2022-05-09T00:00:00+01:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.025d;

	public static void main(String[] args) throws Exception {
		final GitFileSystemWithHistoryFetcher subFetcher = GitFileSystemWithHistoryFetcherByPrefix
				.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader.given(() -> subFetcher);

		final Grader421 grader421 = new Grader421();
		final DoubleGrader doubleGrader = new DoubleGrader(grader421);

		batchGrader.getAndWriteGrades(doubleGrader, Path.of("grades " + PREFIX),
				PREFIX + " " + Instant.now().atZone(DEADLINE_SECOND_CHANCE.getZone()));
		grader421.close();
		LOGGER.info("Done, closed.");
	}

	private static final Criterion C_STATE_0 = Criterion.given("Illegal state (0)");

	private static final Criterion C_STATE_1 = Criterion.given("Illegal state (1)");

	private static final Criterion C_ARG = Criterion.given("Illegal argument");
	private static final Criterion C0 = Criterion.given("Zero");

	private static final Criterion C1 = Criterion.given("One");

	private static final Criterion C60 = Criterion.given("Sixty");

	private final ExecutorService executors;

	public Grader421() {
		executors = Executors.newCachedThreadPool();
	}

	private TryCatchAll<Game421> newGame(Instanciator instanciator) {
		final TryCatchAll<Game421> tryTarget = TryCatchAll.get(() -> instanciator.getInstanceOrThrow(Game421.class));
		final TryCatchAll<Game421> game = tryTarget.andApply(
				target -> SimpleTimeLimiter.create(executors).newProxy(target, Game421.class, Duration.ofSeconds(5)));
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
		final boolean invocationFailed = game0.map(r -> false, c -> c instanceof InvocationTargetException);
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
			final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(constant422));
			final boolean initSuccessful = game.isSuccess();
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(-14));
			final boolean pass = initSuccessful && got.map(r -> false, c -> c instanceof IllegalArgumentException);

			builder.put(Criterion.given("Illegal argument"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(constant422));
			final TryCatchAll<Boolean> got = game.andApply(g -> g.tryGet421(0));
			final boolean pass = got.map(b -> !b, c -> false);

			builder.put(Criterion.given("Zero"), Mark.binary(pass, "", got.toString()));
		}

		{
			final TryCatchAll<Boolean> got421;
			{
				final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(constant421));
				got421 = game.andApply(g -> g.tryGet421(1));
			}
			final TryCatchAll<Boolean> got422;
			{
				final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(constant422));
				got422 = game.andApply(g -> g.tryGet421(1));
			}

			final boolean pass421 = got421.map(b -> b, c -> false);
			final boolean pass422 = got422.map(b -> !b, c -> false);
			builder.put(Criterion.given("One"), Mark.binary(pass421 && pass422, "", String
					.format("On successful roll: %s; on failed roll: %s.", got421.toString(), got422.toString())));
		}

		{

			final TryCatchAll<Boolean> got421;
			{
				final Stream<ImmutableList<Integer>> streamFirst = Stream.generate(() -> roll422);
				final Stream<ImmutableList<Integer>> streamSecond = Stream.generate(() -> roll421);
				final ImmutableList<ImmutableList<Integer>> rolls = Stream
						.concat(streamFirst.limit(50), streamSecond.limit(50)).collect(ImmutableList.toImmutableList());
				final PredictedDiceRoller roller = new PredictedDiceRoller(rolls);
				final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(roller));
				got421 = game.andApply(g -> g.tryGet421(60));
			}
			final TryCatchAll<Boolean> got422;
			{
				final Stream<ImmutableList<Integer>> streamFirst = Stream.generate(() -> roll422);
				final Stream<ImmutableList<Integer>> streamSecond = Stream.generate(() -> roll421);
				final ImmutableList<ImmutableList<Integer>> rolls = Stream
						.concat(streamFirst.limit(70), streamSecond.limit(30)).collect(ImmutableList.toImmutableList());
				final PredictedDiceRoller roller = new PredictedDiceRoller(rolls);
				final TryCatchAll<Game421> game = newGame(instanciator).andConsume(g -> g.setRoller(roller));
				got422 = game.andApply(g -> g.tryGet421(60));
			}

			final boolean pass421 = got421.map(b -> b, c -> false);
			final boolean pass422 = got422.map(b -> !b, c -> false);
			builder.put(Criterion.given("Sixty"), Mark.binary(pass421 && pass422, "", String
					.format("On successful roll: %s; on failed roll: %s.", got421.toString(), got422.toString())));
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
