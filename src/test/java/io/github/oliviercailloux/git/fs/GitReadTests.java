package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.JGit;

/**
 * Tests about actual reading from a repo, using the Files API.
 */
public class GitReadTests {

	@Test
	void testReadFiles() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("./file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath(".", "dir", "..", "/file1.txt")));
				assertEquals("Hello from sub dir", Files.readString(gitFs.getRelativePath("dir", "file.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("file1.txt").toAbsolutePath()));
				assertThrows(NoSuchFileException.class,
						() -> Files.readString(gitFs.getAbsolutePath(commits.get(0), "file2.txt")));
				assertEquals("Hello again", Files.readString(gitFs.getAbsolutePath(commits.get(1), "file2.txt")));
				assertEquals("I insist", Files.readString(gitFs.getRelativePath("file2.txt")));
				assertEquals("I insist", Files.readString(gitFs.getRelativePath("file2.txt").toAbsolutePath()));
				assertThrows(NoSuchFileException.class, () -> Files.newByteChannel(gitFs.getRelativePath("ploum.txt")));
				try (SeekableByteChannel dirChannel = Files.newByteChannel(gitFs.getRelativePath())) {
					assertThrows(IOException.class, () -> dirChannel.size());
				}
				assertThrows(IOException.class, () -> Files.readString(gitFs.getRelativePath()));
				assertThrows(IOException.class, () -> Files.readString(gitFs.getRelativePath("dir")));
			}
		}
	}

	@Test
	void testReadLinks() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
//			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				/**
				 * TODO should read links transparently, as indicated in the package-summary of
				 * the nio package. Thus, assuming dir is a symlink to otherdir, reading
				 * dir/file.txt should read otherdir/file.txt. This is also what git operations
				 * do naturally: checking out dir will restore it as a symlink to otherdir
				 * (https://stackoverflow.com/a/954575/). Consider implementing
				 * Provider#readLinks and so on.
				 *
				 * Note that a git repository does not have the concept of hard links
				 * (https://stackoverflow.com/a/3731139/).
				 */
				assertFalse(true);
			}
		}
	}

	@Test
	void testExists() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals(ImmutableSet.copyOf(commits), gitFs.getHistory().getCommitDates().keySet());
				assertTrue(Files.exists(gitFs.getRelativePath()));
				assertTrue(Files.exists(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/main/")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/nothing/")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(ObjectId.zeroId())));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/ploum.txt")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0))));
				assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt")));
				assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt").toAbsolutePath()));
				assertFalse(Files.exists(gitFs.getRelativePath("dir/ploum.txt")));
				assertTrue(Files.exists(gitFs.getRelativePath("file1.txt")));
				assertTrue(Files.exists(gitFs.getRelativePath("dir/file.txt")));
				assertTrue(Files.exists(gitFs.getRelativePath("dir")));
			}
		}
	}

