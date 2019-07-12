package io.github.oliviercailloux.java_grade.ex_two_sets;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.CONCATENATES;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.EX_57;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.JAVADOC;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.REPO_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.THROWS;
import static io.github.oliviercailloux.java_grade.ex_two_sets.ExTwoSetsCriterion.TYPE_SET;
import static java.util.Objects.requireNonNull;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;

public class ExTwoSetsGrader {

	private Instant deadline;

	public ImmutableSet<GradeWithStudentAndCriterion> grade(RepositoryCoordinates coord) {
		final AnonymousGrade usingLastCommit = grade(coord, Instant.MAX);
		final Optional<ObjectId> lastCommit = mainCommit.map(RevCommit::copy);
		final ImmutableSet<GradeWithStudentAndCriterion> realMarks;
		if (timeMark.getPoints() < 0d) {
			final AnonymousGrade usingCommitOnTime = grade(coord, deadline);
			final Optional<ObjectId> commitOnTime = mainCommit.map(RevCommit::copy);
			final double lastCommitPoints = usingLastCommit.getPoints();
			final double onTimePoints = usingCommitOnTime.getPoints();
			if (onTimePoints > lastCommitPoints && onTimePoints > 0d) {
				final GradeWithStudentAndCriterion originalMark = usingCommitOnTime.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(),
						originalMark.getComment() + String.format(
								" (Using commit '%s' on time rather than last commit '%s' because it brings more points.)",
								toString(commitOnTime), toString(lastCommit)));
				realMarks = usingCommitOnTime.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			} else {
				final GradeWithStudentAndCriterion originalMark = usingLastCommit.getMarks().get(ON_TIME);
				final CriterionAndMark commentedMark = CriterionAndMark.of(ON_TIME, originalMark.getPoints(),
						originalMark.getComment() + String.format(
								" (Using last commit '%s' rather than commit '%s' on time because it brings at least as much points.)",
								toString(lastCommit), toString(commitOnTime)));
				realMarks = usingLastCommit.getMarks().values().stream()
						.map((m) -> m.getCriterion() != ON_TIME ? m : commentedMark)
						.collect(ImmutableSet.toImmutableSet());
			}
		} else {
			realMarks = usingLastCommit.getMarks().values();
		}
		return realMarks;
	}

	private String toString(Optional<ObjectId> optCommit) {
		final Optional<String> optId = optCommit.map(ObjectId::getName);
		return optId.isPresent() ? optId.get() : optId.toString();
	}

	public AnonymousGrade grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableSet.Builder<CriterionAndMark> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/interfaces");

		final GitFullContext fullContext = FullContextInitializer.withPathAndIgnore(coord, projectsBaseDir,
				ignoreAfter);
		mainCommit = fullContext.getMainCommit();
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final FilesSource filesReader = fullContext.getFilesReader(fullContext.getMainCommit());

		timeMark = Marks.timeMark(ON_TIME, fullContext, deadline, this::getPenalty);
		gradeBuilder.add(timeMark);
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final Predicate<Path> intQ = (p) -> p.endsWith("IntQuestion.java");
		final Predicate<Path> ee57 = (p) -> p.endsWith("EE57.java");
		final FilesSource ee57src = filesReader.filterOnPath(intQ.or(ee57));
		final FilesSource intQSrc = filesReader.filterOnContent(Predicates.containsPattern("IntQuestion "));
		final boolean foundEe57Src = ee57src.asFileContents().size() >= 1;
		final boolean foundQSrc = intQSrc.asFileContents().size() >= 1;
		if (!foundEe57Src && foundQSrc) {
			LOGGER.warn("Only embedded!");
		}
		gradeBuilder.add(CriterionAndMark.binary(EX_57, foundEe57Src || foundQSrc));

		final FilesSource e1src = filesReader.filterOnPath((p) -> p.endsWith("E1.java"));
		checkArgument(e1src.asFileContents().size() <= 1);
		final FileContent e1fc = e1src.asFileContents().size() == 1 ? e1src.asFileContents().iterator().next()
				: FileContent.of(Paths.get("E1.java"), "");
		final Path pkgPath = JavaGradeUtils.getPackage(e1fc);
		final Path e1Path = Optional.ofNullable(e1fc.getPath().getParent()).orElse(Path.of(""));
		final Path sourceFolder = JavaGradeUtils.substract(e1Path, pkgPath);

		final FilesSource srcFiles = filesReader.filterOnPath((p) -> p.startsWith(sourceFolder));
		final List<Diagnostic<? extends JavaFileObject>> compileDiagnostics = SimpleCompiler.compile(sourceFolder,
				srcFiles, ImmutableList.of());

		final boolean compiles = compileDiagnostics.isEmpty();

		final FilesSource srcSet = filesReader.filterOnContent(Predicates.containsPattern("[\t ]Set<Integer> "));
		final String typeSetCmt = compiles ? "" : "Does not compile";
		gradeBuilder.add(CriterionAndMark.of(TYPE_SET,
				compiles && srcSet.asFileContents().size() >= 1 ? TYPE_SET.getMaxPoints() : TYPE_SET.getMinPoints(),
				typeSetCmt));

		gradeBuilder.add(CriterionAndMark.binary(THROWS, compiles && filesReader.anyMatch(Predicates.containsPattern("[\t ]throw ")
				.and(Predicates.containsPattern("(IllegalArgumentException)|(Error)|(NullPointerException)")))));

		/**
		 * TODO count the number of words, or similar. Shouldn’t get points for
		 * just @author!
		 */
		final boolean noAutoGen = filesReader.existsAndAllMatch(
				Predicates.containsPattern("Auto-generated").negate().and(Predicates.containsPattern("TODO").negate()));
