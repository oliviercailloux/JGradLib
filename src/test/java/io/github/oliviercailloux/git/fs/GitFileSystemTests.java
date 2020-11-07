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
import java.nio.file.LinkOption;
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

import io.github.oliviercailloux.git.JGit;

public class GitFileSystemTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemTests.class);

	@Test
	void testPaths() throws Exception {
		try (GitRepoFileSystem gitFs = GitFileFileSystem.given(Mockito.mock(GitFileSystemProvider.class),
				Path.of("/path/to/gitdir"))) {
			assertEquals("", gitFs.getPath("").toString());
			assertEquals("", gitFs.getPath("", "", "").toString());
			assertEquals("", gitFs.getPath("", "", "").toRelativePath().toString());
			assertEquals("truc", gitFs.getPath("", "truc").toString());
			assertEquals("truc", gitFs.getPath("", "truc").toRelativePath().toString());
			assertEquals("master//truc", gitFs.getPath("master/", "/truc").toString());
			assertEquals("truc", gitFs.getPath("master/", "/truc").toRelativePath().toString());
			assertEquals(URI.create("gitjfs:/path/to/gitdir?revStr=master&dirAndFile=/truc"),
					gitFs.getPath("master/", "/truc").toUri());
			assertEquals("master//truc", gitFs.getPath("master/", "", "/truc", "").toString());
			assertEquals("master//truc", gitFs.getPath("master//", "truc").toString());
			assertEquals("master//truc", gitFs.getPath("master//", "/truc").toString());
			assertEquals("master//truc", gitFs.getPath("master///", "/truc").toString());
			assertEquals("master//chose/truc", gitFs.getPath("master//chose//", "/truc").toString());
			assertEquals("master//truc", gitFs.getPath("master//truc").toString());
			assertEquals("master//truc/chose", gitFs.getPath("master//truc", "chose").toString());
			assertEquals("chose/truc", gitFs.getPath("", "chose//truc").toString());

			/** Should think about this… */
			assertEquals("truc", gitFs.getPath("truc").toString());

			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("chose/truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("master", "/truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("master", "//truc").toString());
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("", "/truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("master", "truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("master/", "truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("//", "/truc"));
			assertThrows(IllegalArgumentException.class, () -> gitFs.getPath("/truc", "/truc"));
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
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFs.getPath("", "file1.txt")));
				assertEquals("Hello from sub dir", Files.readString(gitFs.getRelativePath("dir", "file.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getPath("master/", "/file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getAbsolutePath("master", "file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getAbsolutePath("refs/heads/master", "file1.txt")));
				assertThrows(NoSuchFileException.class,
						() -> Files.readString(gitFs.getAbsolutePath(commits.get(0).getName(), "file2.txt")));
				assertEquals("Hello again",
						Files.readString(gitFs.getAbsolutePath(commits.get(1).getName(), "file2.txt")));
				assertEquals("I insist", Files.readString(gitFs.getAbsolutePath("master", "file2.txt")));
				assertThrows(NoSuchFileException.class,
						() -> gitFs.newByteChannel(gitFs.getPath("master/", "/ploum.txt")));
				try (SeekableByteChannel dirChannel = gitFs.newByteChannel(gitFs.getPath("master/", "/"))) {
					assertThrows(IOException.class, () -> dirChannel.size());
				}
				assertThrows(IOException.class, () -> Files.readString(gitFs.getPath("", "")));
				assertThrows(IOException.class, () -> Files.readString(gitFs.getPath("", "dir")));
			}
		}
	}

	@Test
	void testExists() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals(ImmutableSet.copyOf(commits), gitFs.getHistory().getCommitDates().keySet());
				assertTrue(Files.exists(gitFs.getAbsolutePath("master")));
				assertTrue(Files.exists(gitFs.getAbsolutePath("refs/heads/master")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/heads/master")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/master")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0).getName())));
				assertFalse(Files.exists(gitFs.getPath("master/", "/ploum.txt")));
				assertFalse(Files.exists(gitFs.getPath("master/", "/dir/ploum.txt")));
				assertTrue(Files.exists(gitFs.getPath("master/", "/file1.txt")));
				assertTrue(Files.exists(gitFs.getPath("master/", "/dir/file.txt")));
				assertTrue(Files.exists(gitFs.getPath("master/", "/dir")));
				assertTrue(Files.exists(gitFs.getPath("master/", "/")));
				assertTrue(Files.exists(gitFs.getPath("", "dir")));
				assertTrue(Files.exists(gitFs.getPath("", "")));
				assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("blah")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("blah", "/file1.txt")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0).getName(), "/file1.txt")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(0).getName(), "/ploum.txt")));
			}
		}
	}

	@Test
	void testRealPath() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			final String lastCommitId = commits.get(commits.size() - 1).getName();
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals(gitFs.getAbsolutePath("master"),
						gitFs.getAbsolutePath("master").toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(gitFs.getAbsolutePath(lastCommitId), gitFs.getAbsolutePath("master").toRealPath());
				assertEquals(gitFs.getAbsolutePath("master", "dir"),
						gitFs.getRelativePath("dir").toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(gitFs.getAbsolutePath(lastCommitId, "dir"), gitFs.getRelativePath("dir").toRealPath());
//				assertEquals(gitFs.getPath("", "dir"),
//						gitFs.getPath("", "./dir").toRealPath(LinkOption.NOFOLLOW_LINKS));
//				assertEquals(gitFs.getPath("", "dir"), gitFs.getPath("", "./dir").toRealPath());
			}
		}
	}

	@Test
	void testStartsWith() throws Exception {
		/** Let’s see how the default file system works, for comparison. */
		final Path root = FileSystems.getDefault().getRootDirectories().iterator().next();
		assertFalse(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root.resolve(Path.of("dir", "p"))));
		assertTrue(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root.resolve(Path.of("dir"))));
		assertTrue(root.resolve(Path.of("dir", "ploum.txt")).startsWith(root));
		assertFalse(Path.of("dir/ploum.txt").startsWith(Path.of("dir/p")));
		assertTrue(Path.of("/dir").startsWith(Path.of("/")));

		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertTrue(gitFs.getAbsolutePath("master").startsWith(gitFs.getAbsolutePath("master")));
				assertTrue(gitFs.getAbsolutePath("master", "ploum.txt").startsWith(gitFs.getAbsolutePath("master")));
				assertTrue(gitFs.getAbsolutePath("master", "dir", "ploum.txt")
						.startsWith(gitFs.getAbsolutePath("master")));
				assertTrue(gitFs.getAbsolutePath("master", "dir", "ploum.txt")
						.startsWith(gitFs.getAbsolutePath("master", "dir")));
				assertFalse(gitFs.getAbsolutePath("master", "dir", "ploum.txt")
						.startsWith(gitFs.getAbsolutePath("master", "dir", "p")));
				assertFalse(gitFs.getAbsolutePath("master", "dir", "ploum.txt")
						.startsWith(gitFs.getAbsolutePath("master", "dir", "plom.txt")));
				assertFalse(gitFs.getAbsolutePath("master", "dir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "ploum.txt")));
				assertFalse(gitFs.getAbsolutePath("master").startsWith(gitFs.getRelativePath("")));
				assertFalse(gitFs.getAbsolutePath("master").startsWith(gitFs.getRoot()));

				assertTrue(gitFs.getRelativePath("dir").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(gitFs.getRelativePath("dir", "ploum.txt").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(
						gitFs.getRelativePath("dir", "subdir", "ploum.txt").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir", "p")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir", "plom.txt")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("subdir", "ploum.txt")));
				assertFalse(gitFs.getRelativePath("dir").startsWith(gitFs.getAbsolutePath("master")));
				assertFalse(gitFs.getRelativePath("").startsWith(gitFs.getAbsolutePath("master")));
				assertTrue(gitFs.getRelativePath("").startsWith(gitFs.getRoot()));

				assertTrue(gitFs.getAbsolutePath("master").startsWith("master/"));
			}
		}
	}

	@Test
	void testRoots() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<RevCommit> commitsOrdered = gitFs.getHistory().getCommitDates().keySet();
				final ImmutableSet<GitPath> commitPaths = commitsOrdered.stream()
						.map((c) -> gitFs.getPath(c.getName() + "/")).collect(ImmutableSet.toImmutableSet());
				assertEquals(commitPaths, gitFs.getGitRootDirectories());
			}
		}
	}

	@Test
	void testFind() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertEquals(
						ImmutableSet.of(gitFs.getRoot(), gitFs.getRelativePath("file1.txt"),
								gitFs.getRelativePath("file2.txt"), gitFs.getRelativePath("dir"),
								gitFs.getRelativePath("dir", "file.txt")),
						Files.find(gitFs.getRelativePath(""), 4, (p, a) -> true)
								.collect(ImmutableSet.toImmutableSet()));
				assertEquals(
						ImmutableSet.of(gitFs.getAbsolutePath("master", "/dir"),
								gitFs.getAbsolutePath("master", "/dir", "file.txt")),
						Files.find(gitFs.getAbsolutePath("master", "/dir"), 4, (p, a) -> true)
								.collect(ImmutableSet.toImmutableSet()));
			}
		}
	}

	@Test
	void testReadDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				assertThrows(NoSuchFileException.class,
						() -> gitFs.newDirectoryStream(gitFs.getPath("master/", "/no such dir"), (p) -> true));
				assertEquals(ImmutableSet.of(gitFs.getPath("", "file1.txt"), gitFs.getPath("", "file2.txt")),
						ImmutableSet.copyOf(gitFs.newDirectoryStream(gitFs.getRoot(), p -> true)));
				assertEquals(
						ImmutableSet.of(gitFs.getPath("master/", "/file1.txt"), gitFs.getPath("master/", "/file2.txt")),
						ImmutableSet.copyOf(gitFs.newDirectoryStream(gitFs.getPath("master/", "/"), p -> true)));

				assertThrows(NotDirectoryException.class,
						() -> gitFs.newDirectoryStream(gitFs.getPath("master/", "/file1.txt"), p -> true));
			}
		}
	}

	@Test
	void testReadSubDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<GitPath> subEntries = ImmutableSet.of(gitFs.getRelativePath("dir"),
						gitFs.getRelativePath("file1.txt"), gitFs.getRelativePath("file2.txt"));
				assertEquals(subEntries, ImmutableSet.copyOf(gitFs.newDirectoryStream(gitFs.getRoot(), p -> true)));
				assertEquals(subEntries, Files.list(gitFs.getRoot()).collect(ImmutableSet.toImmutableSet()));
			}
		}
	}
}
