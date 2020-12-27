package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitUrlKindTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUrlKindTests.class);

	@Test
	void testUrlKind() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given(""));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given(":"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given(":something"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given("ssh://"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given("https:///host/"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given("unknownscheme://host"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given("file://"));
		assertThrows(IllegalArgumentException.class, () -> GitUrlKind.given("file://something"));

		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("ssh://host/"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("ssh://host/path"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("ssh://user@host/"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("ssh://user@host:port/"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("ssh://user@host:port/"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("file:///"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("file:///path"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("file:///path/subpath"));
		assertEquals(GitUrlKind.SCHEME, GitUrlKind.given("file:///path/subpath/"));

		assertEquals(GitUrlKind.SCP, GitUrlKind.given("host:"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("host:path"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("host:/path"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("host:/path/"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("file:"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("file:/"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("file:/something"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("user@host:"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("user@host:path"));
		assertEquals(GitUrlKind.SCP, GitUrlKind.given("user@host:/path/"));

		assertEquals(GitUrlKind.ABSOLUTE_PATH, GitUrlKind.given("/"));
		assertEquals(GitUrlKind.ABSOLUTE_PATH, GitUrlKind.given("/absolute"));
		assertEquals(GitUrlKind.ABSOLUTE_PATH, GitUrlKind.given("/absolute/path"));

		assertEquals(GitUrlKind.RELATIVE_PATH, GitUrlKind.given(" "));
		assertEquals(GitUrlKind.RELATIVE_PATH, GitUrlKind.given("relative"));
		assertEquals(GitUrlKind.RELATIVE_PATH, GitUrlKind.given("./foo:bar"));
	}
}
