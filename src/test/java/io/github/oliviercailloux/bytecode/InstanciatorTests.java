package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;

public class InstanciatorTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(InstanciatorTests.class);

	@Test
	void testGetInstance() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("MyIdentityFunction.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader = new URLClassLoader(new URL[] { url }, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstance").isPresent());
			}
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

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath1, sourcePath2));
			final Path destSourceWithNoWarnings = work.resolve(thisPackage.getName().replace('.', '/'))
					.resolve("SourceWithNoWarnings.class");
//			Files.delete(destSourceWithNoWarnings);
			assertTrue(Files.isRegularFile(destSourceWithNoWarnings));
			final URL url = work.toUri().toURL();
			try (URLClassLoader loader = new URLClassLoader(new URL[] { url }, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
				@SuppressWarnings("rawtypes")
				final Optional<Function> functionOpt = instanciator.getInstance(Function.class, "newInstance");
				assertTrue(functionOpt.isPresent());

				final Object ret = assertDoesNotThrow(() -> functionOpt.get().apply("t"));
				LOGGER.debug("Ret: {}.", ret);
			}
		}
	}

	@Test
	void testInstanceThrowing() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("MyIdentityFunctionThrowing.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader = new URLClassLoader(new URL[] { url }, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(Function.class, "newInstance").isEmpty());
				final ReflectiveOperationException lastException = instanciator.getLastException();
				LOGGER.debug("Last exc:", lastException);
				assertNotNull(lastException);
			}
		}
	}

}
