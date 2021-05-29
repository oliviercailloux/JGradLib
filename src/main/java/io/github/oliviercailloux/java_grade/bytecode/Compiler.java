package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;
import io.github.classgraph.ClassGraph;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In the below list, “checked” means that I checked everything there and
 * extracted the links to build this list, except that I didn’t check details of
 * short posts such as including only two classes, which are too short to
 * bother.
 * <ul>
 * <li>https://github.com/kite-sdk/kite/tree/master/kite-morphlines/kite-morphlines-core/src/main/java/org/kitesdk/morphline/scriptengine/java
 * old but seems correct quality code; though bug in method header which inverts
 * parameters. Kite is a set of libraries, tools, examples, and documentation
 * focused on making it easier to build systems on top of the Hadoop
 * ecosystem.</li>
 * <li>http://stackoverflow.com/a/33963977 (an answer to q 12173294, link
 * below): extends
 * http://javapracs.blogspot.com/2011/06/dynamic-in-memory-compilation-using.html
 * by Rekha Kumari, provides multiple classes compilation. MOST PROMISING?</li>
 * <li>http://pfmiles.github.io/blog/dynamic-java/ in chinese, could be serious?
 * (cited by Seems original.</li>
 * <li>http://www.soulmachine.me/blog/2015/07/22/compile-and-run-java-source-code-in-memory/
 * references Kite but doesn’t say how it differs from Kite. Seems like copied
 * from Kite, basically. TO EXPLORE</li>
 * <li>https://github.com/trung/InMemoryJavaCompiler (not maintained since
 * end-2017, two years ago) TO EXPLORE LAST?</li>
 * <li>JANINO https://janino-compiler.github.io/ implements a Java compiler,
 * does not reuse</li>
 * <li>https://github.com/OpenHFT/Java-Runtime-Compiler by Peter Lawrey (most
 * maintained, but doesn’t seem OO)</li>
 * <li>https://stackoverflow.com/questions/3447359/how-to-provide-an-interface-to-javacompiler-when-compiling-a-source-file-dynamic
 * checked</li>
 * <li>https://stackoverflow.com/questions/12173294/compile-code-fully-in-memory-with-javax-tools-javacompiler
 * →</li>
 * <li>https://stackoverflow.com/questions/21544446/how-do-you-dynamically-compile-and-load-external-java-classes?noredirect=1&lq=1
 * checked</li>
 * <li>https://stackoverflow.com/questions/31599427/how-to-compile-and-run-java-source-code-in-memory?noredirect=1&lq=1
 * checked</li>
 * <li>https://stackoverflow.com/questions/2946338/how-do-i-programmatically-compile-and-instantiate-a-java-class?noredirect=1&lq=1
 * checked</li>
 * <li>https://stackoverflow.com/questions/274474/how-do-i-use-jdk6-toolprovider-and-javacompiler-with-the-context-classloader?noredirect=1&lq=1
 * checked</li>
 * <li>https://stackoverflow.com/questions/2315719/using-javax-tools-toolprovider-from-a-custom-classloader?noredirect=1&lq=1
 * advanced discussion, perhaps worth reading? Though the bug report seems
 * mostly unrelated and might be just a misunderstanding (according to the
 * reply).</li>
 * <li>https://stackoverflow.com/questions/37822818/how-do-i-use-dependencies-only-available-in-memory-with-javax-tools-javacompiler?noredirect=1&lq=1
 * checked</li>
 * </ul>
 *
 * @author Olivier Cailloux
 *
 */
