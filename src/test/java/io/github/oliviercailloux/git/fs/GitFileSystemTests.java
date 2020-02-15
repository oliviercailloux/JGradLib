package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
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
			assertEquals("", gitFS.getPath("", "", "").getWithoutRoot().toString());
			assertEquals("truc", gitFS.getPath("", "truc").toString());
			assertEquals("truc", gitFS.getPath("", "truc").getWithoutRoot().toString());
			assertEquals("master//truc", gitFS.getPath("master/", "/truc").toString());
			assertEquals("truc", gitFS.getPath("master/", "/truc").getWithoutRoot().toString());
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
//		final Path gitDir = Path.of("git-test-read " + Instant.now());
//		Files.createDirectory(gitDir);
//		try (Repository repo = new FileRepository(gitDir.toString())) {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGitUsage.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				assertThrows(FileNotFoundException.class,
						() -> gitFS.newByteChannel(gitFS.getPath("master/", "/ploum.txt")));
				final GitPath path = gitFS.getPath("master/", "/file1.txt");
//			final SeekableByteChannel newByteChannel = gitFS.newByteChannel(path);
				final String content1 = Files.readString(path);
				assertEquals("Hello, world", content1);
			}
		}
	}

	@Test
	void testReadDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGitUsage.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				final GitPath noSuchDir = gitFS.getPath("master/", "/no such dir");
				assertThrows(FileNotFoundException.class, () -> gitFS.newDirectoryStream(noSuchDir, (p) -> true));
				final GitPath rootDir = gitFS.getPath("", "");
				assertEquals(ImmutableSet.of(gitFS.getPath("", "file1.txt"), gitFS.getPath("", "file2.txt")),
						ImmutableSet.copyOf(gitFS.newDirectoryStream(rootDir, (p) -> true)));
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
			JGitUsage.createBasicRepo(repo);
			try (GitRepoFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromRepository(repo)) {
				final ImmutableSet<RevCommit> commitsOrdered = gitFS.getHistory().getCommitDates().keySet();
				final ImmutableSet<GitPath> commitPaths = commitsOrdered.stream()
						.map((c) -> gitFS.getPath(c.getName() + "/")).collect(ImmutableSet.toImmutableSet());
				assertEquals(commitPaths, gitFS.getGitRootDirectories());
			}
		}
	}
}
