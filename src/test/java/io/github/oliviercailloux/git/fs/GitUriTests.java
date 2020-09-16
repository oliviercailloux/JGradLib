package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.GitUri;

class GitUriTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUriTests.class);

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
	void testUrisSsh() throws Exception {
		final GitUri uris = GitUri.fromGitUri(URI.create("ssh://user@host.xz/path/to/repo.git/"));
		assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, GitUri.fromGitUrl("ssh://user@host.xz/path/to/repo.git/"));
	}

	@Test
	void testUrisFile() throws Exception {
		final GitUri uris = GitUri.fromGitUri(URI.create("file:///path/to/repo.git/"));
		assertEquals("file:///path/to/repo.git/", uris.getGitString());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, GitUri.fromGitUrl("file:///path/to/repo.git/"));
		assertEquals(uris, GitUri.fromGitUrl("/path/to/repo.git/"));
	}

	@Test
	void testUrisHttp() throws Exception {
		final GitUri uris = GitUri.fromGitUri(URI.create("http://host/path/to/repo.git/"));
		assertEquals(URI.create("http://host/path/to/repo.git/"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, GitUri.fromGitUrl("http://host/path/to/repo.git/"));
	}

	@Test
	void testUrisHttpShort() throws Exception {
		final GitUri uris = GitUri.fromGitUri(URI.create("http://host/repo"));
		assertEquals(URI.create("http://host/repo"), uris.getGitHierarchicalUri());
		assertEquals("repo", uris.getRepositoryName());
		assertEquals(uris, GitUri.fromGitUrl("http://host/repo"));
	}

	@Test
	void testUrisScp() throws Exception {
		final GitUri uris = GitUri.fromGitUrl("git@github.com:username/repo.git");
		assertEquals(URI.create("ssh://git@github.com/username/repo.git"), uris.getGitHierarchicalUri());
		assertEquals("git@github.com:username/repo.git", uris.getGitString());
		assertEquals("repo", uris.getRepositoryName());
	}

	@Test
	void testUrisIllegal() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromGitUri(URI.create("file:./path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromGitUrl("file:./path/to/repo.git/"));
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromGitUrl("./path/to/repo.git/"));
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromGitUri(URI.create("file:path/to/repo.git/")));
	}
}
