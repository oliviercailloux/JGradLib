package io.github.oliviercailloux.java_grade.ex;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.bytecode.Compiler;
import io.github.oliviercailloux.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.bytecode.Instanciator;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
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
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.samples.string_files.StringFilesUtils;
import io.github.oliviercailloux.utils.Utils;
import io.vavr.control.Try;

public class StringFilesGrader {
	static enum LocalCriterion implements Criterion {
		IMPL, FACTORY, REF_THROWS_ON_NULL, REF_IS_CURRENT_INITIALLY, REF_CHANGED, REF_NO_SPURIOUS_CHANGE,
		REF_USING_OTHER_FS, REF_DOT_IS_EMPTY, ABS_THROWS_ON_NULL, ABS_THROWS_ON_ABS, ABS_ON_CUR_PLUS_DIR,
		ABS_ON_DOT_PLUS_FILE, ABS_ON_DIR_PLUS_FILE, ABS_ON_JIM_PLUS_FILE, CONTENT_THROWS, CONTENT_READS_FROM_JIMFS,
		CONTENT_READS_FROM_JIMFS_USING_8859, RELATIVE_TO_CURRENT_TO_RELATIVE_TO_REFERENCE;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StringFilesGrader.class);

	private static final String PREFIX = "string-files";

	private static final String TEST_NAME = "string-files-homework";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-05-11T00:00:00+02:00").toInstant();

