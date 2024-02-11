package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.github.model.GitHubToken;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.github.services.GitHubFetcherV3;
import java.util.function.Predicate;

public class RepositoryFetcher {
  public static final String DEFAULT_ORG = "oliviercailloux-org";

  public static RepositoryFetcher withPrefix(String prefix) {
    return new RepositoryFetcher(prefix);
  }

  private final String prefix;
  private Predicate<RepositoryCoordinatesWithPrefix> repositoriesFilter;

  private RepositoryFetcher(String prefix) {
    this.prefix = checkNotNull(prefix);
    this.repositoriesFilter = r -> true;
  }

  public String getPrefix() {
    return prefix;
  }

  public RepositoryFetcher
      setRepositoriesFilter(Predicate<RepositoryCoordinatesWithPrefix> accepted) {
    repositoriesFilter = accepted;
    return this;
  }

  public ImmutableSet<RepositoryCoordinatesWithPrefix> fetch() {
    final ImmutableSet<RepositoryCoordinatesWithPrefix> repositories;
    try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      repositories = fetcher.getRepositoriesWithPrefix(DEFAULT_ORG, prefix).stream()
          .filter(repositoriesFilter).collect(ImmutableSet.toImmutableSet());
    }
    return repositories;
  }
}
