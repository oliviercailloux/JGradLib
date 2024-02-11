package io.github.oliviercailloux.java_grade.utils;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShowEvents {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(ShowEvents.class);

  public static void main(String[] args) throws IllegalStateException {
    final RepositoryCoordinates repo =
        RepositoryCoordinates.from("oliviercailloux-org", "extractor-username");
    try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final ImmutableList<Event> events = fetcher.getEvents(repo);
      LOGGER.info("Events: {}.", events);
    }
  }
}
