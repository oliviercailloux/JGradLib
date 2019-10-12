package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DoubleGitUriTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DoubleGitUriTests.class);

	@Test
	void testUri() throws Exception {
		assertEquals(new URI("ssh://user@host.xz/path/to/repo.git"),
				new URI("ssh", "user", "host.xz", -1, "/path/to/repo.git", null, null));
		assertThrows(URISyntaxException.class, () -> new URI("git@github.com:oliviercailloux/testrel.git"));
		assertEquals(new URI("file:///path/to/repo.git"),
				new URI("file", null, null, -1, "/path/to/repo.git", null, null));
		assertThrows(URISyntaxException.class,
				() -> new URI("http", null, "nonrelativepath", null).normalize().getPath());
	}

	@Test
	void testDoubleUrisSsh() throws Exception {
		final DoubleGitUri uris = DoubleGitUri
				.fromGitFsUri(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"));
		assertEquals(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"), uris.getGitFsUri());
		assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh://user@host.xz/path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("ssh://user@host.xz/path/to/repo.git/"));
	}

	@Test
	void testDoubleUrisSshImplicit() throws Exception {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs://user@host.xz/path/to/repo.git/"));
		assertEquals(URI.create("gitfs://user@host.xz/path/to/repo.git/?git-scheme=ssh"), uris.getGitFsUri());
		assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh://user@host.xz/path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("ssh://user@host.xz/path/to/repo.git/"));
	}

	@Test
	void testDoubleUrisFile() throws Exception {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs:/path/to/repo.git/?git-scheme=file"));
		assertEquals(URI.create("gitfs:/path/to/repo.git/?git-scheme=file"), uris.getGitFsUri());
		assertEquals("file:///path/to/repo.git/", uris.getGitString());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitFsUri(URI.create("gitfs:/./path/to/repo.git/?git-scheme=file")));
		assertEquals(uris, DoubleGitUri.fromGitFsUri(URI.create("gitfs://./path/to/repo.git/?git-scheme=file")));
		assertEquals(uris, DoubleGitUri.fromGitFsUri(URI.create("gitfs:///path/to/repo.git/?git-scheme=file")));
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("file:///path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("file:///path/to/repo.git/"));
		assertEquals(uris, DoubleGitUri.fromGitUrl("/path/to/repo.git/"));
	}

	@Test
	void testDoubleUrisFileImplicit() throws Exception {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs:/path/to/repo.git/"));
		assertEquals(URI.create("gitfs:/path/to/repo.git/?git-scheme=file"), uris.getGitFsUri());
		assertEquals("file:///path/to/repo.git/", uris.getGitString());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitFsUri(URI.create("gitfs:///path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("file:///path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("file:///path/to/repo.git/"));
	}

	@Test
	void testDoubleUrisHttp() throws Exception {
		final DoubleGitUri uris = DoubleGitUri
				.fromGitFsUri(URI.create("gitfs://host/path/to/repo.git/?git-scheme=http"));
		assertEquals(URI.create("gitfs://host/path/to/repo.git/?git-scheme=http"), uris.getGitFsUri());
		assertEquals(URI.create("http://host/path/to/repo.git/"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("http://host/path/to/repo.git/")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("http://host/path/to/repo.git/"));
	}

	@Test
	void testDoubleUrisHttpShort() throws Exception {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(URI.create("gitfs://host/repo?git-scheme=http"));
		assertEquals(URI.create("gitfs://host/repo?git-scheme=http"), uris.getGitFsUri());
		assertEquals(URI.create("http://host/repo"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("http://host/repo")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("http://host/repo"));
	}

	@Test
	void testDoubleUrisScp() throws Exception {
		final DoubleGitUri uris = DoubleGitUri.fromGitUrl("git@github.com:username/repo.git");
		assertEquals(URI.create("gitfs:git@github.com:username/repo.git"), uris.getGitFsUri());
		assertEquals(URI.create("ssh://git@github.com/~git/username/repo.git"), uris.getGitHierarchicalUri());
		assertEquals("git@github.com:username/repo.git", uris.getGitString());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, DoubleGitUri.fromGitFsUri(URI.create("gitfs:git@github.com:username/repo.git")));
		assertEquals(uris, DoubleGitUri.fromGitUri(URI.create("ssh:git@github.com:username/repo.git")));
		assertEquals(uris, DoubleGitUri.fromGitUrl("ssh:git@github.com:username/repo.git"));
	}

	@Test
	void testDoubleUrisIllegal() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs:/path/to/repo.git?git-scheme=invalid")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs:path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs://host.xz/path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs://user@host.xz/path/to/repo.git/?git-scheme=http")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs:///path/to/repo.git/?git-scheme=http")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs://host?git-scheme=http")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(new URI("gitfs://host/?git-scheme=http")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs:./path/to/repo.git/?git-scheme=file")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitUri(URI.create("file:./path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class, () -> DoubleGitUri.fromGitUrl("file:./path/to/repo.git/"));
		assertThrows(IllegalArgumentException.class, () -> DoubleGitUri.fromGitUrl("./path/to/repo.git/"));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitFsUri(URI.create("gitfs:path/to/repo.git/?git-scheme=file")));
		assertThrows(IllegalArgumentException.class,
				() -> DoubleGitUri.fromGitUri(URI.create("file:path/to/repo.git/")));
	}
}
