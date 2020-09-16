package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;

class CompilerTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CompilerTests.class);

	@Test
	void testEclipseWarn() throws Exception {
		final Path source = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
		assertTrue(Files.exists(source), source.toString());
		{
			final CompilationResult resultDefault = Compiler.eclipseCompile(ImmutableList.of(Path.of(".")),
					ImmutableSet.of(source), false);
			assertTrue(resultDefault.compiled);
			/**
			 * Does not count the unnecessary type cast, the declared and unused throwing, …
			 */
			assertEquals(2, resultDefault.countWarnings());
		}
		{
			final CompilationResult result = Compiler.eclipseCompile(ImmutableList.of(Path.of(".")),
					ImmutableSet.of(source));
			assertTrue(result.compiled);
			assertEquals(5, result.countWarnings());
		}
	}

	@Test
	void testEclipseNoWarn() throws Exception {
		final Path source = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		final CompilationResult result = Compiler.eclipseCompile(ImmutableList.of(Path.of(".")),
				ImmutableSet.of(source));
		assertTrue(result.compiled);
		assertEquals(0, result.countWarnings());
	}

	@Test
	void testJdkWarningsDontWarn() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final ImmutableSet<Path> files;
			try (Stream<Path> stream = Files.walk(work.toAbsolutePath())) {
				files = stream.filter(Files::isRegularFile).collect(ImmutableSet.toImmutableSet());
			}
			assertEquals(ImmutableSet.of(work.resolve(getClass().getPackageName().replace('.', '/'))
					.resolve("SourceWithWarnings.class").toAbsolutePath()), files);
		}
	}

	@Test
	void testJdkWithCp() throws Exception {
		final Path sourceRequired = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		final Path sourceRequiring = Path.of(getClass().getResource("MyFunctionRequiring.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path cp = jimFs.getPath("cp");
			Files.createDirectories(cp);
			final Path main = jimFs.getPath("main");
			Files.createDirectories(main);

			Compiler.intolerant(ImmutableList.of(), cp).compile(ImmutableList.of(sourceRequired));
			assertThrows(VerifyException.class,
					() -> Compiler.intolerant(ImmutableList.of(), main).compile(ImmutableList.of(sourceRequiring)));
			Compiler.intolerant(ImmutableList.of(cp), main).compile(ImmutableList.of(sourceRequiring));

			final ImmutableSet<Path> files;
			try (Stream<Path> stream = Files.walk(main.toAbsolutePath())) {
				files = stream.filter(Files::isRegularFile).collect(ImmutableSet.toImmutableSet());
			}
			assertEquals(ImmutableSet.of(main.resolve(getClass().getPackageName().replace('.', '/'))
					.resolve("MyFunctionRequiring.class").toAbsolutePath()), files);
		}
	}

	@Test
	void testCompileIncorrectClassName() throws Exception {
		final Path work = Jimfs.newFileSystem(Configuration.unix()).getPath("");
		final Path sourceDir = work.resolve(getClass().getPackage().getName().replace('.', '/'));
		final Path sourcePath = sourceDir.resolve("AnotherName.java");
		{
			final String idFct = Files.readString(Path.of(getClass().getResource("SourceWithWarnings.java").toURI()));
			Files.createDirectories(sourceDir);
			Files.writeString(sourcePath, idFct);
		}

		final List<Diagnostic<? extends JavaFileObject>> diagnostics = Compiler.compile(ImmutableList.of(),
				Path.of("."), ImmutableSet.of(sourcePath));
		assertEquals(1, diagnostics.size());
		assertEquals(
				"io/github/oliviercailloux/bytecode/AnotherName.java:5: error: class SourceWithWarnings is public, should be declared in a file named SourceWithWarnings.java\n"
						+ "public class SourceWithWarnings {\n" + "       ^",
				Iterables.getOnlyElement(diagnostics).toString());
	}

	/**
	 * See comment in source of {@link Compiler#compile(…)}
	 */
	@Test
	void testBugJdk() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
			try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null,
					null)) {
				fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, ImmutableList.of(work));
				final Iterable<? extends JavaFileObject> srcToCompileObjs = fileManager
						.getJavaFileObjectsFromPaths(ImmutableList.of(sourcePath));
				final CompilationTask task = compiler.getTask(new StringWriter(), fileManager, diagnosticCollector,
						ImmutableList.of(), null, srcToCompileObjs);
				assertThrows(IllegalStateException.class, () -> task.call());
			}
		}
	}
}
