package io.github.oliviercailloux.java_grade.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.java_grade.bytecode.SimpleCompiler.CompilationResult;

class CompilerTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CompilerTests.class);

	@Test
	void testWarn() throws Exception {
		final Path source = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
		assertTrue(Files.exists(source), source.toString());
		{
			final CompilationResult resultDefault = SimpleCompiler.eclipseCompile(ImmutableList.of(Path.of(".")),
					source, false);
			assertTrue(resultDefault.compiled);
			/**
			 * Does not count the unnecessary type cast, the declared and unused throwing, …
			 */
			assertEquals(2, resultDefault.countWarnings());
		}
		{
			final CompilationResult result = SimpleCompiler.eclipseCompile(source);
			assertTrue(result.compiled);
			assertEquals(5, result.countWarnings());
		}
	}

	@Test
	void testNoWarn() throws Exception {
		final Path source = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		final CompilationResult result = SimpleCompiler.eclipseCompile(source);
		assertTrue(result.compiled);
		assertEquals(0, result.countWarnings());
	}

	@Test
	void testCompileSourceWithWarnings() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			final List<Diagnostic<? extends JavaFileObject>> diagnostics = SimpleCompiler
					.compileFromPaths(ImmutableList.of(sourcePath), ImmutableList.of(), work);

			assertEquals(ImmutableList.of(), diagnostics);

			final ImmutableSet<Path> files;
			try (Stream<Path> stream = Files.walk(work.toAbsolutePath())) {
				files = stream.filter(Files::isRegularFile).collect(ImmutableSet.toImmutableSet());
			}
			assertEquals(
					ImmutableSet.of(jimFs
							.getPath("/work/io/github/oliviercailloux/java_grade/bytecode/SourceWithWarnings.class")),
					files);
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

		final List<Diagnostic<? extends JavaFileObject>> diagnostics = SimpleCompiler
				.compileFromPaths(ImmutableList.of(sourcePath), ImmutableList.of());
		assertEquals(1, diagnostics.size());
		assertEquals(
				"io/github/oliviercailloux/java_grade/bytecode/AnotherName.java:5: error: class SourceWithWarnings is public, should be declared in a file named SourceWithWarnings.java\n"
						+ "public class SourceWithWarnings {\n" + "       ^",
				Iterables.getOnlyElement(diagnostics).toString());
	}
}
