package io.github.oliviercailloux.java_grade.compiler;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.utils.Utils;

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

	public static JavaFileObject asJavaSource(Path srcPath, FileContent content) {
		return new JavaSourceFromString(srcPath.relativize(content.getPath()).toString(), content.getContent());
	}

	public static JavaFileObject asJavaSource(String name, Path path) {
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return asJavaSource(name, content);
	}

	public static JavaFileObject asJavaSource(String name, String content) {
		return new JavaSourceFromString(name, content);
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Collection<? extends JavaFileObject> srcToCompile,
			Collection<Path> cp) {
		return compile(srcToCompile, cp, Path.of("."));
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Collection<? extends JavaFileObject> srcToCompile,
			Collection<Path> cp, Path destDir) {
		/**
		 * Compiler throws if asked to compile no source (even though the doc seems to
		 * allow it).
		 */
		if (srcToCompile.isEmpty()) {
			return ImmutableList.of();
		}

		final ImmutableList<String> cpOptions;
		if (cp.size() >= 1) {
			cpOptions = ImmutableList.of("-classpath",
					cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		} else {
			cpOptions = ImmutableList.of();
		}
		final ImmutableList<String> dOptions = destDir.toString().equals(".") ? ImmutableList.of()
				: ImmutableList.of("-d", destDir.toString());

		final ImmutableList<String> options = Streams.concat(cpOptions.stream(), dOptions.stream())
				.collect(ImmutableList.toImmutableList());

		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
			final StringWriter compilationOutputReceiver = new StringWriter();
			compiler.getTask(compilationOutputReceiver, fileManager, diagnostics, options, null, srcToCompile).call();
			final String compilationOutput = compilationOutputReceiver.toString();
			if (!compilationOutput.isEmpty()) {
				throw new UnsupportedOperationException();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		LOGGER.debug("Compiling with {}: {}.", options, diagnostics.getDiagnostics());
		return diagnostics.getDiagnostics();
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Path srcPath, FilesSource sources,
			Collection<Path> cp) {
		final ImmutableList<JavaFileObject> javaSources = sources.asFileContents().stream()
				.map((fc) -> asJavaSource(srcPath, fc)).collect(ImmutableList.toImmutableList());
		return compile(javaSources, cp, Path.of("."));
	}

}
