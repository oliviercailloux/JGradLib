package io.github.oliviercailloux.java_grade.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

class InstanciatorTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(InstanciatorTests.class);

	@Test
	void testGetInstance() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");
			final Path sourceDir = work.resolve(getClass().getPackage().getName().replace('.', '/'));
			final Path sourcePath = sourceDir.resolve("MyIdentityFunction.java");
			{
				final String idFct = Files
						.readString(Path.of(getClass().getResource("MyIdentityFunction.java").toURI()));
				Files.createDirectories(sourceDir);
				Files.writeString(sourcePath, idFct);
			}
			final List<Diagnostic<? extends JavaFileObject>> diagnostics = SimpleCompiler
					.compileFromPaths(ImmutableList.of(sourcePath), ImmutableList.of());
			assertEquals(ImmutableList.of(), diagnostics);

			final URL url = work.toUri().toURL();
			final Instanciator instanciator = Instanciator.given(url, getClass().getClassLoader());
			assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
			assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
			assertTrue(instanciator.getInstance(Function.class, "newInstance").isPresent());
		}
	}

	@Test
	void testClassgraph() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");
			final Path sourceDir = work.resolve(getClass().getPackage().getName().replace('.', '/'));
			final Path sourcePath = sourceDir.resolve("MyIdentityFunction.java");
			{
				final String idFct = Files
						.readString(Path.of(getClass().getResource("MyIdentityFunction.java").toURI()));
				Files.createDirectories(sourceDir);
				Files.writeString(sourcePath, idFct);
			}
			final List<Diagnostic<? extends JavaFileObject>> diagnostics = SimpleCompiler
					.compileFromPaths(ImmutableList.of(sourcePath), ImmutableList.of());
			assertEquals(ImmutableList.of(), diagnostics);

			final URL url = work.toUri().toURL();
			try (URLClassLoader child = new URLClassLoader(new URL[] { url }, getClass().getClassLoader())) {
				final ClassGraph graph = new ClassGraph().enableURLScheme(url.getProtocol()).overrideClassLoaders(child)
						.ignoreParentClassLoaders().enableAllInfo();
				LOGGER.debug("Scanning.");
				try (ScanResult scanResult = graph.scan()) {
					LOGGER.debug("Scanned.");
				}
			}
		}
	}

	@Test
	void testClassgraphEmpty() throws Exception {
		try (FileSystem memFs = Jimfs.newFileSystem()) {
			Path pathInMem = memFs.getPath("");

			URL url = pathInMem.toUri().toURL();
			try (URLClassLoader child = new URLClassLoader(new URL[] { url }, getClass().getClassLoader())) {
				LOGGER.debug("Scanning.");
				ClassGraph graph = new ClassGraph().enableURLScheme(url.getProtocol()).overrideClassLoaders(child)
						.ignoreParentClassLoaders().enableAllInfo();
				try (ScanResult scanResult = graph.scan()) {
					LOGGER.debug("Scanned.");
				}
			}
		}

	}

}
