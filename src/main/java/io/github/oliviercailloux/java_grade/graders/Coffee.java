package io.github.oliviercailloux.java_grade.graders;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAllVoid;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.utils.Summarizer;
import io.github.oliviercailloux.samples.coffee.CoffeeMachine;
import io.github.oliviercailloux.samples.coffee.EspressoMachine;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Coffee {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Coffee.class);
	public static final String PREFIX = "coffee";
	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-03-03T14:31:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		/* Nb should give 1.0/20 pts for user.grade. */
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
//				.setRepositoriesFilter(r -> r.getUsername().equals("…"));
		final GitGeneralGrader grader = GitGeneralGrader
				.using(fetcher,
						DeadlineGrader.usingInstantiatorGrader(Coffee::grade, DEADLINE).setPenalizer(
								DeadlineGrader.LinearPenalizer.proportionalToLateness(Duration.ofSeconds(300))))
				.setExcludeCommitsByAuthors(ImmutableSet.of("Olivier Cailloux"));// .setExcludeCommitsByGitHub(true);
		grader.grade();
		/** TODO see why still open. */
		// final Path src = Path.of("/tmp/coffee-…/.git/");
		// final GitFileFileSystem gitFs =
		// GitFileSystemProvider.getInstance().newFileSystemFromGitDir(src);

		Summarizer.create().setPrefix(PREFIX).setDissolveCriteria(ImmutableSet.of(Criterion.given("Warnings")))
//		.setPatched()
				// .restrictTo(ImmutableSet.of(GitHubUsername.given(""),
				// GitHubUsername.given("")))
				.summarize();
	}

	private Coffee() {
	}

	public static IGrade grade(Instanciator instanciator) {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
		final Optional<CoffeeMachine> dripMachineOpt = instanciator.getInstance(CoffeeMachine.class);
		final IGrade dripGrade = dripMachineOpt.map(Coffee::getDripGrade)
				.orElse(Mark.zero("Could not initialize drip machine."));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Drip"), dripGrade, 8d));

		final Optional<EspressoMachine> espressoMachineOpt = instanciator.getInstance(EspressoMachine.class);
		final IGrade espressoGrade = espressoMachineOpt.map(Coffee::getEspressoGrade)
				.orElse(Mark.zero("Could not initialize espresso machine."));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Espresso"), espressoGrade, 11d));

		return WeightingGrade.from(gradeBuilder.build());
	}

	private static IGrade getDripGrade(CoffeeMachine dripMachine) {
		final ImmutableSet.Builder<CriterionGradeWeight> dripGradeBuilder = ImmutableSet.builder();
		{
			boolean thrown = doesThrow(dripMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			dripGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("Energy state exception"), Mark.binary(thrown), 1d));
		}
		{
			final int nbStart = dripMachine.getNumberOfCoffeesProduced();
			dripMachine.produceCoffee(1d);
			final int nbOne = dripMachine.getNumberOfCoffeesProduced();
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Nb starts at zero"),
					Mark.binary(nbStart == 0 && nbOne == 1), 1d));
		}
		{
			final double maxStrength = dripMachine.getMaxStrength();
			dripGradeBuilder.add(
					CriterionGradeWeight.from(Criterion.given("Max strength"), Mark.binary(maxStrength == 10d), 1d));
		}
//		{
//			final int nbBefore = dripMachine.getNumberOfCoffeesProduced();
//			dripMachine.produceCoffee(0d);
//			final int nbAfter = dripMachine.getNumberOfCoffeesProduced();
//			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Nb includes zero strength"),
//					Mark.binary(nbAfter == nbBefore + 1), 1d));
//		}
		{
			final TryCatchAllVoid ran = TryCatchAllVoid.run(() -> dripMachine.produceCoffee(0.3d));

			final TryCatchAll<Double> energyNonZero = ran.andGet(() -> dripMachine.getEnergySpent());
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Energy non zero"),
					Mark.binary(ran.isSuccess() && energyNonZero.equals(TryCatchAll.success(83d))), 1d));

			dripMachine.produceCoffee(0d);
