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
import java.nio.file.NotLinkException;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.utils.Utils;

/**
 * Tests about actual reading from a repo, using the Files API.
 */
public class GitReadTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitReadTests.class);

	public static void main(String[] args) throws Exception {
		try (Repository repo = new FileRepository("/tmp/ploum/.git")) {
			JGit.createRepoWithLink(repo);
		}
	}

	@Test
	void testReadFiles() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("./file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getRelativePath(".", "dir", "..", "/file1.txt")));
				assertEquals("Hello, world",
						Files.readString(gitFs.getRelativePath(".", "dir", "..", "/file1.txt").toAbsolutePath()));
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
			final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);

			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals("Hello, world", Files.readString(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
				assertEquals("Hello, world", Files.readString(gitFs.getAbsolutePath(commits.get(0), "/link.txt")));
				assertThrows(PathCouldNotBeFoundException.class,
						() -> Files.readString(gitFs.getAbsolutePath(commits.get(0), "/absolute link")));
				assertEquals("Hello instead", Files.readString(gitFs.getAbsolutePath(commits.get(1), "/link.txt")));
				assertEquals("Hello instead", Files.readString(gitFs.getAbsolutePath(commits.get(2), "/dir/link")));
				assertEquals("Hello instead",
						Files.readString(gitFs.getAbsolutePath(commits.get(2), "/dir/linkToParent/dir/link")));
				assertEquals("Hello instead", Files.readString(
						gitFs.getAbsolutePath(commits.get(2), "/dir/linkToParent/dir/linkToParent/dir/link")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/link")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/linkToParent/dir/link")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/link"), LinkOption.NOFOLLOW_LINKS));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/linkToParent/dir/link"),
						LinkOption.NOFOLLOW_LINKS));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink"),
						LinkOption.NOFOLLOW_LINKS));

				assertThrows(NotLinkException.class,
						() -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
				assertEquals(gitFs.getRelativePath("file1.txt"),
						Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/link.txt")));
				assertThrows(NoSuchFileException.class,
						() -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/notexists")));
				assertEquals(gitFs.getRelativePath("../link.txt"),
						Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(2), "/dir/./link")));
				assertEquals(gitFs.getRelativePath("../dir/cyclingLink"),
						Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink")));
				final AbsoluteLinkException thrown = assertThrows(AbsoluteLinkException.class,
						() -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/absolute link")));
				assertEquals(gitFs.getAbsolutePath(commits.get(0), "/absolute link"), thrown.getLinkPath());
				assertEquals(Path.of("/absolute"), thrown.getTarget());
			}
		}
	}

	@Test
	void testExists() throws Exception {
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repository);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repository)) {
				assertTrue(Files.exists(gitFs.getRelativePath()));
				assertTrue(Files.exists(gitFs.getRelativePath().toAbsolutePath()));
				assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/main/")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/nothing/")));
				assertFalse(Files.exists(gitFs.getAbsolutePath(ObjectId.zeroId())));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0))));
				assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/ploum.txt")));
				assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
				assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt")));
				assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt").toAbsolutePath()));
				assertFalse(Files.exists(gitFs.getRelativePath("dir/ploum.txt")));
				assertTrue(Files.exists(gitFs.getRelativePath("file1.txt")));
				assertTrue(Files.exists(gitFs.getRelativePath("dir")));
				assertTrue(Files.exists(gitFs.getRelativePath("dir/file.txt")));
			}
		}
	}

	/**
	 * Just some experiments with a TreeWalk.
	 */
	@Test
	void testTreeWalk() throws Exception {
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createRepoWithSubDir(repository);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repository)) {
				final RevTree root = gitFs.getRelativePath().toAbsolutePath().getRoot().getRevTree();
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(root);
					final PathFilter filter = PathFilter.create("dir");
					treeWalk.setFilter(filter);
					treeWalk.setRecursive(false);
					final boolean foundDir = treeWalk.next();
					assertTrue(foundDir);
				}
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(root);
					final PathFilter filter = PathFilter.create("dir/file.txt");
					treeWalk.setFilter(filter);
					treeWalk.setRecursive(false);
					final boolean foundDir = treeWalk.next();
					assertTrue(foundDir);
				}
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(root);
					final PathFilter filter = PathFilter.create("dir");
					treeWalk.setFilter(filter);
					treeWalk.setRecursive(false);
					final boolean foundDir = treeWalk.next();
					assertTrue(foundDir);
					treeWalk.enterSubtree();
					assertTrue(treeWalk.next());
				}
			}
		}
	}

	@Test
	void testAttributes() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertThrows(NoSuchFileException.class,
						() -> ((GitAbsolutePath) gitFs.getAbsolutePath(commits.get(0), "/ploum.txt"))
								.readAttributes(ImmutableSet.of()));
			}
		}
	}

	@Test
	void testRealPath() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
			assertEquals(4, commits.size());
			final ObjectId commit1 = commits.get(0);
			final ObjectId commit2 = commits.get(1);
			final ObjectId commit3 = commits.get(2);
			final ObjectId commit4 = commits.get(3);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertEquals(gitFs.getRelativePath().toAbsolutePath(),
						gitFs.getRelativePath().toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(gitFs.getRelativePath().toAbsolutePath(), gitFs.getRelativePath().toRealPath());

				final GitPath c1File1 = gitFs.getAbsolutePath(commit1).resolve("file1.txt");
				assertEquals(c1File1, c1File1.toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(c1File1, c1File1.toRealPath());

				assertEquals(c1File1, gitFs.getAbsolutePath(commit1).resolve("link.txt").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class,
						() -> gitFs.getAbsolutePath(commit1).resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

				final GitPath c2File1 = gitFs.getAbsolutePath(commit2).resolve("file1.txt");
				assertEquals(c2File1, gitFs.getAbsolutePath(commit2).resolve("link.txt").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class,
						() -> gitFs.getAbsolutePath(commit2).resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit2).resolve("notexists.txt").toRealPath());
				assertThrows(NoSuchFileException.class, () -> gitFs.getAbsolutePath(commit2).resolve("notexists.txt")
						.toRealPath(LinkOption.NOFOLLOW_LINKS));

				final GitPath c3File1 = gitFs.getAbsolutePath(commit3).resolve("file1.txt");
				assertEquals(c3File1, gitFs.getAbsolutePath(commit3).resolve("dir/link").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class,
						() -> gitFs.getAbsolutePath(commit3).resolve("dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertEquals(c3File1, gitFs.getAbsolutePath(commit3).resolve("dir/linkToParent/dir/link").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/linkToParent/dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit3).resolve("dir/linkToParent/dir/notexists").toRealPath());
				assertThrows(NoSuchFileException.class, () -> gitFs.getAbsolutePath(commit3).resolve("dir/notexists")
						.toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/linkToParent/dir/notexists").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit3).resolve("dir/cyclingLink").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/cyclingLink").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit4).resolve("file1.txt").toRealPath());
				assertThrows(NoSuchFileException.class, () -> gitFs.getAbsolutePath(commit4).resolve("file1.txt")
						.toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit4).resolve("link.txt").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class,
						() -> gitFs.getAbsolutePath(commit4).resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit4).resolve("dir/linkToParent/dir/link").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit4)
						.resolve("dir/linkToParent/dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertNotEquals(c1File1, gitFs.getAbsolutePath(commit3).resolve("././dir/../link.txt").toRealPath());
				assertEquals(c3File1, gitFs.getAbsolutePath(commit3).resolve("././dir/../link.txt").toRealPath());
				assertEquals(c3File1, gitFs.getAbsolutePath(commit3).resolve("././dir/../file1.txt")
						.toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertEquals(c3File1, gitFs.getAbsolutePath(commit3).resolve("./link.txt").toRealPath());

				assertThrows(NoSuchFileException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/./../dir/./linkToParent/dir/notexists").toRealPath());
				assertThrows(NoSuchFileException.class, () -> gitFs.getAbsolutePath(commit3).resolve("dir/./notexists")
						.toRealPath(LinkOption.NOFOLLOW_LINKS));
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/linkToParent/./dir/../dir/notexists").toRealPath(LinkOption.NOFOLLOW_LINKS));

				assertThrows(NoSuchFileException.class,
						() -> gitFs.getAbsolutePath(commit3).resolve("dir/./../dir/cyclingLink").toRealPath());
				assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getAbsolutePath(commit3)
						.resolve("dir/./../dir/cyclingLink").toRealPath(LinkOption.NOFOLLOW_LINKS));
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
	void testGraph() throws Exception {
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createBasicRepo(repository);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repository)) {
				LOGGER.debug("Commits: {}.", commits);
				assertEquals(Utils.asImmutableGraph(Utils.asGraph(commits), gitFs::getPathRoot),
						gitFs.getCommitsGraph());
			}
		}
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repository);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repository)) {
				assertEquals(Utils.asImmutableGraph(Utils.asGraph(commits), gitFs::getPathRoot),
						gitFs.getCommitsGraph());
			}
		}
	}

	@Test
	void testRefs() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			JGit.createBasicRepo(repo);
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				final ImmutableSet<GitPathRoot> refPaths = gitFs.getRefs();
				assertEquals(1, refPaths.size());
				assertEquals("refs/heads/main", Iterables.getOnlyElement(refPaths).getGitRef());
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
				final ImmutableSet<GitPath> subEntriesAbsolute = ImmutableSet.of(
						gitFs.getRelativePath("dir").toAbsolutePath(),
						gitFs.getRelativePath("file1.txt").toAbsolutePath(),
						gitFs.getRelativePath("file2.txt").toAbsolutePath());
				assertEquals(subEntries, ImmutableSet.copyOf(gitFs.getRelativePath().newDirectoryStream(p -> true)));
				assertEquals(subEntries, Files.list(gitFs.getRelativePath()).collect(ImmutableSet.toImmutableSet()));
				assertEquals(subEntriesAbsolute,
						ImmutableSet.copyOf(gitFs.getRelativePath().toAbsolutePath().newDirectoryStream(p -> true)));
				assertEquals(subEntriesAbsolute,
						Files.list(gitFs.getRelativePath().toAbsolutePath()).collect(ImmutableSet.toImmutableSet()));
			}
		}
	}

}
