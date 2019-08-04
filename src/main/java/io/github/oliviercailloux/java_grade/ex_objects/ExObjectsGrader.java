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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FilesSourceUtils;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;

public class ExObjectsGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExObjectsGrader.class);
	private static final Instant DEADLINE = ZonedDateTime.parse("2019-04-03T23:59:59+01:00").toInstant();

	public ExObjectsGrader() {
		timeMark = null;
	}

	private IGrade timeMark;

	public Map<Criterion, IGrade> grade(RepositoryCoordinates coord, Instant ignoreAfter) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
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

		timeMark = Marks.timeGrade(fullContext, DEADLINE, (d) -> 1d);
		gradeBuilder.put(ON_TIME, timeMark);
		gradeBuilder.put(REPO_EXISTS, Marks.gitRepoGrade(fullContext));

		final Predicate<Path> project43src = (p) -> p.startsWith(Paths.get("project43", "src"));
		final Predicate<Path> projet43src = (p) -> p.startsWith(Paths.get("projet43", "src"));
		final FilesSource p43sources = filesReader.filterOnPath(project43src.or(projet43src));
		gradeBuilder.put(P43, Mark.given(Booleans.countTrue(!p43sources.asFileContents().isEmpty()), ""));
		final Predicate<Path> project47src = (p) -> p.startsWith(Paths.get("project47", "src"));
		final Predicate<Path> projet47src = (p) -> p.startsWith(Paths.get("projet47", "src"));
		final FilesSource p47sources = filesReader.filterOnPath(project47src.or(projet47src));
		gradeBuilder.put(P47, Mark.given(Booleans.countTrue(!p47sources.asFileContents().isEmpty()), ""));
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
		gradeBuilder.put(P53UTILS, Mark.given(Booleans.countTrue(!p53PairOfDices.asFileContents().isEmpty(),
				!p53StatCalcs.asFileContents().isEmpty()), ""));

		final FilesSource p53jars = filesReader.filterOnPath(Predicates.equalTo(Paths.get("project53utils/utils.jar"))
				.or(Predicates.equalTo(Paths.get("projet53utils/utils.jar"))));
		final Path p53jarPath = FilesSourceUtils.getSinglePath(p53jars);
		final Mark jarMark;
		if (p53jarPath.getNameCount() == 0 || p53jarPath.equals(Paths.get(""))) {
			jarMark = Mark.zero("utils.jar not found");
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
				jarMark = Mark.given(Booleans.countTrue(jarContainsPairOfDice, jarContainsStatCalc), "");
			} catch (IOException e) {
				LOGGER.warn("p53jarPath: {} ({} components).", p53jarPath, p53jarPath.getNameCount());
				throw new IllegalStateException(e);
			}
		}
		gradeBuilder.put(P53JAR, jarMark);

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
		gradeBuilder.put(PREFIX, prefixMark(p53MainSrcPath, p53MainSources));

		final ImmutableList<? extends JavaFileObject> srcToCompile = p53MainSources
				.filterOnPath((p) -> p.getName(p.getNameCount() - 1).toString().endsWith(".java")).asFileContents()
				.stream().map((fc) -> SimpleCompiler.asJavaSource(p53MainSrcPath, fc))
				.collect(ImmutableList.toImmutableList());
		final Mark jarRequiredMark;
		if (jarMark.getPoints() != P53JAR.getMaxPoints() || srcToCompile.isEmpty()) {
			jarRequiredMark = Mark.zero();
		} else {
//				final Path cp = projectPath.resolve(p53MainSrcPath);
			final Path cp = projectPath.resolve(p53jarPath);
			final List<Diagnostic<? extends JavaFileObject>> diagnosticsNaked = SimpleCompiler.compile(srcToCompile,
					ImmutableList.of());
			final List<Diagnostic<? extends JavaFileObject>> diagnosticsWithJar = SimpleCompiler.compile(srcToCompile,
					ImmutableList.of(cp));
			if (!diagnosticsNaked.isEmpty() && diagnosticsWithJar.isEmpty()) {
				jarRequiredMark = Mark.one();
			} else {
				jarRequiredMark = Mark.zero(
						"No jar: " + diagnosticsNaked.toString() + " — With jar: " + diagnosticsWithJar.toString());
			}
		}
		gradeBuilder.put(JAR_REQUIRED, jarRequiredMark);

		return gradeBuilder.build();
	}

	private IGrade prefixMark(Path p53MainSrcPath, FilesSource p53MainSources) {
		if (p53MainSources.asFileContents().isEmpty()) {
			return Mark.zero();
		}
		final ImmutableList<Path> prefixes = p53MainSources.getContents().keySet().stream()
				.map((p) -> p53MainSrcPath.relativize(p)).map(ExObjectsGrader::prefixThree)
				.collect(ImmutableList.toImmutableList());
		assert prefixes.size() >= 1;
		final Mark mark;
		switch (prefixes.size()) {
		case 1:
			final Path prefix = prefixes.stream().collect(MoreCollectors.onlyElement());
			if (prefix.getNameCount() >= 3) {
				mark = Mark.one();
			} else {
				mark = Mark.zero(String.format("Common prefix is too short: '%s'.", prefix));
			}
			break;
		default:
			mark = Mark.zero(String.format("Found multiple prefixes: %s.", prefixes));
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
}
