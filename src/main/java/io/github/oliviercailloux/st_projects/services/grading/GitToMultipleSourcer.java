package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;

public class GitToMultipleSourcer implements GradingContexter, ContentSupplier {

	private final GitContext context;
	private String content;
	private final Path relativeStart;
	private String comment;
	private Predicate<Path> predicate;

	public GitToMultipleSourcer(GitContext context, Path start, Predicate<Path> predicate) {
		this.context = requireNonNull(context);
		this.relativeStart = requireNonNull(start);
		this.predicate = requireNonNull(predicate);
		clear();
	}

	/**
	 * @return an empty string if sources.size() != 1.
	 */
	@Override
	public String getContent() {
		assert content != null;
		return content;
	}

	@Override
	public void clear() {
		content = null;
		comment = null;
		sources = null;
	}

	@Override
	public void init() throws GradingException {
		final Client client = context.getClient();
		final Set<Path> sourcesFetched;
		if (!client.hasContentCached()) {
			sourcesFetched = ImmutableSet.of();
		} else {
			final Path start = client.getProjectDirectory().resolve(relativeStart);
			if (!Files.isDirectory(start)) {
				LOGGER.debug("No directory " + relativeStart + ".");
				sourcesFetched = ImmutableSet.of();
			} else {
				try {
					sourcesFetched = Files.walk(start).filter(predicate).collect(Collectors.toSet());
				} catch (IOException e) {
					throw new GradingException(e);
				}
				LOGGER.debug("Sources: {}.", sourcesFetched);
			}
		}

		sources = sourcesFetched;
		if (sources.size() == 1) {
			final Path servlet = sources.iterator().next();
			try {
				content = client.fetchBlobOrEmpty(servlet);
//				LOGGER.debug("Fetched from {}: {}.", servlet, content);
			} catch (IOException e) {
				throw new GradingException(e);
			}
		} else {
			content = "";
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitToMultipleSourcer.class);
	private Set<Path> sources;

	public String getComment() {
		return comment;
	}

	public Set<Path> getSources() {
		return sources;
	}
}