//	@Test
	void testRealPath() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			final String lastCommitId = commits.get(commits.size() - 1).getName();
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertNotEquals(gitFs.getRelativePath().toAbsolutePath(),
						gitFs.getRelativePath().toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(gitFs.getAbsolutePath(lastCommitId), gitFs.getRelativePath().toRealPath());
				assertEquals(gitFs.getAbsolutePath(lastCommitId, "dir"),
						gitFs.getRelativePath("dir").toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(gitFs.getAbsolutePath(lastCommitId, "dir"), gitFs.getRelativePath("dir").toRealPath());
				// assertEquals(gitFs.getPath("", "dir"),
				// gitFs.getPath("", "./dir").toRealPath(LinkOption.NOFOLLOW_LINKS));
				// assertEquals(gitFs.getPath("", "dir"), gitFs.getPath("",
				// "./dir").toRealPath());
			}
		}
	}

	/**
	 * This does not access the file system, in fact. But it does use the GFS path
	 * creation methods, so I leave it here for now.
	 */
	@Test
	void testStartsWith() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertTrue(
						gitFs.getRelativePath().toAbsolutePath().startsWith(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(gitFs.getRelativePath("ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath("dir").toAbsolutePath()));
				assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath("dir", "p").toAbsolutePath()));
				assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath("dir", "plom.txt").toAbsolutePath()));
				assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
						.startsWith(gitFs.getRelativePath("dir", "ploum.txt")));
				assertFalse(gitFs.getRelativePath().toAbsolutePath().startsWith(gitFs.getRelativePath()));

				assertTrue(gitFs.getRelativePath("dir").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(gitFs.getRelativePath("dir", "ploum.txt").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(
						gitFs.getRelativePath("dir", "subdir", "ploum.txt").startsWith(gitFs.getRelativePath("dir")));
				assertTrue(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir", "p")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("dir", "subdir", "ploum.txt2")));
				assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
						.startsWith(gitFs.getRelativePath("subdir", "ploum.txt")));
				assertFalse(gitFs.getRelativePath("dir").startsWith(gitFs.getRelativePath().toAbsolutePath()));
				assertFalse(gitFs.getRelativePath().startsWith(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(gitFs.getRelativePath().startsWith(gitFs.getRelativePath()));

				assertThrows(InvalidPathException.class,
						() -> gitFs.getRelativePath().toAbsolutePath().startsWith("/refs"));
				assertThrows(InvalidPathException.class,
						() -> gitFs.getRelativePath().toAbsolutePath().startsWith("/refs//"));
				assertFalse(gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/heads/"));
				assertTrue(gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/heads/main/"));
			}
		}
	}

	@Test
	void testRoots() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<RevCommit> commitsOrdered = gitFs.getHistory().getCommitDates().keySet();
				final ImmutableSet<GitPath> commitPaths = commitsOrdered.stream().map((c) -> gitFs.getAbsolutePath(c))
						.collect(ImmutableSet.toImmutableSet());
				assertEquals(commitPaths, gitFs.getGitRootDirectories());
			}
		}
	}

	@Test
	void testFind() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals(
						ImmutableSet.of(gitFs.getRelativePath(), gitFs.getRelativePath("file1.txt"),
								gitFs.getRelativePath("file2.txt"), gitFs.getRelativePath("dir"),
								gitFs.getRelativePath("dir", "file.txt")),
						Files.find(gitFs.getRelativePath(), 4, (p, a) -> true).collect(ImmutableSet.toImmutableSet()));
				assertEquals(
						ImmutableSet.of(gitFs.getRelativePath("dir").toAbsolutePath(),
								gitFs.getRelativePath("dir", "file.txt").toAbsolutePath()),
						Files.find(gitFs.getRelativePath("dir").toAbsolutePath(), 4, (p, a) -> true)
								.collect(ImmutableSet.toImmutableSet()));
			}
		}
	}

	@Test
	void testReadDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertThrows(NoSuchFileException.class, () -> Files
						.newDirectoryStream(gitFs.getRelativePath("no such dir").toAbsolutePath(), (p) -> true));
				assertEquals(
						ImmutableSet.of(gitFs.getRelativePath("file1.txt"), gitFs.getRelativePath("", "file2.txt")),
						ImmutableSet.copyOf(Files.newDirectoryStream(gitFs.getRelativePath(), p -> true)));
				assertEquals(
						ImmutableSet.of(gitFs.getRelativePath("file1.txt").toAbsolutePath(),
								gitFs.getRelativePath("file2.txt").toAbsolutePath()),
						ImmutableSet
								.copyOf(Files.newDirectoryStream(gitFs.getRelativePath().toAbsolutePath(), p -> true)));

				assertThrows(NotDirectoryException.class,
						() -> Files.newDirectoryStream(gitFs.getRelativePath("file1.txt").toAbsolutePath(), p -> true));
			}
		}
	}

	@Test
	void testReadSubDir() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<GitPath> subEntries = ImmutableSet.of(gitFs.getRelativePath("dir"),
						gitFs.getRelativePath("file1.txt"), gitFs.getRelativePath("file2.txt"));
				assertEquals(subEntries,
						ImmutableSet.copyOf(gitFs.getRelativePath().toAbsolutePath().newDirectoryStream(p -> true)));
				assertEquals(subEntries, Files.list(gitFs.getRelativePath()).collect(ImmutableSet.toImmutableSet()));
			}
		}
	}

}
