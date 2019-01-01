package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.Client;

public class FileCrawler {
	private final Client client;

	public FileCrawler(Client client) {
		this.client = requireNonNull(client);
	}

	public String getFileContent(Path path) throws IOException {
		checkArgument(!path.toString().equals(""));
		return client.fetchBlobOrEmpty(path);
	}

	/**
	 * @return paths relative to project directory
	 */
	public ImmutableSet<Path> getRecursively(Path relativeStart) throws IOException {
		final ImmutableSet<Path> all;
		if (!client.hasContentCached()) {
			all = ImmutableSet.of();
		} else {
			final Path projectDirectory = client.getProjectDirectory();
			final Path start = projectDirectory.resolve(relativeStart);
			if (!Files.isDirectory(start)) {
				GitToMultipleSourcer.LOGGER.debug("No directory " + relativeStart + ".");
				all = ImmutableSet.of();
			} else {
				all = Files.walk(start).filter((p) -> Files.isRegularFile(p)).map((p) -> projectDirectory.relativize(p))
						.collect(ImmutableSet.toImmutableSet());
				GitToMultipleSourcer.LOGGER.debug("Sources: {}.", all);
			}
		}
		return all;
	}
}