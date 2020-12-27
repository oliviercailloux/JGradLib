package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

/**
 * Just a few tests to examine the behavior of the default file system
 * implementation.
 */
public class DefaultFsTests {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFsTests.class);

	@Test
	void testListLinks() throws Exception {
		final ImmutableSet<Path> content = Files.list(Path.of("/var/")).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Content: {}.", content);
		final ImmutableSet<Path> locks;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("/var/"),
				f -> f.getFileName().toString().equals("lock"))) {
			locks = ImmutableSet.copyOf(stream);
		}
		final Path lock = Iterables.getOnlyElement(locks);
		LOGGER.info("Lock: {}.", lock);
		assertTrue(Files.isSymbolicLink(lock));
	}

	@Test
	void testDefault() throws Exception {
		{
			assertTrue(Path.of("", "/stuff").isAbsolute());
			assertEquals("dir/sub", Path.of("dir", "/sub").toString());
		}

		{
			/**
			 * File system created by the provider (but not by invoking explicitly new using
			 * Uri), and is then available through get. In violation of the getFS contract
			 * (or it should be taken to mean that the invocation of new using Uri can be
			 * implicit, that is, not done by the user, but then this restriction makes no
			 * sense).
			 */
			@SuppressWarnings("resource")
			final FileSystem def = FileSystems.getDefault();
			final URI defaultUri = URI.create("file:/");
			assertEquals(def, def.provider().getFileSystem(defaultUri));
		}

		{
			final Path root = FileSystems.getDefault().getRootDirectories().iterator().next();
			assertDoesNotThrow(() -> Files.newByteChannel(root));
			assertThrows(IOException.class, () -> Files.readString(root));
		}

		/**
		 * Judging from
		 * https://github.com/openjdk/jdk/tree/master/src/java.base/windows/classes/sun/nio/fs,
		 * it seems to me that \ is a root component only relative path, and hence
		 * getFileName on it returns null. Unfortunately, I can’t check this with Jimfs.
		 */
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.windows())) {
			final Path cBackslash = jimFs.getPath("C:\\");
			assertEquals("C:\\", cBackslash.toString());
			assertEquals(0, cBackslash.getNameCount());
			assertEquals("C:\\", cBackslash.getRoot().toString());

			final Path somePath = jimFs.getPath("C:\\some\\path\\");
			assertEquals("C:\\some\\path", somePath.toString());
			assertTrue(somePath.toUri().toString().endsWith("/C:/some/path"), "" + somePath.toUri());

			/** Unsupported unser Jimfs. */
			assertThrows(InvalidPathException.class, () -> jimFs.getPath("\\"));
		}

		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			assertEquals("a/a/a", jimFs.getPath("a", "/", "/", "a", "//a").toString());
			assertEquals("/b", jimFs.getPath("a").resolve("//b").toString());
		}

		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path root = jimFs.getPath("/");
			assertEquals("/", root.toString());
			assertEquals(0, root.getNameCount());
			assertEquals("/", root.getRoot().toString());
		}
		/**
		 * That’s how they test OS:
		 * https://github.com/openjdk/jdk/blob/master/test/jdk/java/nio/file/Path/PathOps.java.
		 */
		if (!System.getProperty("os.name").startsWith("Windows")) {
			final Path root = Path.of("/");
			assertEquals("/", root.toString());
			assertEquals(0, root.getNameCount());
			assertNull(root.getFileName());
			assertTrue(Files.isDirectory(root));
			assertEquals("/", root.getRoot().toString());

			final Path doubleSlash = Path.of("brown//fox");
			assertEquals(2, doubleSlash.getNameCount());

			final Path testEmpty = Path.of("", "brown", "", "fox", "");
			assertEquals(2, testEmpty.getNameCount());

			final Path empty = Path.of("");
			assertFalse(empty.isAbsolute());
		}
		{
			final Path space = Path.of(" ");
			assertNull(space.getRoot());
			final Path empty = Path.of("");
			assertNull(empty.getRoot());
			final Path slash = Path.of("/");
			assertNotNull(slash.getRoot());
			final Path a = Path.of("a");
			assertNull(a.getRoot());
			final ImmutableList<Path> expected = ImmutableList.of(empty, space, slash, a);
			final ArrayList<Path> paths = new ArrayList<>(expected);
			Collections.sort(paths);
			assertEquals(expected, paths);
		}
		final URI uriTest = new URI("scheme:/some/path//refs/heads/master//internal/path");
		assertEquals("scheme:/some/path//refs/heads/master//internal/path", uriTest.toString());
		assertEquals("scheme:/some/path/refs/heads/master/internal/path", uriTest.normalize().toString());
	}

	@Test
	void testStartsWith() throws Exception {
		final Path root = FileSystems.getDefault().getRootDirectories().iterator().next();
		assertFalse(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root.resolve(Path.of("dir", "p"))));
		assertTrue(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root.resolve(Path.of("dir"))));
		assertTrue(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root));
		assertFalse(Path.of("dir/ploum.txt").startsWith(Path.of("dir/p")));
		assertTrue(Path.of("/dir").startsWith(Path.of("/")));
	}

}