public class Compiler {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Compiler.class);

	public static Compiler tolerant(List<Path> cp, Path destDir) {
		return new Compiler(cp, destDir, true);
	}

	public static Compiler intolerant(List<Path> cp, Path destDir) {
		return new Compiler(cp, destDir, false);
	}

	public static ImmutableList<Diagnostic<? extends JavaFileObject>> compile(Collection<Path> classPath, Path destDir,
			Collection<Path> sources) {
		/**
		 * Compiler throws if asked to compile no source (even though the doc seems to
		 * allow it).
		 */
		if (!sources.iterator().hasNext()) {
			return ImmutableList.of();
		}

		return compile(classPath, sources, Optional.of(destDir));
	}

	private static ImmutableList<Diagnostic<? extends JavaFileObject>> compile(Collection<Path> classPath,
			Collection<Path> sources, Optional<Path> destDir) {
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		final boolean compiled;
		final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
		/**
		 * API about diagnostics is unclear, but from the source code of JavacTool, it
		 * seems like the second one (in the #getTask arguments) overrides the first one
		 * (in the getStandardFileManager arguments).
		 */
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null)) {
			/**
			 * Have to set explicitly the annotation processor path, otherwise, the
			 * initialization of annotation processing (involving #getClassLoader) uses the
			 * class path, which fails if the paths do not refer to File instances, because
			 * JavacFileManager#getClassLoader(Location location) calls getLocation, which
			 * tries to return File instances, instead of getLocationAsPaths(Location
			 * location). See CompilerTests#testBugJdk(). I got an email on the 10th of
			 * March, 2021, stating that the incident is fixed in https://jdk.java.net/16/.
			 * I have not checked.
			 * https://github.com/openjdk/jdk/blob/master/src/jdk.compiler/share/classes/com/sun/tools/javac/file/JavacFileManager.java#L744
			 */
			fileManager.setLocationFromPaths(StandardLocation.ANNOTATION_PROCESSOR_PATH, ImmutableList.of());
			fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
			if (destDir.isPresent()) {
				fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, ImmutableSet.of(destDir.get()));
			}
			final Iterable<? extends JavaFileObject> srcToCompileObjs = fileManager
					.getJavaFileObjectsFromPaths(sources);
			final StringWriter compilationOutputReceiver = new StringWriter();
			compiled = compiler.getTask(compilationOutputReceiver, fileManager, diagnosticCollector, ImmutableList.of(),
					null, srcToCompileObjs).call();
			final String compilationOutput = compilationOutputReceiver.toString();
			if (!compilationOutput.isEmpty()) {
				throw new UnsupportedOperationException();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		final List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
		LOGGER.debug("Compiled and got: {}.", diagnostics);
		verify(compiled == diagnostics.isEmpty());
		if (compiled) {
//			SourceScanner.scan()
			/**
			 * Seems impossible to check that the file gets created, because seems
			 * impossible to (elegantly) obtain the package name or output exact location of
			 * the file.
			 */
		}
		return ImmutableList.copyOf(diagnostics);
	}

	/**
	 * Note that this will fail if the properties file is in a resource, not in a
	 * reachable file.
	 *
	 * @throws IOException
	 *
	 * @see https://help.eclipse.org/2020-03/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Ftasks%2Ftask-using_batch_compiler.htm
	 */
	public static CompilationResult eclipseCompile(List<Path> classPath, Set<Path> targets) throws IOException {
		return eclipseCompile(classPath, targets, true);
	}

	public static CompilationResult eclipseCompileUsingOurClasspath(Set<Path> targets, Path destinationDir)
			throws IOException {
		final List<File> classpath = new ClassGraph().getClasspathFiles();
		final ImmutableSet<Path> classpathPaths = classpath.stream().map(File::toPath)
				.collect(ImmutableSet.toImmutableSet());
		return eclipseCompile(classpathPaths.asList(), targets, true, Optional.of(destinationDir));
	}

	public static CompilationResult eclipseCompile(List<Path> classPath, Set<Path> targets, boolean useStrictWarnings)
			throws IOException {
		return eclipseCompile(classPath, targets, useStrictWarnings, Optional.empty());
	}

	/**
	 * TODO org.eclipse.jdt.internal.compiler.batch.Main#performCompilation.
	 *
	 * @throws IOException
	 *
	 */
	public static CompilationResult eclipseCompile(List<Path> classPath, Set<Path> targets, boolean useStrictWarnings,
			Optional<Path> destination) throws IOException {
		// TODO what if targets is empty?
		checkArgument(targets.stream().allMatch(Files::exists));

		final StringWriter out = new StringWriter();
		final StringWriter err = new StringWriter();

		final ImmutableList.Builder<String> builder = ImmutableList.builder();
		{
			builder.add("--release", "11");
		}
		if (useStrictWarnings) {
			/** Could instead use options such as "-warn:+allDeadCode,allDeprecation…". */
			final URL propertiesUrl = Compiler.class.getResource("Eclipse-prefs.epf");
			checkState(propertiesUrl != null);
			final Path propertiesPath = Path.of(URI_UNCHECKER.getUsing(() -> propertiesUrl.toURI()));
			verify(Files.exists(propertiesPath));
			verify(propertiesPath.getFileSystem().provider().getScheme().equals("file"));
			builder.add("-properties", propertiesPath.toString());
		}
		checkArgument(classPath.stream().allMatch(p -> p.getFileSystem().provider().getScheme().equals("file")));
		{
			builder.add("-classpath",
					classPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		}
		if (destination.isPresent()) {
			final Path destinationPath = destination.get();
			checkArgument(destinationPath.getFileSystem().provider().getScheme().equals("file"));
			builder.add("-d", destinationPath.toString());
		}

		final boolean targetsAreFiles = targets.stream()
				.allMatch(p -> p.getFileSystem().provider().getScheme().equals("file"));
		final ImmutableSet<Path> effectiveTargets;
		final Optional<Path> toDelete;
		if (targetsAreFiles) {
			effectiveTargets = ImmutableSet.copyOf(targets);
			toDelete = Optional.empty();
		} else {
			final Path newStartPath = Files.createTempDirectory("sources");
			toDelete = Optional.of(newStartPath);
			final ImmutableMap<Path, Path> corrPaths = Maps.toMap(targets,
					p -> newStartPath.resolve(p.relativize(p.getRoot()).toString()));
			LOGGER.debug("Corr: {}.", corrPaths.values());
			CheckedStream.<Path, IOException>wrapping(targets.stream())
					.peek(p -> Files.createDirectories(corrPaths.get(p).getParent()))
					.forEach(p -> Files.copy(p, corrPaths.get(p)));
			effectiveTargets = corrPaths.values().stream().collect(ImmutableSet.toImmutableSet());
		}

		{
			effectiveTargets.stream().map(Path::toString).forEach(builder::add);
		}
		final ImmutableList<String> args = builder.build();

		final boolean compiled = BatchCompiler.compile(args.toArray(new String[args.size()]), new PrintWriter(out),
				new PrintWriter(err), null);

		LOGGER.debug("Compiled with output: {}, error: {}.", out, err);

		if (toDelete.isPresent()) {
			MoreFiles.deleteRecursively(toDelete.get());
		}

		return new CompilationResult(compiled, out.toString(), err.toString());
	}

	public static class CompilationResult {
		public boolean compiled;
		public String out;
		public String err;

		private CompilationResult(boolean compiled, String out, String err) {
			super();
			this.compiled = compiled;
			this.out = out;
			this.err = err;
			checkArgument(compiled == (countErrors() == 0), err);
		}

		public int countWarnings() {
			/** See https://github.com/google/guava/issues/877 */
			return Splitter.on("WARNING ").splitToStream(err).mapToInt(e -> 1).sum() - 1;
		}

		public int countErrors() {
			return Splitter.on("ERROR ").splitToStream(err).mapToInt(e -> 1).sum() - 1;
		}
	}

	private boolean tolerateFailure;
	private ImmutableList<Path> cp;
	private Path destDir;

	private Compiler(List<Path> cp, Path destDir, boolean tolerateFailure) {
		this.cp = ImmutableList.copyOf(cp);
		this.destDir = checkNotNull(destDir);
		this.tolerateFailure = tolerateFailure;
	}

	public ImmutableList<Diagnostic<? extends JavaFileObject>> compile(Collection<Path> srcToCompile) {
		final ImmutableList<Diagnostic<? extends JavaFileObject>> output = Compiler.compile(cp, destDir, srcToCompile);
		if (!tolerateFailure) {
			verify(output.isEmpty(), output.toString());
		}
		return output;
	}

}
