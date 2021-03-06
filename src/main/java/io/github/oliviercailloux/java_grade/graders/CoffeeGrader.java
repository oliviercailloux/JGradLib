package io.github.oliviercailloux.java_grade.graders;

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
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
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

/**
 * Provide a grader from an Instanciator.
 *
 * From repo: find pom. Find path. Get instanciator. Get grade.
 */
public class CoffeeGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AdminManagesUsers.class);
	public static final String PREFIX = "coffee";
	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-25T14:11:00+01:00[Europe/Paris]");

	CoffeeGrader() {
	}

	public static void main(String[] args) throws Exception {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
		// .setRepositoriesFilter(r->r.getUsername().equals(""));
		final GitGeneralGrader grader = GitGeneralGrader.using(fetcher,
				DeadlineGrader.usingInstantiatorGrader(CoffeeGrader::grade, DEADLINE)
						.setPenalizer(DeadlineGrader.LinearPenalizer.proportionalToLateness(Duration.ofSeconds(300))));
		grader.grade();
	}

	public static IGrade grade(Instanciator instanciator) {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
		final Optional<CoffeeMachine> dripMachineOpt = instanciator.getInstance(CoffeeMachine.class, "newInstance")
				.or(() -> instanciator.getInstance(CoffeeMachine.class));
		final IGrade dripGrade = dripMachineOpt.map(CoffeeGrader::getDripGrade)
				.orElse(Mark.zero("Could not initialize drip machine."));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Drip"), dripGrade, 7d));

		final Optional<EspressoMachine> espressoMachineOpt = instanciator
				.getInstance(EspressoMachine.class, "newInstance")
				.or(() -> instanciator.getInstance(EspressoMachine.class));
		final IGrade espressoGrade = espressoMachineOpt.map(CoffeeGrader::getEspressoGrade)
				.orElse(Mark.zero("Could not initialize espresso machine."));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Espresso"), espressoGrade, 8d));

		return WeightingGrade.from(gradeBuilder.build());
	}

	private static IGrade getDripGrade(CoffeeMachine dripMachine) {
		final ImmutableSet.Builder<CriterionGradeWeight> dripGradeBuilder = ImmutableSet.builder();
		{
			boolean thrown = doesThrow(dripMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			dripGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("ENERGY_STATE_EXCEPTION"), Mark.binary(thrown), 1d));
		}
		{
			final int nbStart = dripMachine.getNumberOfCoffeesProduced();
			dripMachine.produceCoffee(1d);
			final int nbOne = dripMachine.getNumberOfCoffeesProduced();
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("NB_STARTS_AT_ZERO"),
					Mark.binary(nbStart == 0 && nbOne == 1), 1d));
		}
		{
			final double maxStrength = dripMachine.getMaxStrength();
			dripGradeBuilder.add(
					CriterionGradeWeight.from(Criterion.given("MAX_STRENGTH"), Mark.binary(maxStrength == 10d), 1d));
		}
		{
			final int nbBefore = dripMachine.getNumberOfCoffeesProduced();
			dripMachine.produceCoffee(0d);
			final int nbAfter = dripMachine.getNumberOfCoffeesProduced();
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("NB_INCLUDES_ZERO_STRENGTH"),
					Mark.binary(nbAfter == nbBefore + 1), 1d));
		}
		{
			dripMachine.produceCoffee(0.3d);
			final double energyNonZero = dripMachine.getEnergySpent();
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("ENERGY_NON_ZERO"),
					Mark.binary(energyNonZero == 83d), 1d));

			dripMachine.produceCoffee(0d);
			final double energyZero = dripMachine.getEnergySpent();
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("ENERGY_ZERO"),
					Mark.binary((energyZero == 0d) && (energyNonZero != 0d)), 1d));
		}
		{
			final int timeNonZero = dripMachine.getTimeForCoffee(0.8d);
			dripGradeBuilder.add(
					CriterionGradeWeight.from(Criterion.given("TIME_NON_ZERO"), Mark.binary(timeNonZero == 120), 1d));

			final int timeZero = dripMachine.getTimeForCoffee(0d);
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("TIME_ZERO"),
					Mark.binary((timeZero == 0) && (timeNonZero != 0)), 1d));
		}
		{
			boolean thrown = doesThrow(() -> dripMachine.getTimeForCoffee(10.2d),
					e -> e instanceof IllegalArgumentException);
			dripGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("TIME_THROWS"), Mark.binary(thrown), 1d));
		}

		return WeightingGrade.from(dripGradeBuilder.build());
	}

	private static IGrade getEspressoGrade(EspressoMachine espressoMachine) {
		final ImmutableSet.Builder<CriterionGradeWeight> espressoGradeBuilder = ImmutableSet.builder();
		{
			boolean thrown = doesThrow(espressoMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			espressoGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("ENERGY_STATE_EXCEPTION"), Mark.binary(thrown), 1d));
		}
		{
			final int nbStart = espressoMachine.getNumberOfCoffeesProduced();
			espressoMachine.produceCoffee(1d);
			final int nbOne = espressoMachine.getNumberOfCoffeesProduced();
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("NB_STARTS_AT_ZERO"),
					Mark.binary(nbStart == 0 && nbOne == 1), 1d));
		}
		{
			final double maxStrength = espressoMachine.getMaxStrength();
			espressoGradeBuilder.add(
					CriterionGradeWeight.from(Criterion.given("MAX_STRENGTH"), Mark.binary(maxStrength == 20d), 1d));
		}
		{
			final int nbBefore = espressoMachine.getNumberOfCoffeesProduced();
			espressoMachine.produceCoffee(0d);
			final int nbAfter = espressoMachine.getNumberOfCoffeesProduced();
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("NB_INCLUDES_ZERO_STRENGTH"),
					Mark.binary(nbAfter == nbBefore + 1), 1d));
		}
		{
			espressoMachine.produceCoffee(11d);
			final double energyNonZero = espressoMachine.getEnergySpent();
			final double expected = 2000d * 162d / 3600d + 15d;
			espressoGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("ENERGY_NON_ZERO"),
							Mark.binary(DoubleMath.fuzzyEquals(energyNonZero, expected, 0.1d), "",
									"For strength 11, expected 2000 watt × 162 sec / 3600 (sec/h) + 15 watt hours"),
							1d));

			espressoMachine.produceCoffee(0d);
			final double energyZero = espressoMachine.getEnergySpent();
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("ENERGY_ZERO"),
					Mark.binary((energyZero == 0d) && (energyNonZero != 0d)), 1d));
		}
		{
			final int timeNonZero = espressoMachine.getTimeForCoffee(19.6d);
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("TIME_NON_ZERO"),
					Mark.binary((Math.abs(timeNonZero - 179.2d) < 1d)), 1d));

			final int timeZero = espressoMachine.getTimeForCoffee(0d);
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("TIME_ZERO"),
					Mark.binary((timeZero == 0) && (timeNonZero != 0)), 1d));
		}
		{
			boolean thrown = doesThrow(() -> espressoMachine.getTimeForCoffee(-0.2d),
					e -> e instanceof IllegalArgumentException);
			espressoGradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("TIME_THROWS"), Mark.binary(thrown), 1d));
		}
		{
			espressoGradeBuilder.add(CriterionGradeWeight.from(Criterion.given("POWER"),
					Mark.binary(espressoMachine.getPower() == 2000d), 1d));
		}

		return WeightingGrade.from(espressoGradeBuilder.build());
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