//			final TryCatchAll<Double> energyZero = TryCatchAll.get(dripMachine::getEnergySpent);
//			final Mark mark = energyNonZero
//					.and(energyZero, (n, z) -> Mark.binary(z == 0d && n != 0d, "", "Unexpected energy results"))
			final Mark mark = energyNonZero.andApply(
					n -> Mark.binary(dripMachine.getEnergySpent() == 0d && n != 0d, "", "Unexpected energy results"))
					.orMapCause(Coffee::failed);
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Energy zero"), mark, 1d));
		}
		{
			final TryCatchAll<Integer> timeNonZero = TryCatchAll.get(() -> dripMachine.getTimeForCoffee(0.8d));
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time non zero"),
					Mark.binary(timeNonZero.equals(TryCatchAll.success(120))), 1d));

			final int timeZero = dripMachine.getTimeForCoffee(0d);
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time zero"),
					timeNonZero
							.andApply(t -> (t != 0 ? Mark.binary(timeZero == 0) : Mark.zero("Got zero time for 0.8d")))
							.orMapCause(Coffee::failed),
					1d));

			boolean thrown = doesThrow(() -> dripMachine.getTimeForCoffee(10.2d),
					e -> e instanceof IllegalArgumentException);
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time throws"),
					Mark.binary(timeNonZero.isSuccess() && thrown), 1d));
		}

		return WeightingGrade.from(dripGradeBuilder.build());
	}

	private static IGrade getEspressoGrade(EspressoMachine espressoMachine) {
		final ImmutableSet.Builder<CriterionGradeWeight> espressoGradeBuilder = ImmutableSet.builder();
		{
			boolean thrown = doesThrow(espressoMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			espressoGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("Energy state exception"), Mark.binary(thrown), 4d));
		}
		{
			final int nbStart = espressoMachine.getNumberOfCoffeesProduced();
			final TryCatchAllVoid ran = TryCatchAllVoid.run(() -> espressoMachine.produceCoffee(1d));
			final int nbOne = espressoMachine.getNumberOfCoffeesProduced();
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Nb starts at zero"),
					Mark.binary(ran.isSuccess() && nbStart == 0 && nbOne == 1), 4d));
		}
		{
			final double maxStrength = espressoMachine.getMaxStrength();
			espressoGradeBuilder.add(
					CriterionGradeWeight.from(Criterion.given("Max strength"), Mark.binary(maxStrength == 20d), 3d));
		}
//		{
//			final int nbBefore = espressoMachine.getNumberOfCoffeesProduced();
//			final TryVoid ran = TryVoid.run(() -> espressoMachine.produceCoffee(0d));
//			final int nbAfter = espressoMachine.getNumberOfCoffeesProduced();
//			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Nb includes zero strength"),
//					Mark.binary(ran.isSuccess() && (nbAfter == nbBefore + 1)), 1d));
//		}
		{
			final TryCatchAllVoid ran = TryCatchAllVoid.run(() -> espressoMachine.produceCoffee(11d));
			final TryCatchAll<Double> energyNonZero = ran.andGet(() -> espressoMachine.getEnergySpent());
			final double expected = 2000d * 162d / 3600d + 15d;
			espressoGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("Energy non zero"),
							energyNonZero.map(n -> Mark.binary(DoubleMath.fuzzyEquals(n, expected, 0.1d), "",
									"For strength 11, expected 2000 watt × 162 sec / 3600 (sec/h) + 15 watt hours"),
									Coffee::failed),
							4d));

			final TryCatchAllVoid ranZero = TryCatchAllVoid.run(() -> espressoMachine.produceCoffee(0d));
			final TryCatchAll<Double> energyZero = ranZero.andGet(() -> espressoMachine.getEnergySpent());
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Energy zero"),
					energyZero.and(energyNonZero, (z, n) -> Mark.binary(z == 0d && n != 0d)).orMapCause(Coffee::failed),
					4d));
		}
		{
			final TryCatchAll<Integer> timeNonZero = TryCatchAll.get(() -> espressoMachine.getTimeForCoffee(19.6d));
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time non zero"),
					timeNonZero.map(n -> Mark.binary(Math.abs(n - 179.2d) < 1d), Coffee::failed), 3d));

			final int timeZero = espressoMachine.getTimeForCoffee(0d);
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time zero"),
					Mark.binary((timeZero == 0) && (timeNonZero.isSuccess() && timeNonZero.orMapCause(t -> {
						throw new VerifyException(t);
					}) != 0)), 4d));

			boolean thrown = doesThrow(() -> espressoMachine.getTimeForCoffee(-0.2d),
					e -> e instanceof IllegalArgumentException);
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Time throws"),
					Mark.binary(timeNonZero.isSuccess() && thrown), 4d));
		}
		{
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Power"),
					Mark.binary(espressoMachine.getPower() == 2000d), 3d));
		}

		return WeightingGrade.from(espressoGradeBuilder.build());
	}

	private static Mark failed(Throwable t) {
		return Mark.zero("Got exception: " + t);
	}

	@SafeVarargs
	private static <T> boolean doesThrow(Callable<T> callable, Predicate<Exception>... andSatisfies) {
		boolean satisfies;
		try {
			callable.call();
			satisfies = false;
		} catch (Exception exc) {
			satisfies = Arrays.stream(andSatisfies).allMatch(p -> p.test(exc));
		}
		return satisfies;
	}

}
