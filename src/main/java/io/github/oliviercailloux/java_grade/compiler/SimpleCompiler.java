package io.github.oliviercailloux.java_grade.compiler;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
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

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.context.FilesSource;

public class SimpleCompiler {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCompiler.class);

	public static JavaFileObject asJavaSource(Path srcPath, FileContent content) {
		return new JavaSourceFromString(srcPath.relativize(content.getPath()).toString(), content.getContent());
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Collection<? extends JavaFileObject> srcToCompile,
			Collection<Path> cp) {
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

		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
			final StringWriter compilationOutputReceiver = new StringWriter();
			compiler.getTask(compilationOutputReceiver, fileManager, diagnostics, cpOptions, null, srcToCompile).call();
			final String compilationOutput = compilationOutputReceiver.toString();
			if (!compilationOutput.isEmpty()) {
				throw new UnsupportedOperationException();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		LOGGER.debug("Compiling with {}: {}.", cpOptions, diagnostics.getDiagnostics());
		return diagnostics.getDiagnostics();
	}

	public static List<Diagnostic<? extends JavaFileObject>> compile(Path srcPath, FilesSource sources,
			Collection<Path> cp) {
		final ImmutableList<JavaFileObject> javaSources = sources.asFileContents().stream()
				.map((fc) -> asJavaSource(srcPath, fc)).collect(ImmutableList.toImmutableList());
		return compile(javaSources, cp);
	}

}
