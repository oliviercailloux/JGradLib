package io.github.oliviercailloux.java_grade.ex_objects;

import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.JAR_REQUIRED;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.P43;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.P47;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.P53JAR;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.P53UTILS;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.PREFIX;
import static io.github.oliviercailloux.java_grade.ex_objects.ExObjectsCriterion.REPO_EXISTS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FilesSourceUtils;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;

public class ExObjectsGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExObjectsGrader.class);
	private static final Instant DEADLINE = ZonedDateTime.parse("2019-04-03T23:59:59+01:00").toInstant();

	public static void main(String[] args) throws Exception {
		final String prefix = "objects";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java L3/");
		orch.readUsernames(srcDir.resolve("usernamesGH-manual.json"));

		orch.readRepositories();
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExObjectsGrader grader = new ExObjectsGrader();

		final ImmutableSet<GradeWithStudentAndCriterion> grades = repositories.entrySet().stream()
				.map((e) -> GradeWithStudentAndCriterion.of(e.getKey(), grader.grade(e.getValue()))).collect(ImmutableSet.toImmutableSet());

		Files.writeString(srcDir.resolve("all grades " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(srcDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExObjectsGrader() {
		timeMark = null;
	}

	private CriterionAndMark timeMark;

	public ImmutableSet<GradeWithStudentAndCriterion> grade(RepositoryCoordinates coord) {
		final AnonymousGrade usingLastCommit = grade(coord, Instant.MAX);
		final ImmutableSet<GradeWithStudentAndCriterion> realMarks;
		if (timeMark.getPoints() < 0d) {
			final AnonymousGrade usingCommitOnTime = grade(coord, DEADLINE);
			final double lastCommitPoints = usingLastCommit.getPoints();
			final double onTimePoints = usingCommitOnTime.getPoints();
			if (onTimePoints > lastCommitPoints) {
				final GradeWithStudentAndCriterion originalMark = usingCommitOnTime.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(), originalMark.getComment()
						+ " (Using commit on time rather than last commit because it brings more points.)");
				realMarks = usingCommitOnTime.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			} else {
				final GradeWithStudentAndCriterion originalMark = usingLastCommit.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(), originalMark.getComment()
						+ " (Using last commit rather than commit on time because it brings at least as much points.)");
				realMarks = usingLastCommit.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			}
		} else {
			realMarks = usingLastCommit.getMarks().values();
		}
		return realMarks;
	}

	public AnonymousGrade grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableSet.Builder<CriterionAndMark> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/objects");

		final GitFullContext fullContext = FullContextInitializer.withPathAndIgnore(coord, projectsBaseDir,
				ignoreAfter);
		final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
		final Path projectPath = fullContext.getClient().getProjectDirectory();
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final FilesSource filesReader = fullContext.getFilesReader(fullContext.getMainCommit());

		timeMark = Marks.timeMark(ON_TIME, fullContext, DEADLINE, this::getPenalty);
		gradeBuilder.add(timeMark);
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final Predicate<Path> project43src = (p) -> p.startsWith(Paths.get("project43", "src"));
		final Predicate<Path> projet43src = (p) -> p.startsWith(Paths.get("projet43", "src"));
		final FilesSource p43sources = filesReader.filterOnPath(project43src.or(projet43src));
		gradeBuilder.add(CriterionAndMark.binary(P43, !p43sources.asFileContents().isEmpty()));
		final Predicate<Path> project47src = (p) -> p.startsWith(Paths.get("project47", "src"));
		final Predicate<Path> projet47src = (p) -> p.startsWith(Paths.get("projet47", "src"));
		final FilesSource p47sources = filesReader.filterOnPath(project47src.or(projet47src));
		gradeBuilder.add(CriterionAndMark.binary(P47, !p47sources.asFileContents().isEmpty()));
		final Predicate<Path> project53srcPairOfDice = (p) -> p.startsWith(Paths.get("project53utils", "src"))
				&& p.endsWith("PairOfDice.java");
		final Predicate<Path> projet53srcPairOfDice = (p) -> p.startsWith(Paths.get("projet53utils", "src"))
				&& p.endsWith("PairOfDice.java");
		final Predicate<Path> project53srcStatCalc = (p) -> p.startsWith(Paths.get("project53utils", "src"))
				&& p.endsWith("StatCalc.java");
		final Predicate<Path> projet53srcStatCalc = (p) -> p.startsWith(Paths.get("projet53utils", "src"))
				&& p.endsWith("StatCalc.java");
		final FilesSource p53PairOfDices = filesReader.filterOnPath(project53srcPairOfDice.or(projet53srcPairOfDice));
		final FilesSource p53StatCalcs = filesReader.filterOnPath(project53srcStatCalc.or(projet53srcStatCalc));
		gradeBuilder.add(CriterionAndMark.proportional(P53UTILS, !p53PairOfDices.asFileContents().isEmpty(),
				!p53StatCalcs.asFileContents().isEmpty()));

		final FilesSource p53jars = filesReader.filterOnPath(Predicates.equalTo(Paths.get("project53utils/utils.jar"))
				.or(Predicates.equalTo(Paths.get("projet53utils/utils.jar"))));
		final Path p53jarPath = FilesSourceUtils.getSinglePath(p53jars);
		final CriterionAndMark jarMark;
		if (p53jarPath.getNameCount() == 0 || p53jarPath.equals(Paths.get(""))) {
			jarMark = CriterionAndMark.min(P53JAR, "utils.jar not found");
		} else {
			try (JarFile jar = new JarFile(projectPath.resolve(p53jarPath).toFile())) {
				final ImmutableSet<JarEntry> entries = ImmutableSet.copyOf(jar.entries().asIterator());
				final ImmutableList<JarEntry> pairOfDiceEntries = entries.stream()
						.filter((e) -> e.getName().endsWith("PairOfDice.class"))
						.collect(ImmutableList.toImmutableList());
				final ImmutableList<JarEntry> statCalcEntries = entries.stream()
						.filter((e) -> e.getName().endsWith("StatCalc.class")).collect(ImmutableList.toImmutableList());
				final boolean jarContainsPairOfDice = pairOfDiceEntries.size() == 1;
				final boolean jarContainsStatCalc = statCalcEntries.size() == 1;
//			try (InputStream input = jar.getInputStream(pairOfDiceEntries.get(0))) {
//				final byte[] pairOfDice = input.readAllBytes();
//			}
				jarMark = CriterionAndMark.proportional(P53JAR, jarContainsPairOfDice, jarContainsStatCalc);
			} catch (IOException e) {
				LOGGER.warn("p53jarPath: {} ({} components).", p53jarPath, p53jarPath.getNameCount());
				throw new IllegalStateException(e);
			}
		}
		gradeBuilder.add(jarMark);

		final Path project53MainSrcPath = Paths.get("project53main", "src");
		final Path projet53MainSrcPath = Paths.get("projet53main", "src");
		final Predicate<Path> project53MainSrc = (p) -> p.startsWith(project53MainSrcPath);
		final Predicate<Path> projet53MainSrc = (p) -> p.startsWith(projet53MainSrcPath);
		final boolean projet53 = !filesReader.filterOnPath(projet53MainSrc).asFileContents().isEmpty();
		final boolean project53 = !filesReader.filterOnPath(project53MainSrc).asFileContents().isEmpty();
		final Path p53MainSrcPath;
		if (!projet53 && project53) {
			p53MainSrcPath = project53MainSrcPath;
		} else if (projet53 && !project53) {
			p53MainSrcPath = projet53MainSrcPath;
		} else if (!projet53 && !project53) {
			p53MainSrcPath = project53MainSrcPath;
		} else {
			throw new UnsupportedOperationException("MainSrcPath is incorrect then");
		}
		final FilesSource p53MainSources = filesReader.filterOnPath(project53MainSrc.or(projet53MainSrc))
				.filterOnPath((p) -> !p.endsWith(".DS_Store"));
		gradeBuilder.add(prefixMark(p53MainSrcPath, p53MainSources));

		final ImmutableList<? extends JavaFileObject> srcToCompile = p53MainSources
				.filterOnPath((p) -> p.getName(p.getNameCount() - 1).toString().endsWith(".java")).asFileContents()
				.stream().map((fc) -> SimpleCompiler.asJavaSource(p53MainSrcPath, fc))
				.collect(ImmutableList.toImmutableList());
		final CriterionAndMark jarRequiredMark;
		if (jarMark.getPoints() != P53JAR.getMaxPoints() || srcToCompile.isEmpty()) {
			jarRequiredMark = CriterionAndMark.min(JAR_REQUIRED);
		} else {
//				final Path cp = projectPath.resolve(p53MainSrcPath);
			final Path cp = projectPath.resolve(p53jarPath);
			final List<Diagnostic<? extends JavaFileObject>> diagnosticsNaked = SimpleCompiler.compile(srcToCompile,
					ImmutableList.of());
			final List<Diagnostic<? extends JavaFileObject>> diagnosticsWithJar = SimpleCompiler.compile(srcToCompile,
					ImmutableList.of(cp));
			if (!diagnosticsNaked.isEmpty() && diagnosticsWithJar.isEmpty()) {
				jarRequiredMark = CriterionAndMark.max(JAR_REQUIRED);
			} else {
				jarRequiredMark = CriterionAndMark.min(JAR_REQUIRED,
						"No jar: " + diagnosticsNaked.toString() + " — With jar: " + diagnosticsWithJar.toString());
			}
		}
		gradeBuilder.add(jarRequiredMark);

		final ImmutableSet<CriterionAndMark> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExObjectsCriterion.values()),
				grade.stream().map(CriterionAndMark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.isEmpty() : diff;
		return GradeWithStudentAndCriterion.anonymous(grade);
	}

	private CriterionAndMark prefixMark(Path p53MainSrcPath, FilesSource p53MainSources) {
		if (p53MainSources.asFileContents().isEmpty()) {
			return CriterionAndMark.min(PREFIX);
		}
		final ImmutableList<Path> prefixes = p53MainSources.getContents().keySet().stream()
				.map((p) -> p53MainSrcPath.relativize(p)).map(ExObjectsGrader::prefixThree)
				.collect(ImmutableList.toImmutableList());
		assert prefixes.size() >= 1;
		final CriterionAndMark mark;
		switch (prefixes.size()) {
		case 1:
			final Path prefix = prefixes.stream().collect(MoreCollectors.onlyElement());
			if (prefix.getNameCount() >= 3) {
				mark = CriterionAndMark.max(PREFIX);
			} else {
				mark = CriterionAndMark.min(PREFIX, String.format("Common prefix is too short: '%s'.", prefix));
			}
			break;
		default:
			mark = CriterionAndMark.min(PREFIX, String.format("Found multiple prefixes: %s.", prefixes));
			break;
		}
		return mark;
	}

	private static Path prefixThree(Path p) {
		if (p.getNameCount() == 0) {
			return p;
		}
		if (p.getNameCount() == 1) {
			return Paths.get("");
		}
		return p.subpath(0, Math.min(3, p.getNameCount() - 1));
	}

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExObjectsCriterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));

		final long hoursLate = tardiness.toHours() + 1;
		return -3d / 20d * maxGrade * hoursLate;
	}
}
