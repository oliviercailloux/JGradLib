package io.github.oliviercailloux.java_grade.utils;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;

public class ShowEvents {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ShowEvents.class);

	public static void main(String[] args) throws IllegalStateException, IOException {
		final RepositoryCoordinates repo = RepositoryCoordinates.from("oliviercailloux-org", "extractor-aitalibraham");
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final ImmutableList<Event> events = fetcher.getEvents(repo);
			LOGGER.info("Events: {}.", events);
		}
	}
}
