package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

class DoubleGitUriTests {

	@Test
	void testUri() throws Exception {
		assertEquals(new URI("ssh://user@host.xz/path/to/repo.git"),
				new URI("ssh", "user", "host.xz", -1, "/path/to/repo.git", null, null));
		assertEquals(new URI("file:///path/to/repo.git"),
				new URI("file", null, null, -1, "/path/to/repo.git", null, null));
	}

	@Test
	void testDoubleUris() throws Exception {
		/**
		 * It has syntax
		 * autograde://host.xz[:port]/path/to/repo.git[/]?git-scheme=access-scheme,
		 * where access-scheme is ssh, git, http or https, and with the git-cloneable
		 * equivalent being access-scheme://host.xz[:port]/path/to/repo.git[/], or
		 * autograde://user@host.xz/path/to/repo.git[/][?git-scheme=ssh], with
		 * git-clonable equivalent ssh://user@host.xz/path/to/repo.git[/], or
		 * autograde:///path/to/repo.git[/][?git-scheme=file], with git-cloneable
		 * equivalent file:///path/to/repo.git[/].
		 */
		{
			final DoubleGitUri uris = DoubleGitUri
					.fromGitFsUri(URI.create("gitfs://host.xz/path/to/repo.git/?git-scheme=ssh"));
			assertEquals(URI.create("gitfs://host.xz/path/to/repo.git/?git-scheme=ssh"), uris.getGitFsUri());
			assertEquals(URI.create("ssh://host.xz/path/to/repo.git/"), uris.getGitUri());
			assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh://host.xz/path/to/repo.git/")));
			assertEquals("repo", uris.getRepositoryName());
		}
		{
			final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs://user@host.xz/path/to/repo.git/"));
			assertEquals(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"), uris.getGitFsUri());
			assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uris.getGitUri());
			assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh://user@host.xz/path/to/repo.git/")));
		}
		{
			final DoubleGitUri uris = DoubleGitUri
					.fromGitFsUri(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"));
			assertEquals(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"), uris.getGitFsUri());
			assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uris.getGitUri());
			assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh://user@host.xz/path/to/repo.git/")));
		}
		{
			final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs:///path/to/repo.git/"));
			assertEquals(URI.create("gitfs:///path/to/repo.git/?git-scheme=file"), uris.getGitFsUri());
			assertEquals(URI.create("file:///path/to/repo.git/"), uris.getGitUri());
			assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("file:///path/to/repo.git/")));
		}
		{
			final DoubleGitUri uris = DoubleGitUri
					.fromGitFsUri(URI.create("gitfs:///path/to/repo.git/?git-scheme=file"));
			assertEquals(URI.create("gitfs:///path/to/repo.git/?git-scheme=file"), uris.getGitFsUri());
			assertEquals(URI.create("file:///path/to/repo.git/"), uris.getGitUri());
			assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("file:///path/to/repo.git/")));
		}

		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs:/path/to/repo.git?git-scheme=unknown")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs:path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs://host.xz/path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=http")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs:///path/to/repo.git/?git-scheme=http")));

	}
}
