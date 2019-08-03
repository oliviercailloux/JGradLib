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
import java.util.Map;
import java.util.Optional;
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
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;

public class ExTwoSetsGrader {

	private Instant deadline;

	private String toString(Optional<ObjectId> optCommit) {
		final Optional<String> optId = optCommit.map(ObjectId::getName);
		return optId.isPresent() ? optId.get() : optId.toString();
	}

	public Map<Criterion, IGrade> grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
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

		timeMark = Marks.timeGrade(fullContext, deadline, this::getPenalty);
		gradeBuilder.put(ON_TIME, timeMark);
		gradeBuilder.put(REPO_EXISTS, Marks.gitRepoGrade(fullContext));

		final Predicate<Path> intQ = (p) -> p.endsWith("IntQuestion.java");
		final Predicate<Path> ee57 = (p) -> p.endsWith("EE57.java");
		final FilesSource ee57src = filesReader.filterOnPath(intQ.or(ee57));
		final FilesSource intQSrc = filesReader.filterOnContent(Predicates.containsPattern("IntQuestion "));
		final boolean foundEe57Src = ee57src.asFileContents().size() >= 1;
		final boolean foundQSrc = intQSrc.asFileContents().size() >= 1;
		if (!foundEe57Src && foundQSrc) {
			LOGGER.warn("Only embedded!");
		}
		gradeBuilder.put(EX_57, Mark.given(Booleans.countTrue(foundEe57Src || foundQSrc), ""));

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
		gradeBuilder.put(TYPE_SET, Mark.given(compiles && srcSet.asFileContents().size() >= 1 ? 1d : 0d, typeSetCmt));

		gradeBuilder
				.put(THROWS,
						Mark.given(
								Booleans.countTrue(
										compiles && filesReader.anyMatch(Predicates.containsPattern("[\t ]throw ")
												.and(Predicates.containsPattern(
														"(IllegalArgumentException)|(Error)|(NullPointerException)")))),
								""));

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
		gradeBuilder.put(JAVADOC,
				Mark.given(
						Booleans.countTrue(
								compiles && noAutoGen && filesReader.anyMatch(Predicates.containsPattern("/\\*\\*"))),
						""));

		gradeBuilder.put(CONCATENATES, Mark.given(
				Booleans.countTrue(compiles && filesReader.anyMatch(Predicates.containsPattern("\\.addAll"))), ""));

		return gradeBuilder.build();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExTwoSetsGrader.class);
	private IGrade timeMark;
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

	public ExTwoSetsGrader() {
		deadline = ZonedDateTime.parse("2019-04-16T23:59:59+01:00").toInstant();
		timeMark = null;
	}
}
