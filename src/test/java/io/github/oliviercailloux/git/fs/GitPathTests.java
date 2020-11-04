package io.github.oliviercailloux.git.fs;

import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.JGit;

public class GitPathTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathTests.class);

	private static final GitRepoFileSystem GIT_FILE_SYSTEM = IO_UNCHECKER
			.getUsing(() -> GitDirFileSystem.given(Mockito.mock(GitFileSystemProvider.class), Path.of(".")));

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
		}

		final URI gitFs = new URI("gitjfs:/some/path//refs/heads/master//internal/path");
		assertEquals("gitjfs:/some/path//refs/heads/master//internal/path", gitFs.toString());
		assertEquals("gitjfs:/some/path//refs/heads/master//internal/path", gitFs.normalize().toString());
	}

	@Test
	void testBasics() throws Exception {
		final String revSpec = "master";
		final String dirAndFile = "";
		assertThrows(IllegalArgumentException.class, () -> getGitPath(revSpec, dirAndFile));
		assertThrows(IllegalArgumentException.class, () -> getGitPath("master", "ploum"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath("", "/"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath("", "/truc"));

		assertEquals("", getGitPath("", "").toString());
		assertEquals("ploum", getGitPath("", "ploum").toString());
		assertEquals("truc/chose", getGitPath("", "truc/chose").toString());
		assertEquals("master//", getGitPath("master", "/").toString());
		assertEquals("master//ploum", getGitPath("master", "/ploum").toString());
		assertEquals("master//truc/chose", getGitPath("master", "/truc/chose").toString());

		assertFalse(getGitPath("", "").isAbsolute());
		assertFalse(getGitPath("", "truc/chose").isAbsolute());
		assertTrue(getGitPath("master", "/").isAbsolute());
		assertTrue(getGitPath("master", "/truc/chose").isAbsolute());

		assertEquals("master//", getGitPath("master", "/").getRoot().toString());
		assertEquals("master//", getGitPath("master", "/truc/chose").getRoot().toString());
		assertEquals("master//", getGitPath("master", "/truc.txt").getRoot().toString());
		assertNull(getGitPath("", "truc.txt").getRoot());

		assertEquals(0, getGitPath("master", "/").getNameCount());
		assertEquals(1, getGitPath("master", "/truc.txt").getNameCount());
		assertEquals(2, getGitPath("master", "/truc/chose").getNameCount());
		assertEquals(1, getGitPath("", "").getNameCount());
		assertEquals(1, getGitPath("", "truc").getNameCount());
		assertEquals(2, getGitPath("", "truc/chose").getNameCount());

		assertEquals("", getGitPath("", "").getFileName().toString());
		assertEquals("chose", getGitPath("", "truc/chose").getFileName().toString());
		assertEquals("chose", getGitPath("master", "/truc/chose").getFileName().toString());
		assertNull(getGitPath("master", "/").getFileName());

		assertNull(getGitPath("master", "/").getParent());
		assertEquals("truc", getGitPath("", "truc/chose").getParent().toString());
		assertEquals("master//", getGitPath("master", "/truc").getParent().toString());
		assertEquals("master//truc", getGitPath("master", "/truc/chose").getParent().toString());

		assertEquals("", getGitPath("", "").resolve(getGitPath("", "")).toString());
		assertEquals("ploum.txt", getGitPath("", "").resolve(getGitPath("", "ploum.txt")).toString());
		assertEquals("master//", getGitPath("", "").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt", getGitPath("", "").resolve(getGitPath("master", "/ploum.txt")).toString());
		assertEquals("truc", getGitPath("", "truc").resolve(getGitPath("", "")).toString());
		assertEquals("truc/ploum.txt", getGitPath("", "truc").resolve(getGitPath("", "ploum.txt")).toString());
		assertEquals("master//", getGitPath("", "truc").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt",
				getGitPath("", "truc").resolve(getGitPath("master", "/ploum.txt")).toString());
		assertEquals("master//", getGitPath("master", "/").resolve(getGitPath("", "")).toString());
		assertEquals("master//ploum.txt", getGitPath("master", "/").resolve(getGitPath("", "ploum.txt")).toString());
		assertEquals("master//", getGitPath("master", "/").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt",
				getGitPath("master", "/").resolve(getGitPath("master", "/ploum.txt")).toString());
		assertEquals("master//", getGitPath("main", "/").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt",
				getGitPath("main", "/").resolve(getGitPath("master", "/ploum.txt")).toString());
		assertEquals("master//truc", getGitPath("master", "/truc").resolve(getGitPath("", "")).toString());
		assertEquals("master//truc/ploum.txt",
				getGitPath("master", "/truc").resolve(getGitPath("", "ploum.txt")).toString());
		assertEquals("master//", getGitPath("master", "/truc").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt",
				getGitPath("master", "/truc").resolve(getGitPath("master", "/ploum.txt")).toString());
		assertEquals("master//", getGitPath("main", "/truc").resolve(getGitPath("master", "/")).toString());
		assertEquals("master//ploum.txt",
				getGitPath("main", "/truc").resolve(getGitPath("master", "/ploum.txt")).toString());

		assertEquals("", getGitPath("", "").relativize(getGitPath("", "")).toString());
		assertEquals("chose", getGitPath("", "truc").relativize(getGitPath("", "truc/chose")).toString());
		assertEquals("../../stuff", getGitPath("", "truc/chose").relativize(getGitPath("", "stuff")).toString());
		assertEquals("../stuff", getGitPath("master", "/truc").relativize(getGitPath("master", "/stuff")).toString());
		assertEquals("", getGitPath("master", "/truc").relativize(getGitPath("master", "/truc")).toString());
		assertEquals("chose", getGitPath("master", "/truc").relativize(getGitPath("master", "/truc/chose")).toString());
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath("master", "/truc").relativize(getGitPath("", "/truc/chose")));
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath("", "/truc").relativize(getGitPath("master", "/truc/chose")));

		assertEquals("master//", getGitPath("master", "/").toAbsolutePath().toString());
		assertEquals("master//truc/chose", getGitPath("master", "/truc/chose").toAbsolutePath().toString());
		assertEquals("master//", getGitPath("", "").toAbsolutePath().toString());
		assertEquals("master//truc/chose", getGitPath("", "truc/chose").toAbsolutePath().toString());
		assertEquals("master//truc.txt", getGitPath("", "truc.txt").toAbsolutePath().toString());

		assertTrue(getGitPath("", "truc").compareTo(getGitPath("", "trud")) < 0);
		assertTrue(getGitPath("", "truc").compareTo(getGitPath("", "trucc")) < 0);
		assertTrue(getGitPath("", "truc").compareTo(getGitPath("", "truc/chose")) < 0);
		assertTrue(getGitPath("", "truc/chose").compareTo(getGitPath("", "truc/chose")) == 0);
		assertTrue(getGitPath("", "trucd").compareTo(getGitPath("", "truc")) > 0);
		assertTrue(getGitPath("masta", "/truc").compareTo(getGitPath("master", "/trua")) < 0);
		assertTrue(getGitPath("master", "/truc/chose").compareTo(getGitPath("master", "/truc/chose")) == 0);
		assertTrue(getGitPath("master", "/truc").compareTo(getGitPath("master", "/trud")) < 0);

		assertEquals(getGitPath("", "truc/chose"), getGitPath("", "truc/chose"));
		assertEquals(getGitPath("master", "/truc/chose"), getGitPath("master", "/truc/chose"));

		assertNotEquals(getGitPath("", ""), getGitPath("master", "/"));
		assertNotEquals(getGitPath("", "truc/chose"), getGitPath("master", "/truc/chose"));
	}

	@Test
	void testUris() throws Exception {
		final Path gitDir = Path.of("git dir");
		@SuppressWarnings("resource")
		final GitRepoFileSystem fs = GitDirFileSystem.given(new GitFileSystemProvider(), gitDir);
		final GitPath path = fs.getPath("master/", "/file.txt");
		assertEquals(
				new URI("gitjfs", null, gitDir.toAbsolutePath().toString(), "revStr=master&dirAndFile=/file.txt", null),
				path.toUri());

		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			@SuppressWarnings("resource")
			final GitRepoFileSystem rfs = GitRepoFileSystem.given(new GitFileSystemProvider(), repo);
			final GitPath p2 = rfs.getPath("master/", "/file.txt");
			assertEquals("gitjfs://mem/myrepo?revStr=master&dirAndFile=/file.txt", p2.toUri().toString());
		}
	}

	private GitPath getGitPath(String revSpec, String dirAndFile) {
		return new GitPath(GIT_FILE_SYSTEM, revSpec, GitRepoFileSystem.JIM_FS.getPath(dirAndFile));
	}
}
