package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.time.Instant;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.utils.Utils;

class GitFileSystemProviderTests {
	@Test
	void testNoSystemThere() {
		final Path noDir = Utils.getTempDirectory().resolve("test-" + Instant.now().toString());
		assertFalse(Files.exists(noDir));
		assertThrows(FileSystemNotFoundException.class,
				() -> new GitFileSystemProvider().newFileSystemFromGitDir(noDir));
	}

	@Test
	void testNone() throws Exception {
		final Path emptyDir = Utils.getTempDirectory().resolve("test-" + Instant.now().toString());
		assertFalse(Files.exists(emptyDir));
		assertThrows(FileSystemNotFoundException.class,
				() -> FileSystems.newFileSystem(emptyDir, ClassLoader.getSystemClassLoader()));
	}

	@Test
	void testEmpty() throws Exception {
		final Path emptyDir = Utils.getTempUniqueDirectory("testEmpty");
		Files.createDirectories(emptyDir);
		assertTrue(Files.exists(emptyDir));
		assertThrows(ProviderNotFoundException.class,
				() -> FileSystems.newFileSystem(emptyDir, ClassLoader.getSystemClassLoader()));
	}

	@Test
	void testRepo() throws Exception {
		final Path dir = Utils.getTempUniqueDirectory("testRepo");
		try (Repository repo = new FileRepository(dir.toFile())) {
			JGit.createBasicRepo(repo);
		}
		try (FileSystem gitFs = FileSystems.newFileSystem(dir, ClassLoader.getSystemClassLoader())) {
			assertEquals(2, ImmutableList.copyOf(gitFs.getRootDirectories()).size());
		}
	}
}
