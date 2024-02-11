package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.filter.GitFilteringFs;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;
import java.time.Instant;
import org.eclipse.jgit.lib.ObjectId;

public class GitFileSystemWithHistoryFetcherFilterer implements GitFileSystemWithHistoryFetcher {
  public static GitFileSystemWithHistoryFetcher filterer(GitFileSystemWithHistoryFetcher delegate, Instant cap) {
    return new GitFileSystemWithHistoryFetcherFilterer(delegate, cap);
  }

  private final GitFileSystemWithHistoryFetcher delegate;
  private final Instant cap;

  private GitFileSystemWithHistoryFetcherFilterer(GitFileSystemWithHistoryFetcher delegate, Instant cap) {
    this.delegate = checkNotNull(delegate);
    this.cap = checkNotNull(cap);
  }

  @Override
  public ImmutableSet<GitHubUsername> getAuthors() {
    return delegate.getAuthors();
  }

  @Override
  public GitHistorySimple goToFs(GitHubUsername author) throws IOException {
    final GitHistorySimple hs = delegate.goToFs(author);
    final ImmutableMap<ObjectId, Instant> timestamps = hs.getTimestamps();
    final GitFilteringFs capped = GitFilteringFs.filter(hs.fs(), c -> !timestamps.get(c.id()).isAfter(cap));
    return GitHistorySimple.create(capped, timestamps);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

}
