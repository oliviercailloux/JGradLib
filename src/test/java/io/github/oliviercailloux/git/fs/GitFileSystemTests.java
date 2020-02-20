package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class GitFileSystemTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemTests.class);

	@Test
	void testPaths() throws Exception {
		try (GitFileSystem gitFS = GitFileSystem.given(Mockito.mock(GitFileSystemProvider.class),
				Path.of("/path/to/gitdir"))) {
			assertEquals("", gitFS.getPath("").toString());
			assertEquals("", gitFS.getPath("", "", "").toString());
			assertEquals("", gitFS.getPath("", "", "").toRelativePath().toString());
			assertEquals("truc", gitFS.getPath("", "truc").toString());
			assertEquals("truc", gitFS.getPath("", "truc").toRelativePath().toString());
			assertEquals("master//truc", gitFS.getPath("master/", "/truc").toString());
			assertEquals("truc", gitFS.getPath("master/", "/truc").toRelativePath().toString());
			assertEquals(URI.create("gitfs:/path/to/gitdir?revStr=master&dirAndFile=/truc"),
					gitFS.getPath("master/", "/truc").toUri());
			assertEquals("master//truc", gitFS.getPath("master/", "", "/truc", "").toString());
			assertEquals("master//truc", gitFS.getPath("master//", "truc").toString());
			assertEquals("master//truc", gitFS.getPath("master//", "/truc").toString());
			assertEquals("master//truc", gitFS.getPath("master///", "/truc").toString());
			assertEquals("master//chose/truc", gitFS.getPath("master//chose//", "/truc").toString());
			assertEquals("master//truc", gitFS.getPath("master//truc").toString());
			assertEquals("master//truc/chose", gitFS.getPath("master//truc", "chose").toString());
			assertEquals("chose/truc", gitFS.getPath("", "chose//truc").toString());

			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("master").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("chose/truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("master", "/truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("master", "//truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("", "/truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("master", "truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("master/", "truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("//", "/truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFS.getPath("/truc", "/truc"));
		}
	}

	@Test
	void testReadFiles() throws Exception {
//		final Path gitDir = Path.of("git-test " + Instant.now());
//		Files.createDirectory(gitDir);
//		try (Repository repo = new FileRepository(gitDir.toString())) {

		/** Let’s see how the default file system works, for comparison. */
		final Path root = FileSystems.getDefault().getRootDirectories().iterator().next();
		assertDoesNotThrow(() -> Files.newByteChannel(root));
		assertThrows(IOException.class, () -> Files.readString(root));

		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFS.getPath("", "file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFS.getPath("master/", "/file1.txt")));
				assertThrows(NoSuchFileException.class,
						() -> gitFS.newByteChannel(gitFS.getPath("master/", "/ploum.txt")));
				try (SeekableByteChannel dirChannel = gitFS.newByteChannel(gitFS.getPath("master/", "/"))) {
					assertThrows(IOException.class, () -> dirChannel.size());
				}
				assertThrows(IOException.class, () -> Files.readString(gitFS.getPath("", "")));
				assertThrows(IOException.class, () -> Files.readString(gitFS.getPath("", "dir")));
			}
		}
	}

	@Test
	void testExists() throws Exception {
//		final Path gitDir = Path.of("git-test " + Instant.now());
//		Files.createDirectory(gitDir);
//		try (Repository repo = new FileRepository(gitDir.toString())) {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				assertFalse(Files.exists(gitFS.getPath("master/", "/ploum.txt")));
				assertFalse(Files.exists(gitFS.getPath("master/", "/dir/ploum.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/dir/file.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/file1.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/dir")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/")));
				assertTrue(Files.exists(gitFS.getPath("", "dir")));
				assertTrue(Files.exists(gitFS.getPath("", "")));
			}
		}
	}

	@Test
	void testReadDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				assertThrows(NoSuchFileException.class, () -> gitFS.newDirectoryStream(gitFS.getPath("master/", "/no such dir"), (p) -> true));
				final GitPath rootDir = gitFS.getPath("", "");
				assertEquals(ImmutableSet.of(gitFS.getPath("", "file1.txt"), gitFS.getPath("", "file2.txt")),
						ImmutableSet.copyOf(gitFS.newDirectoryStream(rootDir, p -> true)));
				final GitPath masterRootDir = gitFS.getPath("master/", "/");
				assertEquals(
						ImmutableSet.of(gitFS.getPath("master/", "/file1.txt"), gitFS.getPath("master/", "/file2.txt")),
						ImmutableSet.copyOf(gitFS.newDirectoryStream(masterRootDir, (p) -> true)));
			}
		}
	}

	@Test
	void testRoots() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				final ImmutableSet<RevCommit> commitsOrdered = gitFS.getHistory().getCommitDates().keySet();
				final ImmutableSet<GitPath> commitPaths = commitsOrdered.stream()
						.map((c) -> gitFS.getPath(c.getName() + "/")).collect(ImmutableSet.toImmutableSet());
				assertEquals(commitPaths, gitFS.getGitRootDirectories());
			}
		}
	}
}
