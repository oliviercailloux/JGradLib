package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.BasicCompiler;
import io.github.oliviercailloux.grade.utils.LogCaptor;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TConsumer;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResultExt;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.bytecode.MyCompiler;
import io.github.oliviercailloux.vexam.Various;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraderVarious implements CodeGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderVarious.class);

	public static final String PREFIX = "various";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2022-06-22T14:20:00+02:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.025d;

	public static void main(String[] args) throws Exception {
		final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
//				.getRetrievingByPrefixAndFiltering(PREFIX, "");
				.getRetrievingByPrefix(PREFIX);
		final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader.given(() -> fetcher);

		final GraderVarious grader = new GraderVarious();
		final MyCompiler delegate = new BasicCompiler();
		final MavenCodeGrader<RuntimeException> m = MavenCodeGrader.complex(grader, UncheckedIOException::new, false,
				new MyCompiler() {
					@Override
					public CompilationResultExt compile(Path compiledDir, Set<Path> javaPaths) throws IOException {
						LOGGER.info("Compiling {} to {}.", javaPaths, compiledDir);
						final ImmutableSet<GitPath> gitPaths = javaPaths.stream().map(p -> (GitPath) p)
								.collect(ImmutableSet.toImmutableSet());
						final String root = gitPaths.stream().map(GitPath::getRoot).map(GitPathRoot::getStaticCommitId)
								.map(ObjectId::getName).distinct().collect(MoreCollectors.onlyElement());
						LOGGER.info("Root: {}.", root);
						if (!root.equals("fc6a3c6f9864eccd9fa53d0c8e3cb70704d4835e")) {
							return delegate.compile(compiledDir, javaPaths);
						}
						copyDirectory(Path.of("/tmp/manual-compilation"), compiledDir);
						/* NB problem with nb of warnings here. */
						return CompilationResultExt.compiled("", 2);
					}
				});

		batchGrader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(6), GitFsGraderUsingLast.using(m), USER_WEIGHT,
				Path.of("grades " + PREFIX), PREFIX + Instant.now().atZone(DEADLINE.getZone()));
		grader.close();
		LOGGER.info("Done, closed.");
	}

	public static void copyDirectory(Path sourceDirectoryLocation, Path destinationDirectoryLocation)
			throws IOException {
		Files.walk(sourceDirectoryLocation).forEach(source -> {
			final Path destination = destinationDirectoryLocation.resolve(sourceDirectoryLocation.relativize(source));
			LOGGER.info("Copying from {} to {}.", source, destination);
			try {
				Files.copy(source, destination);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private static final Criterion C_LOG = Criterion.given("Logging");
	private static final Criterion C_POLITE = Criterion.given("Politeness");
	private static final Criterion C_ONE_ONE = Criterion.given("One one");
	private static final Criterion C_ELEVEN_ONES = Criterion.given("Eleven ones");
	private static final Criterion C_READ = Criterion.given("Read");
	private static final Criterion C_READ_WITH_EOL = Criterion.given("Read with EOL");
	private static final Criterion C_READ_ALL_ONE = Criterion.given("Read all one");
	private static final Criterion C_READ_ALL_FIVE = Criterion.given("Read all five");

	private final ExecutorService executors;

	private final LogCaptor logCaptor;

	public GraderVarious() {
		executors = Executors.newCachedThreadPool();
		logCaptor = LogCaptor.redirecting(Various.class.getPackageName());
	}

	private TryCatchAll<Various> newInstance(Instanciator instanciator) {
		final TryCatchAll<Various> tryTarget = TryCatchAll.get(() -> instanciator.getInstanceOrThrow(Various.class));
		final TryCatchAll<Various> instance = tryTarget.andApply(
				target -> SimpleTimeLimiter.create(executors).newProxy(target, Various.class, Duration.ofSeconds(5)));
		return instance;
	}

	@Override
	public MarksTree gradeCode(Instanciator instanciator) {
		final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();

		final TryCatchAll<Various> v0 = newInstance(instanciator);
		final String invocationErrorStr = v0.map(r -> "", c -> "Invocation failed: %s".formatted(c));
		if (!invocationErrorStr.isEmpty()) {
			return Mark.zero(invocationErrorStr);
		}

		{
			final TryCatchAll<Various> v = newInstance(instanciator);
			final int before = logCaptor.getEvents().size();
			TConsumer<? super Various, ?> consumer = Various::log;
			final TryCatchAll<Various> got = v.andConsume(consumer);
			final int after = logCaptor.getEvents().size();
			final boolean pass = after > before;
			builder.put(C_LOG, Mark.binary(pass, "", got.map(r -> "Nothing logged", c -> "Obtained %s".formatted(c))));
		}

		{
			final TryCatchAll<Various> v = newInstance(instanciator);
			final TryCatchAll<String> got = v.andApply(Various::bePolite);
			final boolean pass = got.map(r -> r.equals("Hello,  everybody!"), c -> false);

			builder.put(C_POLITE, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		}

		{
			final TryCatchAll<Various> v = newInstance(instanciator);
			final TryCatchAll<String> got = v.andApply(Various::ones);
			final boolean pass = got.map(r -> r.equals("1"), c -> false);

			builder.put(C_ONE_ONE, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		}

		{
			final TryCatchAll<Various> v = newInstance(instanciator);
			TryCatchAll<String> got = v.andApply(Various::ones);
			for (int i = 1; i < 11; ++i) {
				got = v.andApply(Various::ones);
			}
			final boolean pass = got
					.map(r -> r.equals(Stream.generate(() -> "1").limit(11).collect(Collectors.joining())), c -> false);

			builder.put(C_ELEVEN_ONES, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		}

		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path dir = fs.getPath("something");
			final Path path = dir.resolve("second.txt");
			final String content = "some   content";
			Files.createDirectories(dir);
			Files.writeString(path, content);
			verify(Files.readString(path).equals(content));
			final TryCatchAll<Various> v = newInstance(instanciator);
			v.andApply(i -> i.read(path));
			final TryCatchAll<String> got = v.andApply(i -> i.read(path));
			final boolean pass = got.map(r -> r.equals(content), c -> false);

			builder.put(C_READ, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path dir = fs.getPath("something");
			final Path path = dir.resolve("second.txt");
			final String content = "\n\n  some\n \n  content\n";
			Files.createDirectories(dir);
			Files.writeString(path, content);
			verify(Files.readString(path).equals(content));
			final TryCatchAll<Various> v = newInstance(instanciator);
			v.andApply(i -> i.read(path));
			final TryCatchAll<String> got = v.andApply(i -> i.read(path));
			final boolean pass = got.map(r -> r.equals(content), c -> false);

			builder.put(C_READ_WITH_EOL, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path dir = fs.getPath("something");
			final Path path = dir.resolve("1.txt");
			final String content = "some, , content";
			Files.createDirectories(dir);
			Files.writeString(path, content);
			final TryCatchAll<Various> v = newInstance(instanciator);
			final TryCatchAll<Various> vAdded = v.andConsume(i -> i.addPath(path));
			final TryCatchAll<String> got = vAdded.andApply(Various::readAll);
			final boolean pass = got.map(r -> r.equals(content), c -> false);

			builder.put(C_READ_ALL_ONE, Mark.binary(pass, "", "Obtained %s".formatted(got)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path dir = fs.getPath("something");
			Files.createDirectories(dir);
			final Path path1 = dir.resolve("1.txt");
			final Path path2 = dir.resolve("2.txt");
			final String content1 = "some, , content 1";
			final String content2 = "some, , content 2";
			Files.writeString(path1, content1);
			Files.writeString(path2, content2);
			final TryCatchAll<Various> v = newInstance(instanciator);
			final TryCatchAll<Various> vAdded = v.andConsume(i -> i.addPath(path1)).andConsume(i -> i.addPath(path2))
					.andConsume(i -> i.addPath(path2)).andConsume(i -> i.addPath(path2))
					.andConsume(i -> i.addPath(path2));
			final TryCatchAll<String> got = vAdded.andApply(Various::readAll);
			final String expected = content1 + Stream.generate(() -> content2).limit(4).collect(Collectors.joining());
			final boolean pass = got.map(r -> r.equals(expected), c -> false);

			builder.put(C_READ_ALL_FIVE, Mark.binary(pass, "", "Obtained %s.".formatted(got)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return MarksTree.composite(builder.build());
	}

	@Override
	public GradeAggregator getCodeAggregator() {
//		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
//		builder.put(C_LOG, 2.5d);
//		builder.put(C_POLITE, 2.5d);
//		builder.put(C_ONE_ONE, 2.5d);
//		builder.put(C_ELEVEN_ONES, 2.5d);
//		builder.put(C_READ, 2.5d);
//		builder.put(C_READ_WITH_EOL, 2.25d);
//		builder.put(C_READ_ALL_ONE, 2.5d);
//		builder.put(C_READ_ALL_FIVE, 2.25d);
//		return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		return GradeAggregator.owa(ImmutableList.of(4d, 4d, 3.5d, 3d, 2.0d, 1.5d, 1.0d, 0.5d));
	}

	public void close() {
		executors.shutdownNow();
		logCaptor.close();
	}
}
