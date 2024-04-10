package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.factory.GitCloner;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubToken;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.github.services.GitHubFetcherQL;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitFileSystemWithHistoryFetcherFromMap implements GitFileSystemWithHistoryFetcher {
  @SuppressWarnings("unused")
  private static final Logger LOGGER =
      LoggerFactory.getLogger(GitFileSystemWithHistoryFetcherFromMap.class);

  public static GitFileSystemWithHistoryFetcher
      fromMap(Map<GitHubUsername, RepositoryCoordinates> map, boolean useCommitDates) {
    return new GitFileSystemWithHistoryFetcherFromMap(map, useCommitDates);
  }

  private final ImmutableMap<GitHubUsername, RepositoryCoordinates> map;
  private Repository lastRepository;
  private GitFileSystem lastGitFs;
  private GitHistorySimple lastHistory;
  private final boolean useCommitDates;
  private final GitCloner cloner;
  private final GitHubFetcherQL fetcherQl;

  public GitFileSystemWithHistoryFetcherFromMap(Map<GitHubUsername, RepositoryCoordinates> map, boolean useCommitDates) {
    this.map = ImmutableMap.copyOf(map);
    this.useCommitDates = useCommitDates;
    lastGitFs = null;
    lastRepository = null;
    lastHistory = null;
    cloner = GitCloner.create();
    fetcherQl = GitHubFetcherQL.using(GitHubToken.getRealInstance());
  }

  @Override
  public ImmutableSet<GitHubUsername> getAuthors() {
    return map.keySet();
  }

  @Override
  public GitHistorySimple goToFs(GitHubUsername username) throws IOException {
    final Optional<RuntimeException> exc = closePrevious();
    exc.ifPresent(e -> {
      throw e;
    });

    final RepositoryCoordinates coordinates = map.get(username);

    final Path dir = Utils.getTempDirectory().resolve(coordinates.getRepositoryName());

    lastRepository = cloner.download(coordinates.asGitUri(), dir);

    lastGitFs = GitFileSystemProvider.instance().newFileSystemFromRepository(lastRepository);

    final GitHubHistory gitHubHistory = fetcherQl.getReversedGitHubHistory(coordinates);
    if (useCommitDates) {
      lastHistory = GitHistorySimple.usingCommitterDates(lastGitFs);
      LOGGER.info("Last history: {}.", lastHistory);
    } else {
      lastHistory = GitHistorySimple.create(lastGitFs, gitHubHistory.getConsistentPushDates());
    }

    return lastHistory;
  }

  @Override
  public void close() throws IOException {
    Optional<RuntimeException> firstCloseExc = closePrevious();
    try {
      fetcherQl.close();
    } catch (RuntimeException e) {
      firstCloseExc = Optional.of(firstCloseExc.orElse(e));
    }

    firstCloseExc.ifPresent(e -> {
      throw e;
    });
  }

  private Optional<RuntimeException> closePrevious() throws IOException {
    Optional<RuntimeException> firstCloseExc = Optional.empty();
    if (lastGitFs != null) {
      try {
        lastGitFs.close();
      } catch (RuntimeException e) {
        firstCloseExc = Optional.of(firstCloseExc.orElse(e));
      }
    }
    if (lastRepository != null) {
      try {
        lastRepository.close();
      } catch (RuntimeException e) {
        firstCloseExc = Optional.of(firstCloseExc.orElse(e));
      }
    }
    return firstCloseExc;
  }
}
