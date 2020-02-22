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
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GitFileSystemTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemTests.class);

	@Test
	void testPaths() throws Exception {
		try (GitRepoFileSystem gitFS = GitDirFileSystem.given(Mockito.mock(GitFileSystemProvider.class),
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
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFS.getPath("", "file1.txt")));
				assertEquals("Hello from sub dir", Files.readString(gitFS.getRelativePath("dir", "file.txt")));
				assertEquals("Hello, world", Files.readString(gitFS.getPath("master/", "/file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFS.getAbsolutePath("master", "file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFS.getAbsolutePath("refs/heads/master", "file1.txt")));
				assertThrows(NoSuchFileException.class,
						() -> Files.readString(gitFS.getAbsolutePath(commits.get(0).getName(), "file2.txt")));
				assertEquals("Hello again",
						Files.readString(gitFS.getAbsolutePath(commits.get(1).getName(), "file2.txt")));
				assertEquals("I insist", Files.readString(gitFS.getAbsolutePath("master", "file2.txt")));
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
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals(ImmutableSet.copyOf(commits), gitFS.getHistory().getCommitDates().keySet());
				assertTrue(Files.exists(gitFS.getAbsolutePath("master")));
				assertTrue(Files.exists(gitFS.getAbsolutePath("refs/heads/master")));
				assertFalse(Files.exists(gitFS.getAbsolutePath("/refs/heads/master")));
				assertFalse(Files.exists(gitFS.getAbsolutePath("/master")));
				assertTrue(Files.exists(gitFS.getAbsolutePath(commits.get(0).getName())));
				assertFalse(Files.exists(gitFS.getPath("master/", "/ploum.txt")));
				assertFalse(Files.exists(gitFS.getPath("master/", "/dir/ploum.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/dir/file.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/file1.txt")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/dir")));
				assertTrue(Files.exists(gitFS.getPath("master/", "/")));
				assertTrue(Files.exists(gitFS.getPath("", "dir")));
				assertTrue(Files.exists(gitFS.getPath("", "")));
				assertFalse(Files.exists(gitFS.getRelativePath("ploum.txt")));
				assertFalse(Files.exists(gitFS.getAbsolutePath("blah")));
				assertFalse(Files.exists(gitFS.getAbsolutePath("blah", "/file1.txt")));
				assertTrue(Files.exists(gitFS.getAbsolutePath(commits.get(0).getName(), "/file1.txt")));
				assertFalse(Files.exists(gitFS.getAbsolutePath(commits.get(0).getName(), "/ploum.txt")));
			}
		}
	}

	@Test
	void testRoots() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<RevCommit> commitsOrdered = gitFS.getHistory().getCommitDates().keySet();
				final ImmutableSet<GitPath> commitPaths = commitsOrdered.stream()
						.map((c) -> gitFS.getPath(c.getName() + "/")).collect(ImmutableSet.toImmutableSet());
				assertEquals(commitPaths, gitFS.getGitRootDirectories());
			}
		}
	}

	@Test
	void testFind() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals(
						ImmutableList.of(gitFS.getRoot(), gitFS.getRelativePath("file1.txt"),
								gitFS.getRelativePath("file2.txt"), gitFS.getRelativePath("dir"),
								gitFS.getRelativePath("dir", "file.txt")),
						Files.find(gitFS.getRelativePath(""), 4, (p, a) -> true)
								.collect(ImmutableList.toImmutableList()));
				assertEquals(
						ImmutableList.of(gitFS.getAbsolutePath("master", "/dir"),
								gitFS.getAbsolutePath("master", "/dir", "file.txt")),
						Files.find(gitFS.getAbsolutePath("master", "/dir"), 4, (p, a) -> true)
								.collect(ImmutableList.toImmutableList()));
			}
		}
	}

	@Test
	void testReadDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertThrows(NoSuchFileException.class,
						() -> gitFS.newDirectoryStream(gitFS.getPath("master/", "/no such dir"), (p) -> true));
				assertEquals(ImmutableSet.of(gitFS.getPath("", "file1.txt"), gitFS.getPath("", "file2.txt")),
						ImmutableSet.copyOf(gitFS.newDirectoryStream(gitFS.getRoot(), p -> true)));
				assertEquals(
						ImmutableSet.of(gitFS.getPath("master/", "/file1.txt"), gitFS.getPath("master/", "/file2.txt")),
						ImmutableSet.copyOf(gitFS.newDirectoryStream(gitFS.getPath("master/", "/"), p -> true)));

				assertThrows(NotDirectoryException.class,
						() -> gitFS.newDirectoryStream(gitFS.getPath("master/", "/file1.txt"), p -> true));
			}
		}
	}

	@Test
	void testReadSubDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<GitPath> subEntries = ImmutableSet.of(gitFS.getRelativePath("dir"),
						gitFS.getRelativePath("file1.txt"), gitFS.getRelativePath("file2.txt"));
				assertEquals(subEntries, ImmutableSet.copyOf(gitFS.newDirectoryStream(gitFS.getRoot(), p -> true)));
				assertEquals(subEntries, Files.list(gitFS.getRoot()).collect(ImmutableSet.toImmutableSet()));
			}
		}
	}
}
