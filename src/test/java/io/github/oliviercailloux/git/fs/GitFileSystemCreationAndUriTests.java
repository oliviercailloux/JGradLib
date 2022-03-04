package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.graph.GraphBuilder;
import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.utils.Utils;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests creation and URI of git file systems and paths.
 */
class GitFileSystemCreationAndUriTests {
	private static Path dir;

	@BeforeAll
	static void initGitRepo() throws Exception {
		dir = Utils.getTempUniqueDirectory("testRepo");
		try (Repository repo = new FileRepository(dir.toFile())) {
			JGit.createBasicRepo(repo);
		}
	}

	@Test
	void testNoSystemThere() {
		final Path noDir = Utils.getTempDirectory().resolve("test-" + Instant.now().toString());
		assertFalse(Files.exists(noDir));
		/*
		 * I believe that FileSystems.newFileSystem(noDir,
		 * ClassLoader.getSystemClassLoader()) threw FileSystemNotFoundException under
		 * Java 11; under Java 17, this throws NoSuchFileException, an arguably better
		 * choice.
		 */
		assertThrows(NoSuchFileException.class,
				() -> GitFileSystemProvider.getInstance().newFileSystemFromGitDir(noDir));
	}

	@Test
	void testEmptyRepo() throws Exception {
		final Path emptyDir = Utils.getTempUniqueDirectory("test empty repo");
		final Repository built = new FileRepositoryBuilder().setGitDir(emptyDir.toFile()).build();
		try (FileRepository repository = (FileRepository) built) {
			assertFalse(repository.getObjectDatabase().exists());
			assertFalse(repository.getRefDatabase().hasRefs());
			assertThrows(UnsupportedOperationException.class,
					() -> GitFileSystemProvider.getInstance().newFileSystemFromFileRepository(repository));
		}
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			assertTrue(repository.getObjectDatabase().exists());
			assertFalse(repository.getRefDatabase().hasRefs());
			try (GitDfsFileSystem gitFs = GitFileSystemProvider.getInstance()
					.newFileSystemFromDfsRepository(repository)) {
				assertEquals(GraphBuilder.directed().build(), gitFs.getCommitsGraph());
			}
		}
	}

	@Test
	void testFileRepo() throws Exception {
		try (FileSystem gitFs = FileSystems.newFileSystem(dir, ClassLoader.getSystemClassLoader())) {
			assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			@SuppressWarnings("resource")
			final GitFileFileSystem obtained = GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir);
			assertEquals(gitFs, obtained);
			final URI expectedUri = UriBuilder.fromUri("gitjfs://FILE").path(dir + "/").build();
			assertEquals(expectedUri, ((GitFileSystem) gitFs).toUri());
			@SuppressWarnings("resource")
			final GitFileSystem obtained2 = GitFileSystemProvider.getInstance().getFileSystem(expectedUri);
			assertEquals(gitFs, obtained2);
		}
		assertThrows(FileSystemNotFoundException.class,
				() -> GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir));
	}

	@Test
	void testFileRepoFromUri() throws Exception {
		final URI uri = UriBuilder.fromUri("gitjfs://FILE").path(dir + "/").build();
		try (FileSystem gitFs = FileSystems.newFileSystem(uri, ImmutableMap.of())) {
			assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			@SuppressWarnings("resource")
			final GitFileFileSystem obtained = GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir);
			assertEquals(gitFs, obtained);
			assertEquals(uri, ((GitFileSystem) gitFs).toUri());
			@SuppressWarnings("resource")
			final GitFileSystem obtained2 = GitFileSystemProvider.getInstance().getFileSystem(uri);
			assertEquals(gitFs, obtained2);
		}
		assertThrows(FileSystemNotFoundException.class,
				() -> GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir));
	}

	@Test
	void testMemRepo() throws Exception {
		final String name = "my/repo,@+ space";
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription(name))) {
			JGit.createBasicRepo(repo);
			try (FileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repo)) {
				assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
				@SuppressWarnings("resource")
				final GitDfsFileSystem obtained = GitFileSystemProvider.getInstance()
						.getFileSystemFromRepositoryName(name);
				assertEquals(gitFs, obtained);
				final URI expectedUri = URI.create("gitjfs://DFS/my/repo,@+%20space");
				assertEquals(expectedUri, ((GitFileSystem) gitFs).toUri());
				@SuppressWarnings("resource")
				final GitFileSystem obtained2 = GitFileSystemProvider.getInstance().getFileSystem(expectedUri);
				assertEquals(gitFs, obtained2);
			}
			assertThrows(FileSystemNotFoundException.class,
					() -> GitFileSystemProvider.getInstance().getFileSystemFromRepositoryName(name));
		}
	}

	@Test
	void testDoubleMemRepo() throws Exception {
		final String name = "sdqmj{~{}#@}#";
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription(name))) {
			JGit.createBasicRepo(repo);
			try (FileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repo)) {
				assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			}
			assertThrows(FileSystemNotFoundException.class,
					() -> GitFileSystemProvider.getInstance().getFileSystemFromRepositoryName(name));
		}
		/*
		 * Let’s do it again to make sure that the first one is no more in memory (name
		 * can be reused).
		 */
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription(name))) {
			JGit.createBasicRepo(repo);
			try (FileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repo)) {
				assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			}
			assertThrows(FileSystemNotFoundException.class,
					() -> GitFileSystemProvider.getInstance().getFileSystemFromRepositoryName(name));
		}
	}

	@Test
	void testPathUri() throws Exception {
		final URI repoUri = UriBuilder.fromUri("gitjfs://FILE").path(dir + "/").build();
		final String uriBasis = repoUri.toString();
		try (GitFileFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromGitDir(dir)) {
			assertEquals(uriBasis, gitFs.getRelativePath("").toUri().toString());
			assertEquals(uriBasis + "?internal-path=a", gitFs.getRelativePath("a").toUri().toString());
			assertEquals(uriBasis + "?internal-path=dir/sub", gitFs.getRelativePath("dir/sub").toUri().toString());
			assertEquals("internal-path=and%26equals%3Dquestion?",
					gitFs.getRelativePath("and&equals=question?").toUri().getRawQuery());
			assertEquals(uriBasis + "?internal-path=and%26equals%3Dquestion?",
					gitFs.getRelativePath("and&equals=question?").toUri().toString());

			final String zeroStr = "/0000000000000000000000000000000000000000/";
			assertEquals(uriBasis + "?root=" + zeroStr + "&internal-path=/",
					gitFs.getPathRoot(ObjectId.zeroId()).toUri().toString());
			assertEquals(uriBasis + "?root=" + zeroStr + "&internal-path=/",
					gitFs.getAbsolutePath(ObjectId.zeroId(), "/").toUri().toString());
			assertEquals(uriBasis + "?root=" + zeroStr + "&internal-path=/dir/sub",
					gitFs.getAbsolutePath(ObjectId.zeroId(), "/dir/sub").toUri().toString());

			assertEquals(uriBasis + "?root=/refs/heads/main/&internal-path=/",
					gitFs.getAbsolutePath("/refs/heads/main/").toUri().toString());
			assertEquals(uriBasis + "?root=/refs/heads/main/&internal-path=/",
					gitFs.getAbsolutePath("/refs/heads/main/", "/").toUri().toString());
			assertEquals(uriBasis + "?root=/refs/heads/main/&internal-path=/dir/sub",
					gitFs.getAbsolutePath("/refs/heads/main/", "/dir/sub").toUri().toString());
			assertEquals(uriBasis + "?root=/refs/tags/and%26equals%3Dquestion?/&internal-path=/",
					gitFs.getAbsolutePath("/refs/tags/and&equals=question?/").toUri().toString());
		}
	}
}
