package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewCompiler {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(NewCompiler.class);

	public static NewCompiler create() {
		return new NewCompiler();
	}

	private boolean tolerateFailure;
	private ImmutableList<Path> classPath;
	private ImmutableSet<Path> sourcePaths;
	private Path outputDirectory;

	private DiagnosticCollector<JavaFileObject> fileDiagnostics;

	private DiagnosticCollector<JavaFileObject> compilerDiagnostics;

	private final JavaCompiler compiler;

	private NewCompiler() {
		this.classPath = ImmutableList.of();
		this.outputDirectory = null;
		this.tolerateFailure = true;
		fileDiagnostics = null;
		compilerDiagnostics = null;
		compiler = new EclipseCompiler();
	}

	public ImmutableList<Path> getClassPath() {
		return classPath;
	}

	public NewCompiler setClassPath(List<Path> classPath) {
		this.classPath = ImmutableList.copyOf(classPath);
		return this;
	}

	public ImmutableSet<Path> getSourcePaths() {
		return sourcePaths;
	}

	public NewCompiler setSourcePaths(Iterable<Path> sourcePaths) {
		this.sourcePaths = ImmutableSet.copyOf(sourcePaths);
		return this;
	}

	public Optional<Path> getOutputDirectory() {
		return Optional.ofNullable(outputDirectory);
	}

	public NewCompiler setOutputDirectory(Path directory) {
		this.outputDirectory = directory;
		return this;
	}

	public NewCompiler setIntolerant() {
		tolerateFailure = false;
		return this;
	}

	public ImmutableList<Diagnostic<? extends JavaFileObject>> compile() throws IOException {
		prepare();
		final boolean compiled;
		try (StandardJavaFileManager fileManager = getFileManager()) {
			compiled = compile(fileManager);
		}
		final ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics = getDiagnostics();
		LOGGER.debug("Compiled and got: {}.", diagnostics);
		verify(compiled == diagnostics.isEmpty());

		if (!tolerateFailure) {
			verify(diagnostics.isEmpty(), diagnostics.toString());
		}

		return diagnostics;
	}

	private void prepare() {
		fileDiagnostics = new DiagnosticCollector<>();
		compilerDiagnostics = new DiagnosticCollector<>();
	}

	private StandardJavaFileManager getFileManager() throws IOException {
		final StandardJavaFileManager fileManager =
				compiler.getStandardFileManager(fileDiagnostics, Locale.US, StandardCharsets.UTF_8);
		/**
		 * Have to set explicitly the annotation processor path, otherwise, the initialization of
		 * annotation processing (involving #getClassLoader) uses the class path, which fails if the
		 * paths do not refer to File instances, because JavacFileManager#getClassLoader(Location
		 * location) calls getLocation, which tries to return File instances, instead of
		 * getLocationAsPaths(Location location). See CompilerTests#testBugJdk(). I got an email on the
		 * 10th of March, 2021, stating that the incident is fixed in https://jdk.java.net/16/. I have
		 * not checked.
		 * https://github.com/openjdk/jdk/blob/master/src/jdk.compiler/share/classes/com/sun/tools/javac/file/JavacFileManager.java#L744
		 */
		fileManager.setLocationFromPaths(StandardLocation.ANNOTATION_PROCESSOR_PATH,
				ImmutableList.of());
		fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
		if (outputDirectory != null) {
			fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT,
					ImmutableSet.of(outputDirectory));
		}
		return fileManager;
	}

	private boolean compile(StandardJavaFileManager fileManager) {
		final StringWriter compilationOutputReceiver = new StringWriter();
		final boolean compiled =
				compiler
						.getTask(compilationOutputReceiver, fileManager, compilerDiagnostics,
								ImmutableList.of(), null, fileManager.getJavaFileObjectsFromPaths(sourcePaths))
						.call();
		final String compilationOutput = compilationOutputReceiver.toString();
		if (!compilationOutput.isEmpty()) {
			throw new UnsupportedOperationException(
					getDiagnostics().toString() + ";;" + compilationOutput);
		}
		return compiled;
	}

	private ImmutableList<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
		final ImmutableSet<Diagnostic<? extends JavaFileObject>> fD =
				ImmutableSet.copyOf(fileDiagnostics.getDiagnostics());
		final ImmutableSet<Diagnostic<? extends JavaFileObject>> cD =
				ImmutableSet.copyOf(compilerDiagnostics.getDiagnostics());
		final ImmutableSet<Diagnostic<? extends JavaFileObject>> inters =
				Sets.intersection(fD, cD).immutableCopy();
		if (!inters.isEmpty()) {
			throw new UnsupportedOperationException();
		}

		final ImmutableList.Builder<Diagnostic<? extends JavaFileObject>> builder =
				ImmutableList.builderWithExpectedSize(fD.size() + cD.size());
		builder.addAll(fD).addAll(cD);
		return builder.build();
	}
}
