package io.github.oliviercailloux.java_grade.ex.print_exec;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.ProcessRunner;
import io.github.oliviercailloux.grade.contexters.ProcessRunner.ProcessOutput;
import io.github.oliviercailloux.grade.format.HtmlGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;
import io.github.oliviercailloux.java_grade.ex.print_exec.SourceScanner.SourceClass;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.supann.QueriesHelper;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

public class PrintExecGrader {
	public static enum PrintExecCriterion implements Criterion {
		FULL_CLASS_NAME, COMPILES, COMPILE_COMMAND, RUN_COMMAND, COMMAND, ARGS, CP, DOT, DIR, DIRS, TIGHT, DEST, TARGET,
		TESTS, TEST_TWO_ARGS, TEST_WITH_FOLDERS, TEST_TRUE, TEST_FOLDERS_FALSE;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PrintExecGrader.class);

	private static final String PREFIX = "print-exec";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-03-05T14:47:20+01:00").toInstant();
	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		QueriesHelper.setDefaultAuthenticator();

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList
//				.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final PrintExecGrader grader = new PrintExecGrader();
		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			LOGGER.info("With {}.", repository);
			final IGrade grade = grader.grade(repository);
			gradesB.put(repository.getUsername(), grade);
			final Path outDir = WORK_DIR;
			Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir);
			final Document doc = HtmlGrade.asHtml(grade, "print exec");
			Files.writeString(Path.of("grade.html"), XmlUtils.asString(doc));
		}
	}

	private Path compileDir;

	private String printExecClassName;

	PrintExecGrader() {
		compileDir = null;
		// nothing
		printExecClassName = null;
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord) throws IOException, GitAPIException {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory, DEADLINE);
		final Set<ObjectId> keptIds = ImmutableSet.copyOf(filtered.getGraph().nodes());
		final Set<ObjectId> allIds = gitHubHistory.getGraph().nodes();
		Verify.verify(allIds.containsAll(keptIds));
		final Set<ObjectId> excludedIds = Sets.difference(allIds, keptIds);
		final String comment;
		if (excludedIds.isEmpty()) {
			comment = "";
		} else {
			comment = "Excluded the following commits (pushed too late): "
					+ excludedIds.stream()
							.map(o -> o.getName().substring(0, 7) + " (" + gitHubHistory
									.getCorrectedAndCompletedPushedDates().get(o).atZone(ZoneId.systemDefault()) + ")")
							.collect(Collectors.joining("; "));
		}

		LOGGER.debug("Graph filtered history: {}.", filtered.getGraph().edges());
		fs.getHistory();
		final GitLocalHistory manual = filtered
				.filter(o -> !MarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final ImmutableGraph<ObjectId> graph = Utils.asImmutableGraph(manual.getGraph(), o -> o);
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			return Mark.zero("Found no manual commit (not counting commits from GitHub).");
		}

		final Set<RevCommit> unfiltered = fs.getHistory().getGraph().nodes();
		@SuppressWarnings("unlikely-arg-type")
		final boolean containsAll = unfiltered.containsAll(graph.nodes());
		Verify.verify(containsAll);

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		final GitLocalHistory ownHistory = filtered
				.filter(o -> MarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner));
		final ImmutableGraph<RevCommit> ownGraph = ownHistory.getGraph();
		gradeBuilder.put(JavaCriterion.ID, Mark.binary(ownGraph.nodes().size() >= 1));

		if (!graph.nodes().containsAll(ownGraph.nodes())) {
			LOGGER.warn("Risk of accessing a commit beyond deadline (e.g. master!).");
		}

		final ImmutableSet<SourceClass> sources = SourceScanner.scan(fs.getRoot());
		final Optional<SourceClass> printExecSource;
		if (sources.size() == 0) {
			printExecSource = Optional.empty();
		} else if (sources.size() == 1) {
			printExecSource = sources.stream().collect(MoreCollectors.toOptional());
		} else {
			/**
			 * In principle, this filter could match several paths (at varying levels of
			 * hierarchy), which will yield a crash.
			 */
			printExecSource = sources.stream().filter(s -> s.getShortClassName().toString().equals("PrintExec.java"))
					.collect(MoreCollectors.toOptional());
			if (printExecSource.isEmpty()) {
				LOGGER.warn("Multiple java files found, but no PrintExec.");
			}
		}
		final String fullName = printExecSource.map(SourceClass::getFullClassName).orElse("");
		gradeBuilder.put(PrintExecCriterion.FULL_CLASS_NAME,
				Mark.binary(fullName.equals("PrintExec"), "", "Expected 'PrintExec' but found '" + fullName + "'"));

		final String content = printExecSource.map(SourceClass::getPath)
				.map(p -> SourceScanner.read(p).replaceAll("package .*", "")).orElse("");
		printExecClassName = printExecSource.map(SourceClass::getShortClassName).orElse("");
		LOGGER.info("Print exec class name: {}.", printExecClassName);
		compileDir = WORK_DIR.resolve("En cours").resolve(owner);
		final Path targetClass = compileDir.resolve(printExecClassName + ".class");
		if (Files.exists(targetClass)) {
			Files.delete(targetClass);
		}
		final JavaFileObject source = SimpleCompiler.asJavaSource(printExecClassName, content);
		final List<Diagnostic<? extends JavaFileObject>> diags = SimpleCompiler.compile(ImmutableList.of(source),
				ImmutableSet.of(), compileDir);
		LOGGER.info("Diags: {}; compiling to {}.", diags.toString(), targetClass);
		Verify.verify(diags.isEmpty() == Files.exists(targetClass));
		gradeBuilder.put(PrintExecCriterion.COMPILES, Mark.binary(diags.isEmpty(), "", diags.toString()));

		final IGrade testsGrade;
		if (diags.isEmpty()) {
			final ImmutableMap.Builder<Criterion, IGrade> testsGradeBuilder = ImmutableMap.builder();
			testsGradeBuilder.put(PrintExecCriterion.TEST_TWO_ARGS,
					gradeTest(ImmutableList.of(), "mydir/", "ATestClass", ""));
			testsGradeBuilder.put(PrintExecCriterion.TEST_WITH_FOLDERS,
					gradeTest(ImmutableList.of("folder1/", "folder2/"), "mydir/", "ATestClass", ""));
			testsGradeBuilder.put(PrintExecCriterion.TEST_TRUE,
					gradeTest(ImmutableList.of(), "mydir/", "MyClass", "true"));
			testsGradeBuilder.put(PrintExecCriterion.TEST_FOLDERS_FALSE,
					gradeTest(ImmutableList.of("folder1"), "mydir/", "MyClass", "false"));
			testsGrade = WeightingGrade.from(testsGradeBuilder.build(),
					ImmutableMap.of(PrintExecCriterion.TEST_TWO_ARGS, 5d, PrintExecCriterion.TEST_WITH_FOLDERS, 4.5d,
							PrintExecCriterion.TEST_TRUE, 4.5d, PrintExecCriterion.TEST_FOLDERS_FALSE, 4.5d));
		} else {
			testsGrade = Mark.zero();
		}
		gradeBuilder.put(PrintExecCriterion.TESTS, testsGrade);

		final ImmutableMap.Builder<Criterion, Double> weightsBuilder = ImmutableMap.builder();
		weightsBuilder.put(JavaCriterion.ID, 0.5d);
		weightsBuilder.put(PrintExecCriterion.FULL_CLASS_NAME, 0.5d);
		weightsBuilder.put(PrintExecCriterion.COMPILES, 0.5d);
		weightsBuilder.put(PrintExecCriterion.TESTS, 18.5d);
		return WeightingGrade.from(gradeBuilder.build(), weightsBuilder.build(), comment);
	}

	private IGrade gradeTest(List<String> folders, String classDir, String className, String lastArg) {
		checkArgument(!folders.contains(classDir));

		final Builder<String> callArgsBuilder = ImmutableList.<String>builder();
		callArgsBuilder.addAll(folders).add(classDir).add(className);
		if (!lastArg.equals("")) {
			callArgsBuilder.add(lastArg);
		}
		final ImmutableList<String> callArgs = callArgsBuilder.build();

		final ProcessOutput result = call(compileDir, printExecClassName, callArgs);

		final ImmutableList<String> lines = result.getOutput().lines().collect(ImmutableList.toImmutableList());
		if (lines.size() > 2) {
			LOGGER.warn("Too many lines: {}.", lines);
		}
		final String firstLine = lines.size() >= 1 ? lines.get(0).strip() : "";
		final String secondLine = lines.size() >= 2 ? lines.get(1).strip() : "";

		final WeightingGrade javacGrade;
		{
			final ImmutableList<String> parsedJavac = ArgumentsParser.parse(firstLine);
			final PrintExecCommand javacCommand = PrintExecCommand.parse(parsedJavac);
			final boolean javac = javacCommand.getCommand().equals("javac");
			final boolean cpDot = javacCommand.getClasspathEntries().contains(".");
			final boolean cpDirs = javacCommand.getClasspathEntries().containsAll(folders);
			/**
			 * We want to grant those points only if something non-trivial has been
			 * included.
			 */
			final boolean cpDir = ((!folders.isEmpty() && cpDirs) || cpDot)
					&& !javacCommand.getClasspathEntries().contains(classDir);
			final ImmutableSet<CriterionGradeWeight> cpGrades = folders.isEmpty()
					? ImmutableSet.of(
							CriterionGradeWeight.from(PrintExecCriterion.DOT, Mark.binary(cpDot), 0.2d + 4d / 3d),
							CriterionGradeWeight.from(PrintExecCriterion.DIR, Mark.binary(cpDir), 0.4d + 8d / 3d))
					: ImmutableSet.of(CriterionGradeWeight.from(PrintExecCriterion.DOT, Mark.binary(cpDot), 0.2d),
							CriterionGradeWeight.from(PrintExecCriterion.DIR, Mark.binary(cpDir), 0.4d),
							CriterionGradeWeight.from(PrintExecCriterion.DIRS, Mark.binary(cpDirs), 0.4d));
			final IGrade cpGrade = WeightingGrade.from(cpGrades, "Seen: " + javacCommand.getClasspathEntries());
			/**
			 * In the case D should not be used, let’s avoid getting points simply by never
			 * outputting a D parameter, by cascading destGrade over cpGrade.
			 */
			final IGrade destGrade;
			if (lastArg.equals("true")) {
				destGrade = Mark.binary(javacCommand.hasD()
						&& (javacCommand.getDArgument().equals("bin") || javacCommand.getDArgument().equals("bin/")));
			} else {
				destGrade = javacCommand.isDUsed() ? Mark.zero() : Mark.given(cpGrade.getPoints(), "Ok, used CP mark");
			}
			final boolean fileNameArg = javacCommand.getLastArgument().equals(classDir + className + ".java");
			final IGrade argsGrade = WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(PrintExecCriterion.CP, cpGrade, 0.4d),
							CriterionGradeWeight.from(PrintExecCriterion.DEST, destGrade, 0.4d)));
			javacGrade = WeightingGrade.from(
					ImmutableSet.of(
							CriterionGradeWeight.from(PrintExecCriterion.COMMAND,
									Mark.binary(javac, "", "Seen: " + javacCommand.getCommand()), 0.1d),
							CriterionGradeWeight.from(PrintExecCriterion.ARGS, argsGrade, 0.6d),
							CriterionGradeWeight.from(PrintExecCriterion.TARGET,
									Mark.binary(fileNameArg, "", "Seen: " + javacCommand.getLastArgument()), 0.3d)),
					"Seen: " + parsedJavac);
		}
		final WeightingGrade javaGrade;
		{
			final ImmutableList<String> parsedJava = ArgumentsParser.parse(secondLine);
			final PrintExecCommand javaCommand = PrintExecCommand.parse(parsedJava);
			final boolean java = javaCommand.getCommand().equals("java");
			final boolean cpDot = javaCommand.getClasspathEntries().contains(".");
			final boolean cpDir = lastArg.equals("true")
					? (javaCommand.getClasspathEntries().contains("bin")
							|| javaCommand.getClasspathEntries().contains("bin/"))
					: javaCommand.getClasspathEntries().contains(classDir);
			final boolean cpDirs = javaCommand.getClasspathEntries().containsAll(folders);
			final ImmutableSet<CriterionGradeWeight> cpGrades = folders.isEmpty()
					? ImmutableSet.of(
							CriterionGradeWeight.from(PrintExecCriterion.DOT, Mark.binary(cpDot), 0.2d + 4d / 3d),
							CriterionGradeWeight.from(PrintExecCriterion.DIR, Mark.binary(cpDir), 0.4d + 8d / 3d))
					: ImmutableSet.of(CriterionGradeWeight.from(PrintExecCriterion.DOT, Mark.binary(cpDot), 0.2d),
							CriterionGradeWeight.from(PrintExecCriterion.DIR, Mark.binary(cpDir), 0.4d),
							CriterionGradeWeight.from(PrintExecCriterion.DIRS, Mark.binary(cpDirs), 0.4d));
			final IGrade cpGrade = WeightingGrade.from(cpGrades, "Seen: " + javaCommand.getClasspathEntries());
			final boolean classNameArg = javaCommand.getLastArgument().equals(className);
			javaGrade = WeightingGrade.from(
					ImmutableSet.of(
							CriterionGradeWeight.from(PrintExecCriterion.COMMAND,
									Mark.binary(java, "", "Seen: " + javaCommand.getCommand()), 0.1d),
							CriterionGradeWeight.from(PrintExecCriterion.CP, cpGrade, 0.6d),
							CriterionGradeWeight.from(PrintExecCriterion.TARGET,
									Mark.binary(classNameArg, "", "Seen: " + javaCommand.getLastArgument()), 0.3d)),
					"Seen: " + parsedJava);
		}
		final String comment = "Using args " + callArgs
				+ (result.getError().isEmpty() ? "" : "; got error: '" + result.getError() + "'");
		final WeightingGrade grade = WeightingGrade.proportional(PrintExecCriterion.COMPILE_COMMAND, javacGrade,
				PrintExecCriterion.RUN_COMMAND, javaGrade, comment);
		return grade;
	}

	private ProcessOutput call(Path root, String fullClassName, List<String> arguments) {
		final ImmutableList<String> toRun = Streams.concat(Stream.of("java", fullClassName), arguments.stream())
				.collect(ImmutableList.toImmutableList());
		LOGGER.info("Running from {}, {}.", root, toRun);
		return ProcessRunner.run(root.toFile(), toRun);
	}

}
