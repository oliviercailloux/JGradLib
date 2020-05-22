package io.github.oliviercailloux.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FilePermission;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Policy;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.utils.Utils;

public class SecurityTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityTests.class);

	@Test
	void testSetPolicy() throws Exception {
		final String file = "/home/olivier/Local/article.tex";
		assertNull(System.getSecurityManager());
		assertThrows(AccessControlException.class,
				() -> AccessController.checkPermission(new FilePermission(file, "read")));

		assertNotNull(Policy.getPolicy());
		final SandboxSecurityPolicy myPolicy = new SandboxSecurityPolicy();
		Policy.setPolicy(myPolicy);
		System.setSecurityManager(new SecurityManager());
		assertNotNull(System.getSecurityManager());
		assertDoesNotThrow(() -> AccessController.checkPermission(new FilePermission("/-", "read")));
		assertNotNull(Policy.getPolicy());

		Files.readString(Path.of(file));

		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path source = Path.of("src/main/resources/");
			final Path target = jimFs.getPath("/target/");
			Files.createDirectory(target);
			Utils.copyRecursively(source, target);
			assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
		}

	}
}
