package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

/**
 * Just a few tests to examine the behavior of the default file system
 * implementation.
 */
public class DefaultFsTests {

	@Test
	void testDefault() throws Exception {
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

}
