package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinatesWithPrefix;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitFileSystemWithHistoryFetcherByPrefix {
  @SuppressWarnings("unused")
  private static final Logger LOGGER =
      LoggerFactory.getLogger(GitFileSystemWithHistoryFetcherByPrefix.class);

  public static GitFileSystemWithHistoryFetcher getRetrievingByPrefix(String prefix) {
    return get(prefix, Integer.MAX_VALUE,
        Predicates.alwaysTrue(), false);
  }

  public static GitFileSystemWithHistoryFetcher
      getRetrievingByPrefixAndUsingCommitDates(String prefix) {
    return get(prefix, Integer.MAX_VALUE,
        Predicates.alwaysTrue(), true);
  }

  public static GitFileSystemWithHistoryFetcher
      getRetrievingByPrefixAndFilteringAndUsingCommitDates(String prefix, String accepted) {
    return get(prefix, Integer.MAX_VALUE,
        Predicate.isEqual(GitHubUsername.given(accepted)), true);
  }

  public static GitFileSystemWithHistoryFetcher getRetrievingByPrefixAndFiltering(String prefix,
      String accepted) {
    return get(prefix, Integer.MAX_VALUE,
        Predicate.isEqual(GitHubUsername.given(accepted)), false);
  }

  public static GitFileSystemWithHistoryFetcher getFirstRetrievingByPrefix(String prefix) {
    return get(prefix, 1, Predicates.alwaysTrue(), false);
  }

  private static GitFileSystemWithHistoryFetcher get(String prefix, int count,
      Predicate<GitHubUsername> accepted, boolean useCommitDates) {
    checkArgument(count >= 0);
    ImmutableSet<GitHubUsername> authors = GitFileSystemWithHistoryFetcherByPrefix.getAuthors(prefix, count, accepted);
    ImmutableMap<GitHubUsername, RepositoryCoordinates> map = authors.stream()
        .collect(ImmutableMap.toImmutableMap(author -> author, a -> GitFileSystemWithHistoryFetcherByPrefix.coordinates(a, prefix)));
    return GitFileSystemWithHistoryFetcherFromMap.fromMap(map, useCommitDates);
  }
  

  private static ImmutableSet<GitHubUsername> getAuthors(String prefix, int count,
  Predicate<GitHubUsername> accepted) {
    final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(prefix);
    LOGGER.debug("Getting authors using {}, count {}.", prefix, count);
    final ImmutableSet<RepositoryCoordinatesWithPrefix> coordinatess = fetcher.fetch();
    final ImmutableSet<GitHubUsername> unfiltered =
        coordinatess.stream().limit(count).map(RepositoryCoordinatesWithPrefix::getUsername)
            .map(GitHubUsername::given).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<GitHubUsername> filtered =
        unfiltered.stream().filter(accepted).collect(ImmutableSet.toImmutableSet());
    if (filtered.isEmpty()) {
      LOGGER.warn("Filtered to nothing, from {} then {}.", coordinatess, unfiltered);
    }
    return filtered;
  }

  private static RepositoryCoordinates coordinates(GitHubUsername username, String prefix) {
    final RepositoryCoordinatesWithPrefix coordinates = RepositoryCoordinatesWithPrefix
        .from(RepositoryFetcher.DEFAULT_ORG, prefix, username.getUsername());
    return coordinates;
  }
}
