package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;
import java.time.Instant;

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
	public GitFileSystemHistory goTo(GitHubUsername author) throws IOException {
		final GitFileSystemHistory h = delegate.goTo(author);
		final GitFileSystemHistory filtered = h
				.filter(r -> !h.asGitHistory().getTimestamp(r.getStaticCommitId()).isAfter(cap), cap);
		verify(filtered.asGitHistory().getTimestamps().values().stream().allMatch(i -> !i.isAfter(cap)));
		verify(filtered.anyCommitMatches(r -> filtered.getCommitDate(r).isAfter(cap)).getPoints() == 0d);
		verify(filtered.getPushDates().values().stream().allMatch(i -> !i.isAfter(cap)));
		return filtered;
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}

}
