package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitUriTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUriTests.class);

	@Test
	void testUri() throws Exception {
		assertEquals(new URI("ssh://user@host.xz/path/to/repo.git"),
				new URI("ssh", "user", "host.xz", -1, "/path/to/repo.git", null, null));
		assertThrows(URISyntaxException.class, () -> new URI("git@github.com:oliviercailloux/testrel.git"));
		assertEquals(new URI("file:/path/to/repo.git"),
				new URI("file", null, null, -1, "/path/to/repo.git", null, null));
		assertEquals(new URI("file:/path/to/repo.git"), new URI("file:///path/to/repo.git"));
		assertEquals("file:/path/to/repo.git",
				new URI("file", null, null, -1, "/path/to/repo.git", null, null).toString());
		assertEquals("file:/path/to/repo.git",
				new URI("file", "", null, -1, "/path/to/repo.git", null, null).toString());
		assertEquals("file:/path/to/repo.git", new URI("file:/path/to/repo.git").toString());
		assertEquals("file:///path/to/repo.git", new URI("file:///path/to/repo.git").toString());
		assertEquals(null, new URI("file:/path/to/repo.git").getAuthority());
		assertEquals(null, new URI("file:///path/to/repo.git").getAuthority());
		assertThrows(URISyntaxException.class,
				() -> new URI("http", null, "nonrelativepath", null).normalize().getPath());
	}

	@Test
	void testUrisSsh() throws Exception {
		final GitUri uri = GitUri.fromUri(URI.create("ssh://user@host.xz/path/to/repo.git/"));
		assertEquals(URI.create("ssh://user@host.xz/path/to/repo.git/"), uri.asUri());
		assertEquals(uri, GitUri.fromGitUrl("ssh://user@host.xz/path/to/repo.git/"));
		assertEquals(GitUri.fromUri(URI.create("ssh://file/something")), GitUri.fromGitUrl("file:/something"));
	}

	@Test
	void testUrisFile() throws Exception {
		final GitUri uri = GitUri.fromUri(URI.create("file:///path/to/repo.git/"));
		assertEquals(URI.create("file:///path/to/repo.git/"), uri.asUri());
		assertEquals("file:///path/to/repo.git/", uri.asString());
		assertEquals(uri, GitUri.fromGitUrl("file:///path/to/repo.git/"));
		assertEquals(URI.create("file:///path/to/repo.git"), GitUri.fromGitUrl("/path/to/repo.git/").asUri());
		assertEquals(URI.create("file:///"), GitUri.fromGitUrl("file:///").asUri());
		assertEquals(URI.create("file:///"), GitUri.fromUri(URI.create("file:/")).asUri());
		assertEquals("file:///", GitUri.fromUri(URI.create("file:/")).asString());
		assertEquals("file:///", GitUri.fromUri(URI.create("file:/")).asUri().toString());
		assertEquals("file:///somepath", GitUri.fromUri(URI.create("file:/somepath")).asUri().toString());
		assertEquals("file:///somepath/", GitUri.fromUri(URI.create("file:/somepath/")).asString());
	}

	@Test
	void testUrisHttp() throws Exception {
		final GitUri uri = GitUri.fromUri(URI.create("http://host/path/to/repo.git/"));
		assertEquals(URI.create("http://host/path/to/repo.git/"), uri.asUri());
		assertEquals(uri, GitUri.fromGitUrl("http://host/path/to/repo.git/"));
	}

	@Test
	void testUrisHttpShort() throws Exception {
		final GitUri uri = GitUri.fromUri(URI.create("http://host/repo"));
		assertEquals(URI.create("http://host/repo"), uri.asUri());
		assertEquals(uri, GitUri.fromGitUrl("http://host/repo"));
	}

	@Test
	void testUrisScp() throws Exception {
		final GitUri uri = GitUri.fromGitUrl("git@github.com:username/repo.git");
		assertEquals(URI.create("ssh://git@github.com/username/repo.git"), uri.asUri());
		assertEquals("ssh://git@github.com/username/repo.git", uri.asString());
	}

	@Test
	void testUrisIllegal() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromUri(URI.create("file:./path/to/repo.git/")));
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromGitUrl("./path/to/repo.git/"));
		assertThrows(IllegalArgumentException.class, () -> GitUri.fromUri(URI.create("file:path/to/repo.git/")));
	}
}
