package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.time.Instant;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.graph.GraphBuilder;

import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.utils.Utils;

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
		assertThrows(FileSystemNotFoundException.class,
				() -> GitFileSystemProvider.getInstance().newFileSystemFromGitDir(noDir));
	}

	@Test
	void testNone() throws Exception {
		final Path emptyDir = Utils.getTempDirectory().resolve("test-" + Instant.now().toString());
		assertFalse(Files.exists(emptyDir));
		assertThrows(FileSystemNotFoundException.class,
				() -> FileSystems.newFileSystem(emptyDir, ClassLoader.getSystemClassLoader()));
	}

	@Test
	void testEmptyPath() throws Exception {
		final Path emptyDir = Utils.getTempUniqueDirectory("testEmpty");
		Files.createDirectories(emptyDir);
		assertTrue(Files.exists(emptyDir));
		assertThrows(ProviderNotFoundException.class,
				() -> FileSystems.newFileSystem(emptyDir, ClassLoader.getSystemClassLoader()));
	}

	@Test
	void testEmptyRepo() throws Exception {
		final Path emptyDir = Utils.getTempUniqueDirectory("test empty repo");
		try (FileRepository repository = (FileRepository) new FileRepositoryBuilder().setGitDir(emptyDir.toFile())
				.build()) {
			assertFalse(repository.getObjectDatabase().exists());
			assertFalse(repository.getRefDatabase().hasRefs());
			assertThrows(UnsupportedOperationException.class,
					() -> GitFileSystemProvider.getInstance().newFileSystemFromFileRepository(repository));
		}
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			assertTrue(repository.getObjectDatabase().exists());
			assertFalse(repository.getRefDatabase().hasRefs());
			final GitDfsFileSystem gitFs = GitFileSystemProvider.getInstance()
					.newFileSystemFromDfsRepository(repository);
			assertEquals(GraphBuilder.directed().build(), gitFs.getCommitsGraph());
		}
	}

	@Test
	void testFileRepo() throws Exception {
		try (FileSystem gitFs = FileSystems.newFileSystem(dir, ClassLoader.getSystemClassLoader())) {
			assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir));
			final URI expectedUri = UriBuilder.fromUri("gitjfs://FILE").path(dir + "/").build();
			assertEquals(expectedUri, ((GitFileSystem) gitFs).toUri());
			assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystem(expectedUri));
		}
		assertThrows(FileSystemNotFoundException.class,
				() -> GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir));
	}

	@Test
	void testFileRepoFromUri() throws Exception {
		final URI uri = UriBuilder.fromUri("gitjfs://FILE").path(dir + "/").build();
		try (FileSystem gitFs = FileSystems.newFileSystem(uri, ImmutableMap.of())) {
			assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
			assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystemFromGitDir(dir));
			assertEquals(uri, ((GitFileSystem) gitFs).toUri());
			assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystem(uri));
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
				assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystemFromRepositoryName(name));
				final URI expectedUri = URI.create("gitjfs://DFS/my/repo,@+%20space");
				assertEquals(expectedUri, ((GitFileSystem) gitFs).toUri());
				assertEquals(gitFs, GitFileSystemProvider.getInstance().getFileSystem(expectedUri));
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
