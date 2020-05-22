package io.github.oliviercailloux.java_grade.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

class InstanciatorTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(InstanciatorTests.class);

	@Test
	void testGetInstance() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("MyIdentityFunction.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			SimpleCompiler.throwing(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			final Instanciator instanciator = Instanciator.given(url, getClass().getClassLoader());
			assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
			assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
			assertTrue(instanciator.getInstance(Function.class, "newInstance").isPresent());
		}
	}

	@Test
	void testGetInstanceRequiring() throws Exception {
		final Path sourcePath1 = Path.of(getClass().getResource("MyFunctionRequiring.java").toURI());
		final Path sourcePath2 = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		final ClassLoader parent = getClass().getClassLoader();
		final Package thisPackage = getClass().getPackage();
		assertThrows(ClassNotFoundException.class, () -> parent.loadClass(thisPackage + ".MyFunctionRequiring"));
		assertThrows(ClassNotFoundException.class, () -> parent.loadClass(thisPackage + ".SourceWithNoWarnings"));
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			SimpleCompiler.throwing(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath1, sourcePath2));
			final Path destSourceWithNoWarnings = work
					.resolve("io.github.oliviercailloux.java_grade.bytecode".replace('.', '/'))
					.resolve("SourceWithNoWarnings.class");
//			Files.delete(destSourceWithNoWarnings);
			assertTrue(Files.isRegularFile(destSourceWithNoWarnings));
			final URL url = work.toUri().toURL();
			final Instanciator instanciator = Instanciator.given(url, parent);
			assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
			assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
			@SuppressWarnings("rawtypes")
			final Optional<Function> functionOpt = instanciator.getInstance(Function.class, "newInstance");
			assertTrue(functionOpt.isPresent());

			LOGGER.info("Applying function.");
			final Object ret = functionOpt.get().apply("t");
			LOGGER.info("Ret: {}.", ret);
			assertDoesNotThrow(() -> ret);
		}
	}

	@Test
	void testInstanceThrowing() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");
			final Path sourceDir = work.resolve(getClass().getPackage().getName().replace('.', '/'));
			final Path sourcePath = sourceDir.resolve("MyIdentityFunctionThrowing.java");
			{
				final String idFct = Files
						.readString(Path.of(getClass().getResource("MyIdentityFunctionThrowing.java").toURI()));
				Files.createDirectories(sourceDir);
				Files.writeString(sourcePath, idFct);
			}
			final List<Diagnostic<? extends JavaFileObject>> diagnostics = SimpleCompiler
					.compileFromPaths(ImmutableList.of(sourcePath), ImmutableList.of());
			assertEquals(ImmutableList.of(), diagnostics);

			final URL url = work.toUri().toURL();
			final Instanciator instanciator = Instanciator.given(url, getClass().getClassLoader());
			assertTrue(instanciator.getInstance(Function.class, "newInstance").isEmpty());
			final ReflectiveOperationException lastException = instanciator.getLastException();
			LOGGER.debug("Last exc:", lastException);
			assertNotNull(lastException);
		}
	}

}
