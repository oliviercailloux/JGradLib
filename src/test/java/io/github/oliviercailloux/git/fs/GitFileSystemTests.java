package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GitFileSystemTests {
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
			assertEquals(URI.create("gitfs:/path/to/gitdir?revStr=master&dirAndFile=%2Ftruc"),
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
	void testReadNonExistingFile() throws Exception {
		final Path gitDir = Path.of("git-test-read " + Instant.now());
		Files.createDirectory(gitDir);
		try (Repository repo = new FileRepository(gitDir.toString())) {
			JGitUsage.createBasicRepo(repo);
		}
//		try (GitFileSystem gitFS = GitFileSystem.given(Mockito.mock(GitFileSystemProvider.class), gitDir)) {
		try (GitFileSystem gitFS = new GitFileSystemProvider().newFileSystemFromGitDir(gitDir)) {
			assertThrows(FileNotFoundException.class,
					() -> gitFS.newByteChannel(gitFS.getPath("master/", "/ploum.txt")));
			final GitPath path = gitFS.getPath("master/", "/file1.txt");
//			final SeekableByteChannel newByteChannel = gitFS.newByteChannel(path);
//			newByteChannel.
			final String content1 = Files.readString(path);
			assertEquals("Hello, world", content1);
		}
	}
}
