package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;
import static io.github.oliviercailloux.exceptions.Unchecker.URI_UNCHECKER;

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
import java.util.function.Function;
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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.context.FilesSource;

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
public class SimpleCompiler {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCompiler.class);

	public static SimpleCompiler throwing(List<Path> cp, Path destDir) {
		return new SimpleCompiler(cp, destDir, true);
	}

	public static JavaFileObject asJavaSource(Path srcPath, FileContent content) {
		return new JavaSourceFromString(srcPath.relativize(content.getPath()).toString(), content.getContent());
	}

	public static JavaFileObject asJavaSource(String name, Path path) {
		final String content = IO_UNCHECKER.getUsing(() -> Files.readString(path));
		return asJavaSource(name, content);
	}

	public static JavaFileObject asJavaSource(String name, String content) {
		return new JavaSourceFromString(name, content);
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Iterable<? extends JavaFileObject> srcToCompile,
			Collection<Path> cp) {
		return compile(srcToCompile, cp, Path.of("."));
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Iterable<? extends JavaFileObject> srcToCompile,
			Collection<Path> cp, Path destDir) {
		/**
		 * Compiler throws if asked to compile no source (even though the doc seems to
		 * allow it).
		 */
		if (!srcToCompile.iterator().hasNext()) {
			return ImmutableList.of();
		}

		return compile(m -> srcToCompile, cp, Optional.of(destDir));
	}

	public static List<Diagnostic<? extends JavaFileObject>> compileFromPaths(Iterable<Path> srcToCompile,
			Collection<Path> cp) {
		/**
		 * Compiler throws if asked to compile no source (even though the doc seems to
		 * allow it).
		 */
		if (!srcToCompile.iterator().hasNext()) {
			return ImmutableList.of();
		}

		return compile(m -> m.getJavaFileObjectsFromPaths(srcToCompile), cp, Optional.empty());
	}

	public static ImmutableList<Diagnostic<? extends JavaFileObject>> compileFromPaths(Iterable<Path> srcToCompile,
			Collection<Path> cp, Path destDir) {
		/**
		 * Compiler throws if asked to compile no source (even though the doc seems to
		 * allow it).
		 */
		if (!srcToCompile.iterator().hasNext()) {
			return ImmutableList.of();
		}

		return compile(m -> m.getJavaFileObjectsFromPaths(srcToCompile), cp, Optional.of(destDir));
	}

	private static ImmutableList<Diagnostic<? extends JavaFileObject>> compile(
			Function<StandardJavaFileManager, Iterable<? extends JavaFileObject>> fctSrcToCompile, Collection<Path> cp,
			Optional<Path> destDir) {

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
			 * This fails if the paths do not refer to File instances, because
			 * JavacFileManager#getClassLoader(Location location) calls getLocation, which
			 * tries to return File instances, instead of getLocationAsPaths(Location
			 * location).
			 */
			fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, cp);
			if (destDir.isPresent()) {
				fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, ImmutableSet.of(destDir.get()));
			}
			final Iterable<? extends JavaFileObject> srcToCompile = fctSrcToCompile.apply(fileManager);
			final StringWriter compilationOutputReceiver = new StringWriter();
			compiled = compiler.getTask(compilationOutputReceiver, fileManager, diagnosticCollector, ImmutableList.of(),
					null, srcToCompile).call();
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
			/**
			 * TODO check here that the file gets created; requires to simplify the
			 * parameters so that we get the paths of the source.
			 */
		}
		return ImmutableList.copyOf(diagnostics);
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Path srcPath, FilesSource sources,
			Collection<Path> cp) {
		final ImmutableList<JavaFileObject> javaSources = sources.asFileContents().stream()
				.map((fc) -> asJavaSource(srcPath, fc)).collect(ImmutableList.toImmutableList());
		return compile(javaSources, cp, Path.of("."));
	}

	public static CompilationResult eclipseCompile(Path target) {
		return eclipseCompile(ImmutableList.of(Path.of(".")), target);
	}

	/**
	 * Note that this will fail if the properties file is in a resource, not in a
	 * reachable file.
	 *
	 * @see https://help.eclipse.org/2020-03/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Ftasks%2Ftask-using_batch_compiler.htm
	 */
	public static CompilationResult eclipseCompile(List<Path> classPath, Path target) {
		return eclipseCompile(classPath, target, true);
	}

	public static CompilationResult eclipseCompile(List<Path> classPath, Path target, boolean useStrictWarnings) {
		checkArgument(Files.exists(target));

		final StringWriter out = new StringWriter();
		final StringWriter err = new StringWriter();

		final ImmutableList.Builder<String> builder = ImmutableList.builder();
		builder.add("--release", "11");
		if (useStrictWarnings) {
			/** Could instead use options such as "-warn:+allDeadCode,allDeprecation…". */
			final URL propertiesUrl = SimpleCompiler.class.getResource("Eclipse-prefs.epf");
			checkState(propertiesUrl != null);
			final Path propertiesPath = Path.of(URI_UNCHECKER.getUsing(() -> propertiesUrl.toURI()));
			checkState(Files.exists(propertiesPath));
			builder.add("-properties", propertiesPath.toString());
		}
		builder.add("-classpath",
				classPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		builder.add(target.toString());
		final ImmutableList<String> args = builder.build();

		final boolean compiled = BatchCompiler.compile(args.toArray(new String[args.size()]), new PrintWriter(out),
				new PrintWriter(err), null);

		LOGGER.debug("Compiled with output: {}, error: {}.", out, err);
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

	private ImmutableList<Path> cp;
	private boolean throwing;
	private Path destDir;

	private SimpleCompiler(List<Path> cp, Path destDir, boolean throwing) {
		this.cp = ImmutableList.copyOf(cp);
		this.destDir = checkNotNull(destDir);
		this.throwing = throwing;
	}

	public ImmutableList<Diagnostic<? extends JavaFileObject>> compile(Iterable<Path> srcToCompile) {
		final ImmutableList<Diagnostic<? extends JavaFileObject>> output = SimpleCompiler.compileFromPaths(srcToCompile,
				cp, destDir);
		if (throwing) {
			verify(output.isEmpty(), output.toString());
		}
		return output;
	}

}
