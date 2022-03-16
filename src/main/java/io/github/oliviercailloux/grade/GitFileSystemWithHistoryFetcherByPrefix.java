package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitFileSystemWithHistoryFetcherByPrefix implements GitFileSystemWithHistoryFetcher {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemWithHistoryFetcherByPrefix.class);

	public static GitFileSystemWithHistoryFetcher getRetrievingByPrefix(String prefix) {
		return new GitFileSystemWithHistoryFetcherByPrefix(prefix, Integer.MAX_VALUE, Predicates.alwaysTrue(), false);
	}

	public static GitFileSystemWithHistoryFetcher getRetrievingByPrefixAndUsingCommitDates(String prefix) {
		return new GitFileSystemWithHistoryFetcherByPrefix(prefix, Integer.MAX_VALUE, Predicates.alwaysTrue(), true);
	}

	public static GitFileSystemWithHistoryFetcher getRetrievingByPrefixAndFiltering(String prefix, String accepted) {
		return new GitFileSystemWithHistoryFetcherByPrefix(prefix, Integer.MAX_VALUE,
				Predicate.isEqual(GitHubUsername.given(accepted)), false);
	}

	public static GitFileSystemWithHistoryFetcher getFirstRetrievingByPrefix(String prefix) {
		return new GitFileSystemWithHistoryFetcherByPrefix(prefix, 1, Predicates.alwaysTrue(), false);
	}

	private final String prefix;
	private final GitCloner cloner;
	private final GitHubFetcherQL fetcherQl;
	private GitFileSystem lastGitFs;
	private Repository lastRepository;
	private GitFileSystemHistory lastHistory;
	private final int count;
	private final Predicate<GitHubUsername> accepted;
	private final boolean useCommitDates;

	GitFileSystemWithHistoryFetcherByPrefix(String prefix, int count, Predicate<GitHubUsername> accepted,
			boolean useCommitDates) {
		this.prefix = checkNotNull(prefix);
		this.count = count;
		this.accepted = checkNotNull(accepted);
		checkArgument(count >= 0);
		cloner = GitCloner.create().setCheckCommonRefsAgree(false);
		fetcherQl = GitHubFetcherQL.using(GitHubToken.getRealInstance());
		lastGitFs = null;
		lastRepository = null;
		lastHistory = null;
		this.useCommitDates = useCommitDates;
	}

	@Override
	public ImmutableSet<GitHubUsername> getAuthors() {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(prefix);
		final ImmutableSet<RepositoryCoordinatesWithPrefix> coordinatess = fetcher.fetch();
		return coordinatess.stream().limit(count).map(RepositoryCoordinatesWithPrefix::getUsername)
				.map(GitHubUsername::given).filter(accepted).collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public GitFileSystemHistory goTo(GitHubUsername username) throws IOException {
		checkArgument(accepted.test(username));

		final Optional<RuntimeException> exc = closePrevious();
		exc.ifPresent(e -> {
			throw e;
		});

		try {
			final RepositoryCoordinatesWithPrefix coordinates = RepositoryCoordinatesWithPrefix
					.from(RepositoryFetcher.DEFAULT_ORG, prefix, username.getUsername());

			final Path dir = Utils.getTempDirectory().resolve(coordinates.getRepositoryName());

			lastRepository = cloner.download(coordinates.asGitUri(), dir);

			lastGitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(lastRepository);

			final GitHubHistory gitHubHistory = fetcherQl.getReversedGitHubHistory(coordinates);
			final GitHistory history;
			if (useCommitDates) {
				history = GitUtils.getHistory(lastGitFs);
			} else {
				history = gitHubHistory.getConsistentPushHistory();
			}

			lastHistory = GitFileSystemHistory.create(lastGitFs, history, gitHubHistory.getPushDates());
			return lastHistory;
		} catch (RuntimeException | IOException e) {
			try {
				close();
			} catch (RuntimeException suppressed) {
				LOGGER.info("Suppressed {}.", suppressed);
			}
			throw e;
		}
	}

	@Override
	public void close() {
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

	private Optional<RuntimeException> closePrevious() {
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
