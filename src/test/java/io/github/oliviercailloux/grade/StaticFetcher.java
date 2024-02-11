package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import java.io.IOException;
import java.util.Map;

public class StaticFetcher implements GitFileSystemWithHistoryFetcher {
  public static GitFileSystemWithHistoryFetcher single(GitHubUsername username,
      GitHistorySimple gitFs) {
    return new StaticFetcher(ImmutableMap.of(username, gitFs));
  }

  public static GitFileSystemWithHistoryFetcher
      multiple(Map<GitHubUsername, GitHistorySimple> gitFsesByauthor) {
    return new StaticFetcher(gitFsesByauthor);
  }

  private final ImmutableMap<GitHubUsername, GitHistorySimple> gitFsesByauthor;

  private StaticFetcher(Map<GitHubUsername, GitHistorySimple> gitFsesByauthor) {
    this.gitFsesByauthor = ImmutableMap.copyOf(gitFsesByauthor);
  }

  @Override
  public ImmutableSet<GitHubUsername> getAuthors() {
    return gitFsesByauthor.keySet();
  }

  @Override
  public GitHistorySimple goToFs(GitHubUsername author) throws IOException {
    checkArgument(gitFsesByauthor.containsKey(author));
    return gitFsesByauthor.get(author);
  }

  @Override
  public void close() {
    /* Nothing to close. */
  }
}
