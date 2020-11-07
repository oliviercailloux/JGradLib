package io.github.oliviercailloux.git.fs;

import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.JGit;

public class GitPathTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathTests.class);

	final GitRepoFileSystem GIT_FILE_SYSTEM = IO_UNCHECKER
			.getUsing(() -> GitFileFileSystem.given(Mockito.mock(GitFileSystemProvider.class), Path.of(".")));

	@Test
	void testBasics() throws Exception {
		final RootComponent main = RootComponent.DEFAULT;
		assertThrows(IllegalArgumentException.class, () -> getGitPath(main, ""));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(main, "ploum"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(null, "/"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(null, "/truc"));

		assertEquals("", getGitPath(null, "").toString());
		assertEquals("ploum", getGitPath(null, "ploum").toString());
		assertEquals("truc/chose", getGitPath(null, "truc/chose").toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").toString());
		assertEquals("/refs/heads/main//ploum", getGitPath(main, "/ploum").toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(main, "/truc/chose").toString());

		assertFalse(getGitPath(null, "").isAbsolute());
		assertFalse(getGitPath(null, "truc/chose").isAbsolute());
		assertTrue(getGitPath(main, "/").isAbsolute());
		assertTrue(getGitPath(main, "/truc/chose").isAbsolute());

		assertEquals("/refs/heads/main//", getGitPath(main, "/").getRoot().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc/chose").getRoot().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc.txt").getRoot().toString());
		assertNull(getGitPath(null, "truc.txt").getRoot());

		assertEquals(0, getGitPath(main, "/").getNameCount());
		assertEquals(1, getGitPath(main, "/truc.txt").getNameCount());
		assertEquals(2, getGitPath(main, "/truc/chose").getNameCount());
		assertEquals(1, getGitPath(null, "").getNameCount());
		assertEquals(1, getGitPath(null, "truc").getNameCount());
		assertEquals(2, getGitPath(null, "truc/chose").getNameCount());

		assertEquals("", getGitPath(null, "").getFileName().toString());
		assertEquals("chose", getGitPath(null, "truc/chose").getFileName().toString());
		assertEquals("chose", getGitPath(main, "/truc/chose").getFileName().toString());
		assertNull(getGitPath(main, "/").getFileName());

		assertNull(getGitPath(main, "/").getParent());
		assertEquals("truc", getGitPath(null, "truc/chose").getParent().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc").getParent().toString());
		assertEquals("/refs/heads/main//truc", getGitPath(main, "/truc/chose").getParent().toString());

		assertEquals("", getGitPath(null, "").resolve(getGitPath(null, "")).toString());
		assertEquals("ploum.txt", getGitPath(null, "").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(null, "").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("truc", getGitPath(null, "truc").resolve(getGitPath(null, "")).toString());
		assertEquals("truc/ploum.txt", getGitPath(null, "truc").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "truc").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(null, "truc").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").resolve(getGitPath(null, "")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//",
				getGitPath(RootComponent.givenRef("refs/something"), "/").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt", getGitPath(RootComponent.givenRef("refs/something"), "/")
				.resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//truc", getGitPath(main, "/truc").resolve(getGitPath(null, "")).toString());
		assertEquals("/refs/heads/main//truc/ploum.txt",
				getGitPath(main, "/truc").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/truc").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(RootComponent.givenRef("refs/something"), "/truc")
				.resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt", getGitPath(RootComponent.givenRef("refs/something"), "/truc")
				.resolve(getGitPath(main, "/ploum.txt")).toString());

		assertEquals("", getGitPath(null, "").relativize(getGitPath(null, "")).toString());
		assertEquals("chose", getGitPath(null, "truc").relativize(getGitPath(null, "truc/chose")).toString());
		assertEquals("../../stuff", getGitPath(null, "truc/chose").relativize(getGitPath(null, "stuff")).toString());
		assertEquals("../stuff", getGitPath(main, "/truc").relativize(getGitPath(main, "/stuff")).toString());
		assertEquals("", getGitPath(main, "/truc").relativize(getGitPath(main, "/truc")).toString());
		assertEquals("chose", getGitPath(main, "/truc").relativize(getGitPath(main, "/truc/chose")).toString());
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath(main, "/truc").relativize(getGitPath(null, "/truc/chose")));
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath(null, "/truc").relativize(getGitPath(main, "/truc/chose")));

		assertEquals("/refs/heads/main//", getGitPath(main, "/").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(main, "/truc/chose").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(null, "truc/chose").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc.txt", getGitPath(null, "truc.txt").toAbsolutePath().toString());

		assertEquals(getGitPath(null, "truc/chose"), getGitPath(null, "truc/chose"));
		assertEquals(getGitPath(main, "/truc/chose"), getGitPath(main, "/truc/chose"));

		assertNotEquals(getGitPath(null, ""), getGitPath(main, "/"));
		assertNotEquals(getGitPath(null, "truc/chose"), getGitPath(main, "/truc/chose"));
	}

	@Test
	void testUris() throws Exception {
		final Path gitDir = Path.of("git dir");
		@SuppressWarnings("resource")
		final GitRepoFileSystem fs = GitFileFileSystem.given(new GitFileSystemProvider(), gitDir);
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

	private GitPath getGitPath(RootComponent root, String dirAndFile) {
		if (root != null) {
			return GitPath.absolute(GIT_FILE_SYSTEM, root, GitFileSystemProvider.JIM_FS.getPath(dirAndFile));
		}
		return GitPath.relative(GIT_FILE_SYSTEM, GitFileSystemProvider.JIM_FS.getPath(dirAndFile));
	}
}