//		LOGGER.info("Auto-gen: {}.",
//				filesReader.filterOnContent(Predicates.containsPattern("Auto-generated")).asFileContents());
//		LOGGER.info("TO DO: {}.", filesReader.filterOnContent(Predicates.containsPattern("TO DO")).asFileContents());
//		LOGGER.info("No auto gen: {}.", noAutoGen);
		gradeBuilder.add(CriterionAndMark.binary(JAVADOC,
				compiles && noAutoGen && filesReader.anyMatch(Predicates.containsPattern("/\\*\\*"))));

		gradeBuilder.add(
				CriterionAndMark.binary(CONCATENATES, compiles && filesReader.anyMatch(Predicates.containsPattern("\\.addAll"))));

		final ImmutableSet<CriterionAndMark> grade = gradeBuilder.build();
		final Set<CriterionAndPoints> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExTwoSetsCriterion.values()),
				grade.stream().map(CriterionAndMark::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.isEmpty() : diff;
		return GradeWithStudentAndCriterion.anonymous(grade);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExTwoSetsGrader.class);
	private CriterionAndMark timeMark;
	private Optional<RevCommit> mainCommit;

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExTwoSetsCriterion.values())
				.collect(Collectors.summingDouble(CriterionAndPoints::getMaxPoints));

		final long secondsLate = tardiness.toSeconds();
		return -3d / 20d * maxGrade * secondsLate / 3600;
	}

	public static void main(String[] args) throws Exception {
		final String prefix = "interfaces";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java L3/");
		orch.readUsernames(srcDir.resolve("usernamesGH-manual.json"));

		orch.readRepositories();
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExTwoSetsGrader grader = new ExTwoSetsGrader();

		final ImmutableSet<GradeWithStudentAndCriterion> grades = repositories.entrySet().stream()
				.map((e) -> GradeWithStudentAndCriterion.of(e.getKey(), grader.grade(e.getValue()))).collect(ImmutableSet.toImmutableSet());

		Files.writeString(srcDir.resolve("all grades " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(srcDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExTwoSetsGrader() {
		deadline = ZonedDateTime.parse("2019-04-16T23:59:59+01:00").toInstant();
		timeMark = null;
	}
}
