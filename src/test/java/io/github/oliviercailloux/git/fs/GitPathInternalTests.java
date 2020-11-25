package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.UrlEscapers;

import io.github.oliviercailloux.git.JGit;

/**
 * Tests that do not access the underlying git repository, just deal with git
 * paths internally (resolving, transforming to string…)
 */
public class GitPathInternalTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathInternalTests.class);

	@Test
	void testBasics() throws Exception {
		final GitRev main = GitRev.DEFAULT;
		assertThrows(IllegalArgumentException.class, () -> getGitPath(main, ""));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(main, "ploum"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(null, "/"));
		assertThrows(IllegalArgumentException.class, () -> getGitPath(null, "/truc"));

		assertEquals("", getGitPath(null, "").toString());
		assertEquals("ploum", getGitPath(null, "ploum").toString());
		assertEquals("truc/chose", getGitPath(null, "truc/chose").toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").toString());
		assertEquals("/refs/heads/main//ploum", getGitPath(main, "/ploum").toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(main, "/truc/chose").toString());

		assertFalse(getGitPath(null, "").isAbsolute());
		assertFalse(getGitPath(null, "truc/chose").isAbsolute());
		assertTrue(getGitPath(main, "/").isAbsolute());
		assertTrue(getGitPath(main, "/truc/chose").isAbsolute());

		assertEquals("/refs/heads/main//", getGitPath(main, "/").getRoot().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc/chose").getRoot().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc.txt").getRoot().toString());
		assertNull(getGitPath(null, "truc.txt").getRoot());

		assertEquals(0, getGitPath(main, "/").getNameCount());
		assertEquals(1, getGitPath(main, "/truc.txt").getNameCount());
		assertEquals(2, getGitPath(main, "/truc/chose").getNameCount());
		assertEquals(1, getGitPath(null, "").getNameCount());
		assertEquals(1, getGitPath(null, "truc").getNameCount());
		assertEquals(2, getGitPath(null, "truc/chose").getNameCount());

		assertEquals("", getGitPath(null, "").getFileName().toString());
		assertEquals("chose", getGitPath(null, "truc/chose").getFileName().toString());
		assertEquals("chose", getGitPath(main, "/truc/chose").getFileName().toString());
		assertNull(getGitPath(main, "/").getFileName());

		assertNull(getGitPath(main, "/").getParent());
		assertEquals("truc", getGitPath(null, "truc/chose").getParent().toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc").getParent().toString());
		assertEquals("/refs/heads/main//truc", getGitPath(main, "/truc/chose").getParent().toString());

		assertEquals("", getGitPath(null, "").resolve(getGitPath(null, "")).toString());
		assertEquals("ploum.txt", getGitPath(null, "").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(null, "").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("truc", getGitPath(null, "truc").resolve(getGitPath(null, "")).toString());
		assertEquals("truc/ploum.txt", getGitPath(null, "truc").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "truc").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(null, "truc").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").resolve(getGitPath(null, "")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//",
				getGitPath(GitRev.ref("refs/something"), "/").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(GitRev.ref("refs/something"), "/").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//truc", getGitPath(main, "/truc").resolve(getGitPath(null, "")).toString());
		assertEquals("/refs/heads/main//truc/ploum.txt",
				getGitPath(main, "/truc").resolve(getGitPath(null, "ploum.txt")).toString());
		assertEquals("/refs/heads/main//", getGitPath(main, "/truc").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(main, "/truc").resolve(getGitPath(main, "/ploum.txt")).toString());
		assertEquals("/refs/heads/main//",
				getGitPath(GitRev.ref("refs/something"), "/truc").resolve(getGitPath(main, "/")).toString());
		assertEquals("/refs/heads/main//ploum.txt",
				getGitPath(GitRev.ref("refs/something"), "/truc").resolve(getGitPath(main, "/ploum.txt")).toString());

		assertEquals("", getGitPath(null, "").relativize(getGitPath(null, "")).toString());
		assertEquals("chose", getGitPath(null, "truc").relativize(getGitPath(null, "truc/chose")).toString());
		assertEquals("../../stuff", getGitPath(null, "truc/chose").relativize(getGitPath(null, "stuff")).toString());
		assertEquals("../stuff", getGitPath(main, "/truc").relativize(getGitPath(main, "/stuff")).toString());
		assertEquals("", getGitPath(main, "/truc").relativize(getGitPath(main, "/truc")).toString());
		assertEquals("chose", getGitPath(main, "/truc").relativize(getGitPath(main, "/truc/chose")).toString());
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath(main, "/truc").relativize(getGitPath(null, "/truc/chose")));
		assertThrows(IllegalArgumentException.class,
				() -> getGitPath(null, "/truc").relativize(getGitPath(main, "/truc/chose")));

		assertEquals("/refs/heads/main//", getGitPath(main, "/").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(main, "/truc/chose").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//", getGitPath(null, "").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc/chose", getGitPath(null, "truc/chose").toAbsolutePath().toString());
		assertEquals("/refs/heads/main//truc.txt", getGitPath(null, "truc.txt").toAbsolutePath().toString());

		assertEquals(getGitPath(null, "truc/chose"), getGitPath(null, "truc/chose"));
		assertEquals(getGitPath(main, "/truc/chose"), getGitPath(main, "/truc/chose"));

		assertNotEquals(getGitPath(null, ""), getGitPath(main, "/"));
		assertNotEquals(getGitPath(null, "truc/chose"), getGitPath(main, "/truc/chose"));
	}

	private GitPath getGitPath(GitRev root, String dirAndFile) {
		if (root != null) {
			return GitAbsolutePath.givenRev(GitFileSystemCreatePathsTests.GIT_FILE_FILE_SYSTEM_MOCKED, root,
					GitFileSystem.JIM_FS.getPath(dirAndFile));
		}
		return GitRelativePath.relative(GitFileSystemCreatePathsTests.GIT_FILE_FILE_SYSTEM_MOCKED,
				GitFileSystem.JIM_FS.getPath(dirAndFile));
	}

	/**
	 * Attempts of encoding and decoding, independent of Git Path (in search of a
	 * correct solution).
	 */
	@Test
	void testEncodingQueryPart() throws Exception {
		final String orig = "slash/and&space colon:stop.question?plus+backs\\percent%";
		{
			final String escaped = UrlEscapers.urlPathSegmentEscaper().escape(orig);
			final URI uri = new URI("scheme:/" + escaped);
			final String decoded = uri.getPath();
			assertEquals("/" + orig, decoded);
			LOGGER.debug("Escaped: {}.", escaped);
		}
		{
			final URI uri = new URI("scheme:/?param1=and%26&param2=v2");
			assertEquals("param1=and&&param2=v2", uri.getQuery());
			assertEquals("param1=and%26&param2=v2", uri.getRawQuery());
		}
		{
			final URI uri = UriBuilder.fromUri("scheme:/").queryParam("param1", "and%26").queryParam("param2", "v2")
					.build();
			assertEquals("param1=and&&param2=v2", uri.getQuery());
		}
		{
			final String escapedValue = QueryUtils.QUERY_ENTRY_ESCAPER.escape(orig);
			LOGGER.info("Escaped: {}.", escapedValue);
			final URI uri = new URI("scheme:/?param1=" + escapedValue + "&param2=v2");
			final Map<String, String> decoded = QueryUtils.splitQuery(uri);
			assertEquals(orig, decoded.get("param1"));
		}
	}

	@Test
	void testEncodingPath() throws Exception {
		assertEquals("/path/", URI.create("scheme:/path%2F").getPath());
	}

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
						() -> gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/"));
				assertTrue(gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/heads/main/"));
			}
		}
	}
}