	/**
	 * NB for this to work, we need to have the interfaces in the class path with
	 * the same name as the one used by the student’s projects, namely, …samples.…
	 *
	 */
	public static void main(String[] args) throws Exception {
		final Path outDir = Paths.get("../../Java L3/");
		final Path projectsBaseDir = outDir.resolve(TEST_NAME);

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final StringFilesGrader grader = new StringFilesGrader();

		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final Path projectDir = projectsBaseDir.resolve(repository.getRepositoryName());
			gradesB.put(repository.getUsername(), grader.grade(repository, projectDir));
			Files.writeString(outDir.resolve("all grades " + TEST_NAME + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(TEST_NAME, outDir);
		}
	}

	Instant deadline;

	StringFilesGrader() {
		deadline = DEADLINE;
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord, Path projectDir) throws IOException {
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		checkArgument(gitHubHistory.getPatchedKnowns().nodes().isEmpty());

		final Optional<Instant> lastCommitInstantBeforeDeadlineOpt = gitHubHistory.getCommitDates().values().stream()
				.filter(i -> i.isBefore(deadline)).max(Comparator.naturalOrder());
		if (lastCommitInstantBeforeDeadlineOpt.isEmpty()) {
			return Mark.zero("Found no master commit.");
		}

		final Instant lastCommitGitHubInstantBeforeDeadline = lastCommitInstantBeforeDeadlineOpt.get();
		final ImmutableSet<ObjectId> lastCommitsBeforeDeadline = gitHubHistory.getCommitDates().asMultimap().inverse()
				.get(lastCommitGitHubInstantBeforeDeadline);
		verify(!lastCommitsBeforeDeadline.isEmpty());
		final GitLocalHistory history = fs.getHistory();
		final Optional<ObjectId> lastCommitBeforeDeadlineOpt = lastCommitsBeforeDeadline.stream()
				.max(Comparator.comparing(o -> history.getCommitDateById(o)));
		verify(lastCommitBeforeDeadlineOpt.isPresent());
		final ObjectId lastCommitBeforeDeadline = lastCommitBeforeDeadlineOpt.get();
		final Instant lastCommitInstantBeforeDeadline = history.getCommitDateById(lastCommitBeforeDeadline);
		verify(lastCommitsBeforeDeadline.stream().allMatch(o -> o.equals(lastCommitBeforeDeadline)
				|| history.getCommitDateById(o).isBefore(lastCommitInstantBeforeDeadline)));

		final GitPath gitSourcePath = fs.getAbsolutePath(lastCommitBeforeDeadline.getName());

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		{
			final RevCommit master = fs.getCachedHistory().getCommit(lastCommitBeforeDeadline);
			gradeBuilder.put(JavaCriterion.ID, Mark.binary(JavaMarkHelper.committerAndAuthorIs(master, owner)));
		}

		final Path fileSourcePath = Utils.getTempUniqueDirectory("src-" + owner);
		Utils.copyRecursively(gitSourcePath, fileSourcePath, StandardCopyOption.REPLACE_EXISTING);

		final MavenManager mavenManager = new MavenManager();
		final boolean compiled = mavenManager.compile(fileSourcePath);
		if (compiled) {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled));
			final ImmutableList<Path> classPath = mavenManager.getClassPath(fileSourcePath);
			checkArgument(classPath.size() >= 2);
			final Path java = fileSourcePath.resolve("src/main/java/");
			final ImmutableList<Path> depsAndItself = ImmutableList.<Path>builder().add(java).addAll(classPath).build();

			final Path effectiveSourcePath = java
					.resolve("io/github/oliviercailloux/samples/string_files/MyStringFilesUtils.java");
			final boolean suppressed = Files.readString(effectiveSourcePath).contains("@SuppressWarnings");
			final CompilationResult stringFiles = Compiler.eclipseCompile(depsAndItself,
					ImmutableSet.of(effectiveSourcePath));
			verify(stringFiles.compiled, stringFiles.err);
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.binary(!suppressed && (stringFiles.countWarnings() == 0),
					"", stringFiles.err.replaceAll(fileSourcePath.toAbsolutePath().toString() + "/", "")));
		} else {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled, "", mavenManager.getCensoredOutput()));
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.zero());
		}
		final IGrade implGrade;
		if (compiled) {
			try (URLClassLoader loader = new URLClassLoader(
					new URL[] { fileSourcePath.resolve(Path.of("target/classes/")).toUri().toURL() },
					getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);

				final Optional<StringFilesUtils> instanceOpt = instanciator.getInstance(StringFilesUtils.class,
						"newInstance");
				if (instanceOpt.isPresent()) {
					implGrade = grade(() -> instanciator.getInstance(StringFilesUtils.class, "newInstance").get());
				} else {
					implGrade = Mark.zero("Could not initialize implementation: " + instanciator.getLastException());
				}
			}
		} else {
			implGrade = Mark.zero();
		}
		gradeBuilder.put(LocalCriterion.IMPL, implGrade);

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(JavaCriterion.ID, 1d);
		builder.put(JavaCriterion.COMPILE, 1.5d);
		builder.put(JavaCriterion.NO_WARNINGS, 1.5d);
		builder.put(LocalCriterion.IMPL, 16d);
		return WeightingGrade.from(subGrades, builder.build(), "Using commit " + lastCommitBeforeDeadline.getName());
	}

	private IGrade grade(Supplier<StringFilesUtils> factory) throws IOException {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		{
			final StringFilesUtils instance = factory.get();
			boolean thrown = JavaGradeUtils.doesThrow(() -> instance.setReferenceFolder(null),
					e -> e instanceof NullPointerException);
			gradeBuilder.put(LocalCriterion.REF_THROWS_ON_NULL, Mark.binary(thrown));
		}
		{
			final StringFilesUtils instance = factory.get();
			final Try<Boolean> changedToDot = Try.of(() -> instance.setReferenceFolder(Path.of(".")));
			final StringFilesUtils instance2 = factory.get();
			final Try<Boolean> changedToEmpty = Try.of(() -> instance2.setReferenceFolder(Path.of("")));
			gradeBuilder.put(LocalCriterion.REF_IS_CURRENT_INITIALLY,
					Mark.binary(changedToDot.isSuccess() && changedToEmpty.isSuccess()
							&& (changedToDot.equals(Try.success(false)) || changedToEmpty.equals(Try.success(false)))));
		}
		{
			final StringFilesUtils instance = factory.get();
			Try.of(() -> instance.setReferenceFolder(Path.of("")));
			final Try<Boolean> changed = Try.of(() -> instance.setReferenceFolder(Path.of(".")));
			gradeBuilder.put(LocalCriterion.REF_DOT_IS_EMPTY, Mark.binary(changed.equals(Try.success(false))));
		}
		{
			final StringFilesUtils instance = factory.get();
			final Try<Boolean> changed = Try.of(() -> instance.setReferenceFolder(Path.of("truc chose")));
			gradeBuilder.put(LocalCriterion.REF_CHANGED, Mark.binary(changed.equals(Try.success(true))));
		}
		{
			final StringFilesUtils instance = factory.get();
			Try.of(() -> instance.setReferenceFolder(Path.of("truc/chose")));
			final Try<Boolean> changed = Try.of(() -> instance.setReferenceFolder(Path.of("truc/./../truc/chose")));
			gradeBuilder.put(LocalCriterion.REF_NO_SPURIOUS_CHANGE, Mark.binary(changed.equals(Try.success(false))));
		}
		{
			try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
				final StringFilesUtils instance = factory.get();
				final Try<Boolean> changed = Try.of(() -> instance.setReferenceFolder(jimfs.getPath(".")));
				gradeBuilder.put(LocalCriterion.REF_USING_OTHER_FS, Mark.binary(changed.equals(Try.success(true))));
			}
		}

		{
			final StringFilesUtils instance = factory.get();
			boolean thrown = JavaGradeUtils.doesThrow(() -> instance.getAbsolutePath(null),
					e -> e instanceof NullPointerException);
			gradeBuilder.put(LocalCriterion.ABS_THROWS_ON_NULL, Mark.binary(thrown));
		}
		{
			final StringFilesUtils instance = factory.get();
			boolean thrown = JavaGradeUtils.doesThrow(() -> instance.getAbsolutePath("/usr"),
					e -> e instanceof IllegalArgumentException);
			gradeBuilder.put(LocalCriterion.ABS_THROWS_ON_ABS, Mark.binary(thrown));
		}
		{
			final StringFilesUtils instance = factory.get();
			final String relative = "adir/anotherdir/";
			final Try<Path> pathAbs = Try.of(() -> Path.of(instance.getAbsolutePath(relative)));
			gradeBuilder.put(LocalCriterion.ABS_ON_CUR_PLUS_DIR, Mark.binary(
					pathAbs.isSuccess() && pathAbs.get().normalize().equals(Path.of(relative).toAbsolutePath())));
		}
		{
			final StringFilesUtils instance = factory.get();
			final Path reference = Path.of(".");
			Try.of(() -> instance.setReferenceFolder(reference));
			final String relative = "adir/afile";
			final Try<Path> pathAbs = Try.of(() -> Path.of(instance.getAbsolutePath(relative)));
			gradeBuilder.put(LocalCriterion.ABS_ON_DOT_PLUS_FILE, Mark.binary(
					pathAbs.isSuccess() && pathAbs.get().normalize().equals(Path.of(relative).toAbsolutePath())));
		}
		{
			final StringFilesUtils instance = factory.get();
			final Path reference = Path.of("adir/");
			Try.of(() -> instance.setReferenceFolder(reference));
			final String relative = "dir/file.txt";
			final Try<Path> pathAbs = Try.of(() -> Path.of(instance.getAbsolutePath(relative)));
			gradeBuilder.put(LocalCriterion.ABS_ON_DIR_PLUS_FILE, Mark.binary(pathAbs.isSuccess()
					&& pathAbs.get().normalize().equals(reference.resolve(relative).toAbsolutePath())));
		}
		{
			try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
				final StringFilesUtils instance = factory.get();
				final Path reference = jimfs.getPath("subdir/");
				Try.of(() -> instance.setReferenceFolder(reference));
				final String relative = "dir/file.txt";
				final Try<String> pathAbs = Try.of(() -> instance.getAbsolutePath(relative));
				gradeBuilder.put(LocalCriterion.ABS_ON_JIM_PLUS_FILE, Mark.binary(pathAbs.isSuccess() && Strings
						.nullToEmpty(pathAbs.get()).equals(reference.resolve(relative).toAbsolutePath().toString())));
			}
		}

		{
			final StringFilesUtils instance = factory.get();
			final String relative = "nonexistent.txt";
			final boolean thrown = JavaGradeUtils.doesThrow(() -> instance.getContentUsingIso88591Charset(relative),
					e -> e instanceof IOException);
			gradeBuilder.put(LocalCriterion.CONTENT_THROWS, Mark.binary(thrown));
		}
		{
			try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
				final StringFilesUtils instance = factory.get();
				final Path reference = jimfs.getPath("subdir/");
				Try.of(() -> instance.setReferenceFolder(reference));
				if (!jimfs.isOpen()) {
					gradeBuilder.put(LocalCriterion.CONTENT_READS_FROM_JIMFS, Mark.zero("Closed prematurely."));
				} else {
					final String relative = "existent.txt";
					Files.createDirectory(reference);
					Files.writeString(reference.resolve(relative), "One\rTwo\nThree\r\n Four\n");
					final ImmutableList<String> read = Try.of(() -> instance.getContentUsingIso88591Charset(relative))
							.getOrElse(ImmutableList.of());
					gradeBuilder.put(LocalCriterion.CONTENT_READS_FROM_JIMFS,
							Mark.binary(read != null && read.equals(ImmutableList.of("One", "Two", "Three", " Four"))));
				}
			}
		}
		{
			try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
				final StringFilesUtils instance = factory.get();
				final Path reference = jimfs.getPath("subdir/");
				Try.of(() -> instance.setReferenceFolder(reference));
				if (!jimfs.isOpen()) {
					gradeBuilder.put(LocalCriterion.CONTENT_READS_FROM_JIMFS_USING_8859,
							Mark.zero("Closed prematurely."));
				} else {
					final String relative = "existent.txt";
					Files.createDirectory(reference);
					Files.write(reference.resolve(relative), "Hé !\r\n\n\r".getBytes(StandardCharsets.ISO_8859_1));
					final ImmutableList<String> read = Try.of(() -> instance.getContentUsingIso88591Charset(relative))
							.getOrElse(ImmutableList.of());
					gradeBuilder.put(LocalCriterion.CONTENT_READS_FROM_JIMFS_USING_8859,
							Mark.binary(read != null && read.equals(ImmutableList.of("Hé !", "", ""))));
				}
			}
		}

		{
			final StringFilesUtils instance = factory.get();
			final Path reference = Path.of("fold", "subf");
			Try.of(() -> instance.setReferenceFolder(reference));
			final Try<Path> pathRelativeToReference = Try
					.of(() -> Path.of(instance.getPathRelativeToReference("fold/stuff.txt")));
			final Try<Path> pathToCurrentRelativeToReference = Try
					.of(() -> Path.of(instance.getPathRelativeToReference("")));
			final boolean test1 = pathRelativeToReference.isSuccess()
					&& pathRelativeToReference.get().normalize().equals(Path.of("..", "stuff.txt"));
			final boolean test2 = pathToCurrentRelativeToReference.isSuccess()
					&& pathToCurrentRelativeToReference.get().normalize().equals(Path.of("..", ".."));
			gradeBuilder.put(LocalCriterion.RELATIVE_TO_CURRENT_TO_RELATIVE_TO_REFERENCE,
					Mark.given(Booleans.countTrue(test1, test2) / 2d, ""));
		}
		final ImmutableMap<Criterion, IGrade> grade = gradeBuilder.build();

		return WeightingGrade.from(grade, Maps.toMap(grade.keySet(), c -> 1d));
	}

}
