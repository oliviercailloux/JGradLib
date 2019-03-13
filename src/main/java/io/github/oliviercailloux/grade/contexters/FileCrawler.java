package io.github.oliviercailloux.grade.contexters;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * TODO (first priority!) implement a visit using git structure only, not disk.
	 * Define queue containing just start path. While queue not empty: pop from
	 * queue, list folders and add to queue, visit folder (remember those that match
	 * the predicate). Then (second priority) set current commit in this class and
	 * use it with client.
	 *
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
				LOGGER.debug("No directory " + relativeStart + ".");
				all = ImmutableSet.of();
			} else {
				all = Files.walk(start).filter((p) -> Files.isRegularFile(p)).map((p) -> projectDirectory.relativize(p))
						.collect(ImmutableSet.toImmutableSet());
				LOGGER.debug("Sources: {}.", all);
			}
		}
		return all;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(FileCrawler.class);
}