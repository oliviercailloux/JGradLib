package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.InvalidPathException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests of git file system not accessing the underlying git repository: create
 * paths.
 */
public class GitFileSystemCreatePathsTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemCreatePathsTests.class);
	/**
	 * We don’t mock directly {@link GitFileFileSystem} because the tested code may
	 * use its {@link GitFileSystem#mainSlash} field.
	 */
	static final GitFileFileSystem GIT_FILE_FILE_SYSTEM_MOCKED = GitFileFileSystem.givenOurRepository(mockProvider(),
			mockFileRepository());

	private static GitFileSystemProvider mockProvider() {
		LOGGER.info("Mocking provider.");
		final GitFileSystemProvider mocked = Mockito.mock(GitFileSystemProvider.class);
		Mockito.when(mocked.getGitFileSystems()).thenReturn(Mockito.mock(GitFileSystems.class));
		LOGGER.info("Mocked provider.");
		return mocked;
	}

	private static FileRepository mockFileRepository() {
		LOGGER.info("Mocking file repo.");
		final FileRepository mocked = Mockito.mock(FileRepository.class);
		Mockito.when(mocked.newObjectReader()).thenReturn(Mockito.mock(ObjectReader.class));
		Mockito.when(mocked.getDirectory()).thenReturn(Mockito.mock(File.class));
		LOGGER.info("Mocked file repo.");
		return mocked;
	}

	@Test
	void testCreatePaths() throws Exception {
		try (GitFileSystem gitFs = GIT_FILE_FILE_SYSTEM_MOCKED) {
//			assertEquals("", gitFs.getRelativePath().toString());
//			assertEquals("", gitFs.getPath("").toString());
//			assertEquals("", gitFs.getPath("", "", "").toString());
			assertEquals("truc", gitFs.getPath("truc").toString());
			assertEquals("truc", gitFs.getPath("", "truc").toString());
			assertEquals("dir/sub", gitFs.getPath("dir", "sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir", "/sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir/", "sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir/", "/sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir//", "sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir//", "/sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir///", "sub").toString());
			assertEquals("dir/sub", gitFs.getPath("dir///", "/sub").toString());
			assertEquals("dir/sub/a", gitFs.getPath("dir", "/", "sub", "", "a").toString());

			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main/").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main/", "").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main/", "/").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main/", "/", "").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main/", "/", "/", "dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main/", "dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main/", "/dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main/", "dir", "").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main/", "dir", "/sub").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main/", "dir", "/", "sub").toString());

			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main//").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main//", "").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main//", "/").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main//", "/", "").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main//", "/", "/", "dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main//", "dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main//", "/dir").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main//", "dir", "/sub").toString());
			assertEquals("/refs/heads/main//dir/sub",
					gitFs.getPath("/refs/heads/main//", "dir", "/", "sub").toString());

			final String zeroStr = "/0000000000000000000000000000000000000000/";

			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr).toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr, "").toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr, "/").toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr, "/", "").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr, "/", "/", "dir").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr, "dir").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr, "/dir").toString());
			assertEquals(zeroStr + "/dir/sub", gitFs.getPath(zeroStr, "dir", "/sub").toString());
			assertEquals(zeroStr + "/dir/sub", gitFs.getPath(zeroStr, "dir", "/", "sub").toString());

			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr + "/").toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr + "/", "").toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr + "/", "/").toString());
			assertEquals(zeroStr + "/", gitFs.getPath(zeroStr + "/", "/", "").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr + "/", "/", "/", "dir").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr + "/", "dir").toString());
			assertEquals(zeroStr + "/dir", gitFs.getPath(zeroStr + "/", "/dir").toString());
			assertEquals(zeroStr + "/dir/sub", gitFs.getPath(zeroStr + "/", "dir", "/sub").toString());
			assertEquals(zeroStr + "/dir/sub", gitFs.getPath(zeroStr + "/", "dir", "/", "sub").toString());

			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main///").toString());
			assertEquals("/refs/heads/main//", gitFs.getPath("/refs/heads/main////").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main//dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main///dir").toString());
			assertEquals("/refs/heads/main//dir", gitFs.getPath("/refs/heads/main////dir").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main//dir/sub").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main//dir/sub", "").toString());
			assertEquals("/refs/heads/main//dir/sub", gitFs.getPath("/refs/heads/main//dir/sub", "", "/").toString());
			assertEquals("/refs/heads/main//dir/sub/a",
					gitFs.getPath("/refs/heads/main//dir/sub", "", "/", "a").toString());

			assertThrows(InvalidPathException.class, () -> gitFs.getRelativePath("/").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getRelativePath("/", "dir").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getRelativePath("", "/", "dir").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getRelativePath("", "/dir").toString());

			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("//").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/invalid/").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/invalid//").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/refs/").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/refs//").toString());
			assertThrows(InvalidPathException.class, () -> gitFs.getAbsolutePath("/refs//refs//").toString());
		}
	}
}
