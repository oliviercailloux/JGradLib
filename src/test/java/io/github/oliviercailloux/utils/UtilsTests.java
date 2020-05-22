package io.github.oliviercailloux.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FilePermission;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Policy;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.security.SandboxSecurityPolicy;

class UtilsTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(UtilsTests.class);

	public static void main(String[] args) throws Exception {
		final String file = "/home/olivier/Local/article.tex";
//		LOGGER.info("Security manager: {}.", System.getSecurityManager());
//		try {
//			AccessController.checkPermission(new FilePermission(file, "read"));
//		} catch (AccessControlException e) {
//			LOGGER.info("Caught exc.", e);
//		}
//		LOGGER.info("Policy: {}.", Policy.getPolicy());

		/**
		 * Need to be set twice:
		 * https://stackoverflow.com/questions/31458821/policy-setpolicy-doesnt-seem-to-work-properly
		 */
		final SandboxSecurityPolicy policy = new SandboxSecurityPolicy();
		Policy.setPolicy(policy);
		Policy.setPolicy(policy);
		System.setSecurityManager(new SecurityManager());
		LOGGER.info("Security manager: {}.", System.getSecurityManager());
		try {
			AccessController.checkPermission(new FilePermission(file, "read"));
		} catch (AccessControlException e) {
			LOGGER.info("Caught exc.", e);
		}
		LOGGER.info("Policy: {}.", Policy.getPolicy());

		Files.readString(Path.of(file));

		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path source = Path.of("src/main/resources/");
			final Path target = jimFs.getPath("/target/");
			Files.createDirectory(target);
			Utils.copyRecursively(source, target);
			assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
		}
	}

	@Test
	void testQuery() throws Exception {
		final String queryStr = "first=first value&second=(already)?&first=";
		final URI uri = new URI("ssh", "", "host", -1, "/path", queryStr, null);
		assertEquals(queryStr, uri.getQuery());
		assertEquals("first=first%20value&second=(already)?&first=", uri.getRawQuery());
		final Map<String, ImmutableList<String>> query = Utils.getQuery(uri);
		assertEquals(ImmutableSet.of("first", "second"), query.keySet());
		assertEquals(ImmutableList.of("first value", ""), query.get("first"));
		assertEquals(ImmutableList.of("(already)?"), query.get("second"));
	}

	@Test
	void testCopyResursivelyAuthorized() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path source = jimFs.getPath("/source/");
			Files.createDirectory(source);
			final Path target = jimFs.getPath("/target/");
			Files.createDirectory(target);
			Files.writeString(source.resolve("file.txt"), "Hello.");
			Utils.copyRecursively(source, target);
			assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
		}
	}

	@Test
	void testCopyResursivelyNotReadableFile() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path source = Path.of("/home/guest/");
			final Path target = jimFs.getPath("/target/");
			Files.createDirectory(target);
			assertThrows(IOException.class, () -> Utils.copyRecursively(source, target));
		}
	}

	@Test
	void testCopyResursivelyReadableFile() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path source = Path.of("src/main/resources/");
			final Path target = jimFs.getPath("/target/");
			Files.createDirectory(target);
			Utils.copyRecursively(source, target);
			assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
		}
	}
}
