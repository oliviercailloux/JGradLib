package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.utils.Utils;

class GitFileSystemProviderTests {
	@Test
	void testNoSystemThere() {
		final Path emptyDir = Utils.getTempDirectory().resolve("test-" + Instant.now().toString());
		assertFalse(Files.exists(emptyDir));
		assertThrows(IOException.class, () -> new GitFileSystemProvider().newFileSystemFromGitDir(emptyDir));
	}

}
