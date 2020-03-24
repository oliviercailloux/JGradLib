package io.github.oliviercailloux.java_grade.ex.print_exec;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.ProcessRunner;
import io.github.oliviercailloux.grade.contexters.ProcessRunner.ProcessOutput;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.MarkHelper;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.SourceScanner;
import io.github.oliviercailloux.java_grade.SourceScanner.SourceClass;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.supann.QueriesHelper;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

public class PrintExecGrader {
	public static enum PrintExecCriterion implements Criterion {
		FIRST_ATTEMPT, SECOND_ATTEMPT, FULL_CLASS_NAME, COMPILES, COMPILE_COMMAND, RUN_COMMAND, COMMAND, ARGS, CP, DOT,
		DIR, DIRS, TIGHT, DEST, TARGET, TESTS, TEST_TWO_ARGS, TEST_WITH_FOLDERS, TEST_TRUE, TEST_FOLDERS_FALSE;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PrintExecGrader.class);

	private static final String PREFIX = "print-exec";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-03-05T14:47:24+01:00").toInstant();
	private static final Instant DEADLINE2 = ZonedDateTime.parse("2020-03-23T00:00:00+01:00").toInstant();

	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		QueriesHelper.setDefaultAuthenticator();

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		@SuppressWarnings("all")
		final Type type = new HashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		final Map<String, IGrade> grades1Read = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("all grades print-exec-1.json")), type, JsonGrade.asAdapter());

		final ImmutableMap.Builder<String, Double> weightsBuilder = ImmutableMap.builder();
		final ImmutableMap.Builder<String, Integer> nbLinesBuilder = ImmutableMap.builder();
		final CsvParserSettings settings = new CsvParserSettings();
		settings.setHeaderExtractionEnabled(true);
		final CsvParser parser = new CsvParser(settings);
		for (Record record : parser
				.parseAllRecords(new StringReader(Files.readString(WORK_DIR.resolve("print-exec weights.csv"))))) {
			final String username = record.getString("GitHub username");
			LOGGER.debug("Read from CSV: {}.", username);
			final int lineCount = record.getInt("Modified lines");
			final double weight = record.getDouble("Weight second print exec");
			nbLinesBuilder.put(username, lineCount);
			weightsBuilder.put(username, weight);
		}
		final ImmutableMap<String, Double> weights = weightsBuilder.build();
		final ImmutableMap<String, Integer> nbLines = nbLinesBuilder.build();

		final PrintExecGrader grader = new PrintExecGrader(
				s -> s.equals("…") ? ZonedDateTime.parse("2020-03-18T16:00:00+01:00").toInstant() : DEADLINE);
		grader.grades1 = grades1Read;
		grader.weights = weights;
		grader.nbLines = nbLines;

		final Map<String, IGrade> grades = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			LOGGER.info("With {}.", repository);
			final IGrade grade = grader.grade(repository);
			grades.put(repository.getUsername(), grade);
			final Path outDir = WORK_DIR;
			Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
					JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir, false);
			final Document doc = HtmlGrades.asHtml(grade, "print exec");
			Files.writeString(Path.of("grade.html"), XmlUtils.asString(doc));
		}

		final StudentsReaderFromJson usernames = new StudentsReaderFromJson();
		usernames.read(WORK_DIR.resolve("usernames.json"));

		final ImmutableMap<String, IGrade> grades1Output = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey(),
						e -> ((WeightingGrade) e.getValue()).getSubGrades().get(PrintExecCriterion.FIRST_ATTEMPT)));
		verify(grades1Output.equals(Maps.filterKeys(grades1Read, grades::containsKey)));
		final ImmutableMap<String, IGrade> grades2Output = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey(),
						e -> ((WeightingGrade) e.getValue()).getSubGrades().get(PrintExecCriterion.SECOND_ATTEMPT)));

		Files.writeString(WORK_DIR.resolve("all grades print-exec 1.csv"),
				CsvGrades.asCsv(grades1Output.entrySet().stream().filter(e -> e.getValue() instanceof WeightingGrade)
						.collect(ImmutableMap.toImmutableMap(e -> usernames.getStudentOnGitHub(e.getKey()),
								e -> (WeightingGrade) e.getValue()))));
		Files.writeString(WORK_DIR.resolve("all grades print-exec 2.csv"),
				CsvGrades.asCsv(grades2Output.entrySet().stream().filter(e -> e.getValue() instanceof WeightingGrade)
						.collect(ImmutableMap.toImmutableMap(e -> usernames.getStudentOnGitHub(e.getKey()),
								e -> (WeightingGrade) e.getValue()))));
	}

	private final Function<String, Instant> deadlines;
	Map<String, IGrade> grades1;
	ImmutableMap<String, Double> weights;
	ImmutableMap<String, Integer> nbLines;

	private Path compileDir;

	private String printExecClassName;

	private Optional<SourceClass> printExecSource;

	final List<String> diffCommands;

	PrintExecGrader() {
		this(s -> DEADLINE);
	}

	PrintExecGrader(Function<String, Instant> deadlines) {
		compileDir = null;
		printExecClassName = null;
		this.deadlines = deadlines;
		printExecSource = null;
		diffCommands = new ArrayList<>();
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord) throws IOException, GitAPIException {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.debug("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		final IGrade first = gradePart(owner, fs, gitHubHistory, deadlines, this::getFirstTestGrade);
		final Optional<SourceClass> firstSource = printExecSource;
		final IGrade previous = grades1.get(owner);
		/**
		 * In general, the grades will differ as the comment about the ignored commits
		 * changes. But the points should be the same or should be better previously,
		 * thanks to manual corrections.
		 */
		if (first.getPoints() > previous.getPoints()) {
			LOGGER.warn("New grading is better: {} ⇐ {}.", first.getPoints(), previous.getPoints());
		}

		final IGrade second = gradePart(owner, fs, gitHubHistory, s -> DEADLINE2, this::getSecondTestGrade);
		final Optional<SourceClass> secondSource = printExecSource;
		if (firstSource.isEmpty()) {
			verify(secondSource.isEmpty());
		}
		final double w2;
		if (firstSource.isEmpty() || secondSource.isEmpty()) {
			w2 = 0;
		} else {
			final String command = "cd ../print-exec-" + owner + "; git diff "
					+ firstSource.get().getPath().getRoot().toString().substring(0, 6) + ":"
					+ firstSource.get().getPath().getFileName() + " "
					+ secondSource.get().getPath().getRoot().toString().substring(0, 6) + ":"
					+ secondSource.get().getPath().getFileName();
			diffCommands.add(command);
			LOGGER.debug(command);
			final double weight = weights.get(owner);
			w2 = weight;
		}
		final double w1 = 1 - w2;
		final int nbL = nbLines.get(owner);
		final WeightingGrade aggregated = WeightingGrade.fromList(
				ImmutableList.of(CriterionGradeWeight.from(PrintExecCriterion.FIRST_ATTEMPT, previous, w1),
						CriterionGradeWeight.from(PrintExecCriterion.SECOND_ATTEMPT, second, w2)),
				"Nb lines modified: " + nbL);
		return aggregated;
	}

	private IGrade gradePart(String owner, GitRepoFileSystem fs, GitHubHistory gitHubHistory,
			@SuppressWarnings("hiding") Function<String, Instant> deadlines, Supplier<IGrade> testGrader)
			throws IOException {
		printExecSource = null;
		compileDir = null;
		printExecClassName = null;

		final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory,
				deadlines.apply(owner));
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
							.map(o -> o.getName().substring(0, 7) + " ("
									+ gitHubHistory.getCorrectedAndCompletedPushedDates().get(o)
											.atZone(ZoneId.systemDefault())
									+ ")")
							.collect(Collectors.joining("; "))
					+ ".";
		}

		LOGGER.debug("Graph filtered history: {}.", filtered.getGraph().edges());
		fs.getHistory();
		final GitLocalHistory manual = filtered
				.filter(o -> !JavaMarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final ImmutableGraph<ObjectId> graph = Utils.asImmutableGraph(manual.getGraph(), o -> o);
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			printExecSource = Optional.empty();
			return Mark.zero(comment + (comment.isEmpty() ? "" : " ")
					+ "Found no manual commit (not counting commits from GitHub).");
		}

		final Set<RevCommit> unfiltered = fs.getHistory().getGraph().nodes();
		@SuppressWarnings("unlikely-arg-type")
		final boolean containsAll = unfiltered.containsAll(graph.nodes());
		Verify.verify(containsAll);

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		final GitLocalHistory ownHistory = filtered
				.filter(o -> JavaMarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner));
		final ImmutableGraph<RevCommit> ownGraph = ownHistory.getGraph();
		gradeBuilder.put(JavaCriterion.ID, Mark.binary(ownGraph.nodes().size() >= 1));

		if (!graph.nodes().containsAll(ownGraph.nodes())) {
			LOGGER.warn("Risk of accessing a commit beyond deadline (e.g. master!).");
		}

		final ImmutableSet<RevCommit> tips = filtered.getTips();
		final GitPath master = fs.getRelativePath("");
		final ObjectId masterId = fs.getCommitId(master).get();
		final ImmutableSet<RevCommit> bestTips = MarkHelper.findBestMatches(tips,
				ImmutableList.of(o -> o.equals(masterId), o -> true));
		checkState(bestTips.size() == 1);
		final ObjectId tip = tips.iterator().next();

		final ImmutableSet<SourceClass> sources = SourceScanner.scan(fs.getAbsolutePath(tip.getName()));
		{
			/**
			 * In principle, this filter could match several paths (at varying levels of
			 * hierarchy), which will yield a crash.
			 */
			final Predicate<SourceClass> predicate1 = s -> s.getShortClassName().toString().equals("PrintExec");
			final Predicate<SourceClass> predicate2 = s -> s.getShortClassName().toString()
					.equalsIgnoreCase("PrintExec");
			final Predicate<SourceClass> predicate3 = s -> s.getShortClassName().toString()
					.equalsIgnoreCase("print_exec");
			final ImmutableSet<SourceClass> bestSources = io.github.oliviercailloux.grade.markers.MarkHelper
					.findBestMatches(sources, ImmutableList.of(predicate1, predicate2, predicate3, s -> true));
			printExecSource = bestSources.stream().collect(MoreCollectors.toOptional());
		}

		final String fullName = printExecSource.map(SourceClass::getFullClassName).orElse("");
		gradeBuilder.put(PrintExecCriterion.FULL_CLASS_NAME,
				Mark.binary(fullName.equals("PrintExec"), "", "Expected 'PrintExec' but found '" + fullName + "'"));

		final String content = printExecSource.map(SourceClass::getPath)
				.map(p -> JavaGradeUtils.read(p).replaceAll("package .*", "")).orElse("");
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
			testsGrade = testGrader.get();
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

	private IGrade getFirstTestGrade() {
		final IGrade testsGrade;
		final ImmutableMap.Builder<Criterion, IGrade> testsGradeBuilder = ImmutableMap.builder();
		testsGradeBuilder.put(PrintExecCriterion.TEST_TWO_ARGS,
				gradeTest(ImmutableList.of(), "mydir/", "ATestClass", ""));
		testsGradeBuilder.put(PrintExecCriterion.TEST_WITH_FOLDERS,
				gradeTest(ImmutableList.of("folder1/", "folder2/"), "mydir/", "ATestClass", ""));
		testsGradeBuilder.put(PrintExecCriterion.TEST_TRUE, gradeTest(ImmutableList.of(), "mydir/", "MyClass", "true"));
		testsGradeBuilder.put(PrintExecCriterion.TEST_FOLDERS_FALSE,
				gradeTest(ImmutableList.of("folder1"), "mydir/", "MyClass", "false"));
		testsGrade = WeightingGrade.from(testsGradeBuilder.build(),
				ImmutableMap.of(PrintExecCriterion.TEST_TWO_ARGS, 5d, PrintExecCriterion.TEST_WITH_FOLDERS, 4.5d,
						PrintExecCriterion.TEST_TRUE, 4.5d, PrintExecCriterion.TEST_FOLDERS_FALSE, 4.5d));
		return testsGrade;
	}

	private IGrade getSecondTestGrade() {
		final IGrade testsGrade;
		final ImmutableMap.Builder<Criterion, IGrade> testsGradeBuilder = ImmutableMap.builder();
		testsGradeBuilder.put(PrintExecCriterion.TEST_TWO_ARGS,
				gradeTest(ImmutableList.of(), "myfirstfolder/", "ASimpleTestClass", ""));
		testsGradeBuilder.put(PrintExecCriterion.TEST_WITH_FOLDERS,
				gradeTest(ImmutableList.of("folder1/", "folder2/", "asupplementaryfolder/", "yetanotherfolder/"),
						"themainfolder/", "ASimpleTestClass", ""));
		testsGradeBuilder.put(PrintExecCriterion.TEST_TRUE,
				gradeTest(ImmutableList.of("afolder/"), "themainfolder/", "MySimpleClass", "true"));
		testsGradeBuilder.put(PrintExecCriterion.TEST_FOLDERS_FALSE,
				gradeTest(ImmutableList.of("folder1/", "folder2/"), "mymaindir/", "MySimpleClass", "false"));
		testsGrade = WeightingGrade.from(testsGradeBuilder.build(),
				ImmutableMap.of(PrintExecCriterion.TEST_TWO_ARGS, 5d, PrintExecCriterion.TEST_WITH_FOLDERS, 4.5d,
						PrintExecCriterion.TEST_TRUE, 4.5d, PrintExecCriterion.TEST_FOLDERS_FALSE, 4.5d));
		return testsGrade;
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
		LOGGER.debug("Running from {}, {}.", root, toRun);
		return ProcessRunner.run(root.toFile(), toRun);
	}

}
