package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GitFileSystemTests {
	@Test
	void testPaths() throws Exception {
		try (GitFileSystem gitFS = new GitFileSystem(Mockito.mock(GitFileSystemProvider.class),
				Path.of("/path/to/gitdir"))) {
			assertEquals("", gitFS.getPath("").toString());
			assertEquals("", gitFS.getPath("", "", "").toString());
			assertEquals("truc", gitFS.getPath("", "truc").toString());
			assertEquals("master//truc", gitFS.getPath("master/", "/truc").toString());
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
}
