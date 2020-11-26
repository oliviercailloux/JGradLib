package io.github.oliviercailloux.java_grade.ex;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.math.DoubleMath;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.samples.coffee.CoffeeMachine;
import io.github.oliviercailloux.samples.coffee.EspressoMachine;
import io.github.oliviercailloux.utils.Utils;

public class CoffeeGrader {
	public static enum CoffeeCriterion implements Criterion {
		DRIP, FACTORY, ENERGY_STATE_EXCEPTION, NB_STARTS_AT_ZERO, MAX_STRENGTH, NB_INCLUDES_ZERO_STRENGTH, ENERGY_ZERO,
		ENERGY_NON_ZERO, TIME_ZERO, TIME_NON_ZERO, TIME_THROWS, ESPRESSO, POWER;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CoffeeGrader.class);

	private static final String PREFIX = "coffee";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-04-06T00:00:00+02:00").toInstant();
	String branchPrefix;

	/**
	 * NB for this to work, we need to have the interfaces in the class path with
	 * the same name as the one used by the student’s projects, namely,
	 * …samples.coffee.
	 *
	 */
	public static void main(String[] args) throws Exception {
		final Path outDir = Paths.get("../../Java L3/");
		final Path projectsBaseDir = outDir.resolve(PREFIX);

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final CoffeeGrader grader = new CoffeeGrader();
		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final Path projectDir = projectsBaseDir.resolve(repository.getRepositoryName());
			gradesB.put(repository.getUsername(), grader.grade(repository, projectDir));
			Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir);
		}
	}

	CoffeeGrader() {
		branchPrefix = "origin";
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord, Path projectDir) throws IOException {
		new GitCloner().download(GitUri.fromUri(coord.asURI()), projectDir);

		try (GitFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		fs.getHistory();
		final GitPath gitSourcePath = fs.getRelativePath();
		if (!gitSourcePath.getRoot().exists()) {
			return Mark.zero("Found no master commit.");
		}
		final ObjectId masterId = gitSourcePath.getRoot().getCommit();
		final Instant commitDate = gitHubHistory.getCommitDate(masterId);
		if (commitDate.isAfter(DEADLINE)) {
			LOGGER.warn("Should diminish the grade!");
		} else {
			LOGGER.info("Submitted at {}, before {}.", commitDate, DEADLINE);
		}

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		{
			final RevCommit master = fs.getCachedHistory().getCommit(masterId);
			gradeBuilder.put(JavaCriterion.ID, Mark.binary(JavaMarkHelper.committerAndAuthorIs(master, owner)));
		}
		{
			gradeBuilder.put(JavaCriterion.NO_DERIVED_FILES, Marks.noDerivedFilesGrade(gitSourcePath));
		}

		final Path fileSourcePath = Path.of("src-" + owner);
		Utils.copyRecursively(gitSourcePath, fileSourcePath, StandardCopyOption.REPLACE_EXISTING);

		final MavenManager mavenManager = new MavenManager();
		final boolean compiled = mavenManager.compile(fileSourcePath);
		gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled));
		checkArgument(compiled);

		final ImmutableList<Path> classPath = mavenManager.getClassPath(fileSourcePath);
		checkArgument(classPath.size() >= 2);
		final Path java = fileSourcePath.resolve("src/main/java/");
		final ImmutableList<Path> depsAndItself = ImmutableList.<Path>builder().add(java).addAll(classPath).build();

		final CompilationResult compilationDrip = Compiler.eclipseCompile(depsAndItself,
				ImmutableSet.of(java.resolve("io/github/oliviercailloux/samples/coffee/DripCoffeeMaker.java")));
		final CompilationResult compilationEspresso = Compiler.eclipseCompile(depsAndItself,
				ImmutableSet.of(java.resolve("io/github/oliviercailloux/samples/coffee/MyEspressoMachine.java")));
		verify(compilationDrip.compiled, compilationDrip.err);
		verify(compilationEspresso.compiled, compilationEspresso.err);
		gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.binary(
				(compilationDrip.countWarnings() == 0) && (compilationEspresso.countWarnings() == 0), "",
				compilationDrip.err.replaceAll(fileSourcePath.toAbsolutePath().toString() + "/", "") + "\n"
						+ compilationEspresso.err.replaceAll(fileSourcePath.toAbsolutePath().toString() + "/", "")));

		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { fileSourcePath.resolve(Path.of("target/classes/")).toUri().toURL() },
				getClass().getClassLoader())) {
			final Instanciator instanciator = Instanciator.given(loader);

			final Optional<CoffeeMachine> dripMachineOpt = instanciator.getInstance(CoffeeMachine.class, "newInstance")
					.or(() -> instanciator.getInstance(CoffeeMachine.class));
			final IGrade dripGrade = dripMachineOpt.map(this::getDripGrade)
					.orElse(Mark.zero("Could not initialize drip machine."));
			gradeBuilder.put(CoffeeCriterion.DRIP, dripGrade);

			final Optional<EspressoMachine> espressoMachineOpt = instanciator
					.getInstance(EspressoMachine.class, "newInstance")
					.or(() -> instanciator.getInstance(EspressoMachine.class));
			final IGrade espressoGrade = espressoMachineOpt.map(this::getEspressoGrade)
					.orElse(Mark.zero("Could not initialize espresso machine."));
			gradeBuilder.put(CoffeeCriterion.ESPRESSO, espressoGrade);
		}

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(JavaCriterion.ID, 0.5d);
		builder.put(JavaCriterion.NO_DERIVED_FILES, 0.5d);
		builder.put(JavaCriterion.COMPILE, 1d);
		builder.put(JavaCriterion.NO_WARNINGS, 1d);
		builder.put(CoffeeCriterion.DRIP, 8d);
		builder.put(CoffeeCriterion.ESPRESSO, 9d);
		return WeightingGrade.from(subGrades, builder.build());
	}

	private IGrade getDripGrade(CoffeeMachine dripMachine) {
		final ImmutableMap.Builder<Criterion, IGrade> dripGradeBuilder = ImmutableMap.builder();
		{
			dripGradeBuilder.put(CoffeeCriterion.FACTORY,
					Mark.binary(hasPrivateParameterlessConstructor(dripMachine.getClass())));
		}
		{
			boolean thrown = doesThrow(dripMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			dripGradeBuilder.put(CoffeeCriterion.ENERGY_STATE_EXCEPTION, Mark.binary(thrown));
		}
		{
			final int nbStart = dripMachine.getNumberOfCoffeesProduced();
			dripMachine.produceCoffee(1d);
			final int nbOne = dripMachine.getNumberOfCoffeesProduced();
			dripGradeBuilder.put(CoffeeCriterion.NB_STARTS_AT_ZERO, Mark.binary(nbStart == 0 && nbOne == 1));
		}
		{
			final double maxStrength = dripMachine.getMaxStrength();
			dripGradeBuilder.put(CoffeeCriterion.MAX_STRENGTH, Mark.binary(maxStrength == 10d));
		}
		{
			final int nbBefore = dripMachine.getNumberOfCoffeesProduced();
			dripMachine.produceCoffee(0d);
			final int nbAfter = dripMachine.getNumberOfCoffeesProduced();
			dripGradeBuilder.put(CoffeeCriterion.NB_INCLUDES_ZERO_STRENGTH, Mark.binary(nbAfter == nbBefore + 1));
		}
		{
			dripMachine.produceCoffee(0.3d);
			final double energyNonZero = dripMachine.getEnergySpent();
			dripGradeBuilder.put(CoffeeCriterion.ENERGY_NON_ZERO, Mark.binary(energyNonZero == 83d));

			dripMachine.produceCoffee(0d);
			final double energyZero = dripMachine.getEnergySpent();
			dripGradeBuilder.put(CoffeeCriterion.ENERGY_ZERO, Mark.binary((energyZero == 0d) && (energyNonZero != 0d)));
		}
		{
			final int timeNonZero = dripMachine.getTimeForCoffee(0.8d);
			dripGradeBuilder.put(CoffeeCriterion.TIME_NON_ZERO, Mark.binary(timeNonZero == 120));

			final int timeZero = dripMachine.getTimeForCoffee(0d);
			dripGradeBuilder.put(CoffeeCriterion.TIME_ZERO, Mark.binary((timeZero == 0) && (timeNonZero != 0)));
		}
		{
			boolean thrown = doesThrow(() -> dripMachine.getTimeForCoffee(10.2d),
					e -> e instanceof IllegalArgumentException);
			dripGradeBuilder.put(CoffeeCriterion.TIME_THROWS, Mark.binary(thrown));
		}

		final ImmutableMap<Criterion, Double> weights = Maps.toMap(ImmutableSet.<Criterion>builder()
				.add(CoffeeCriterion.FACTORY, CoffeeCriterion.ENERGY_STATE_EXCEPTION, CoffeeCriterion.NB_STARTS_AT_ZERO,
						CoffeeCriterion.MAX_STRENGTH, CoffeeCriterion.NB_INCLUDES_ZERO_STRENGTH,
						CoffeeCriterion.ENERGY_ZERO, CoffeeCriterion.ENERGY_NON_ZERO, CoffeeCriterion.TIME_ZERO,
						CoffeeCriterion.TIME_NON_ZERO, CoffeeCriterion.TIME_THROWS)
				.build(), c -> 1d);

		return WeightingGrade.from(dripGradeBuilder.build(), weights);
	}

	private IGrade getEspressoGrade(EspressoMachine espressoMachine) {
		final ImmutableMap.Builder<Criterion, IGrade> espressoGradeBuilder = ImmutableMap.builder();
		{
			espressoGradeBuilder.put(CoffeeCriterion.FACTORY,
					Mark.binary(hasPrivateParameterlessConstructor(espressoMachine.getClass())));
		}
		{
			boolean thrown = doesThrow(espressoMachine::getEnergySpent, e -> e instanceof IllegalStateException);
			espressoGradeBuilder.put(CoffeeCriterion.ENERGY_STATE_EXCEPTION, Mark.binary(thrown));
		}
		{
			final int nbStart = espressoMachine.getNumberOfCoffeesProduced();
			espressoMachine.produceCoffee(1d);
			final int nbOne = espressoMachine.getNumberOfCoffeesProduced();
			espressoGradeBuilder.put(CoffeeCriterion.NB_STARTS_AT_ZERO, Mark.binary(nbStart == 0 && nbOne == 1));
		}
		{
			final double maxStrength = espressoMachine.getMaxStrength();
			espressoGradeBuilder.put(CoffeeCriterion.MAX_STRENGTH, Mark.binary(maxStrength == 20d));
		}
		{
			final int nbBefore = espressoMachine.getNumberOfCoffeesProduced();
			espressoMachine.produceCoffee(0d);
			final int nbAfter = espressoMachine.getNumberOfCoffeesProduced();
			espressoGradeBuilder.put(CoffeeCriterion.NB_INCLUDES_ZERO_STRENGTH, Mark.binary(nbAfter == nbBefore + 1));
		}
		{
			espressoMachine.produceCoffee(11d);
			final double energyNonZero = espressoMachine.getEnergySpent();
			final double expected = 2000d * 162d / 3600d + 15d;
			espressoGradeBuilder.put(CoffeeCriterion.ENERGY_NON_ZERO,
					Mark.binary(DoubleMath.fuzzyEquals(energyNonZero, expected, 0.1d), "",
							"For strength 11, expected 2000 watt × 162 sec / 3600 (sec/h) + 15 watt hours"));

			espressoMachine.produceCoffee(0d);
			final double energyZero = espressoMachine.getEnergySpent();
			espressoGradeBuilder.put(CoffeeCriterion.ENERGY_ZERO,
					Mark.binary((energyZero == 0d) && (energyNonZero != 0d)));
		}
		{
			final int timeNonZero = espressoMachine.getTimeForCoffee(19.6d);
			espressoGradeBuilder.put(CoffeeCriterion.TIME_NON_ZERO, Mark.binary((Math.abs(timeNonZero - 179.2d) < 1d)));

			final int timeZero = espressoMachine.getTimeForCoffee(0d);
			espressoGradeBuilder.put(CoffeeCriterion.TIME_ZERO, Mark.binary((timeZero == 0) && (timeNonZero != 0)));
		}
		{
			boolean thrown = doesThrow(() -> espressoMachine.getTimeForCoffee(-0.2d),
					e -> e instanceof IllegalArgumentException);
			espressoGradeBuilder.put(CoffeeCriterion.TIME_THROWS, Mark.binary(thrown));
		}
		{
			espressoGradeBuilder.put(CoffeeCriterion.POWER, Mark.binary(espressoMachine.getPower() == 2000d));
		}

		final ImmutableMap<Criterion, Double> weightsEsp = Maps.toMap(ImmutableSet.<Criterion>builder()
				.add(CoffeeCriterion.FACTORY, CoffeeCriterion.ENERGY_STATE_EXCEPTION, CoffeeCriterion.NB_STARTS_AT_ZERO,
						CoffeeCriterion.MAX_STRENGTH, CoffeeCriterion.NB_INCLUDES_ZERO_STRENGTH,
						CoffeeCriterion.ENERGY_ZERO, CoffeeCriterion.ENERGY_NON_ZERO, CoffeeCriterion.TIME_ZERO,
						CoffeeCriterion.TIME_NON_ZERO, CoffeeCriterion.TIME_THROWS, CoffeeCriterion.POWER)
				.build(), c -> 1d);

		return WeightingGrade.from(espressoGradeBuilder.build(), weightsEsp);
	}

	private boolean hasPrivateParameterlessConstructor(Class<?> clazz) {
		final ImmutableSet<Constructor<?>> constructors = ImmutableSet.copyOf(clazz.getDeclaredConstructors());
		final Optional<Constructor<?>> parameterlessConstructor = constructors.stream()
				.filter(c -> c.getParameters().length == 0).collect(MoreCollectors.toOptional());
		final Optional<Integer> modifiers = parameterlessConstructor.map(Constructor::getModifiers);
		final boolean privateConstructor = modifiers.map(Modifier::isPrivate).orElse(false);
		return privateConstructor;
	}

	@SafeVarargs
	private <T> boolean doesThrow(Callable<T> callable, Predicate<Exception>... andSatisfies) {
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
