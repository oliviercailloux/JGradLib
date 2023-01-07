package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;
import java.util.Map;

public class StaticFetcherSimple implements GitFileSystemWithHistoryFetcher {
	public static GitFileSystemWithHistoryFetcher single(GitHubUsername username, GitHistorySimple gitFs) {
		return new StaticFetcherSimple(ImmutableMap.of(username, gitFs));
	}

	public static GitFileSystemWithHistoryFetcher multiple(Map<GitHubUsername, GitHistorySimple> gitFsesByauthor) {
		return new StaticFetcherSimple(gitFsesByauthor);
	}

	private final ImmutableMap<GitHubUsername, GitHistorySimple> gitFsesByauthor;

	private StaticFetcherSimple(Map<GitHubUsername, GitHistorySimple> gitFsesByauthor) {
		this.gitFsesByauthor = ImmutableMap.copyOf(gitFsesByauthor);
	}

	@Override
	public ImmutableSet<GitHubUsername> getAuthors() {
		return gitFsesByauthor.keySet();
	}

	@SuppressWarnings("deprecation")
	@Override
	public GitFileSystemHistory goTo(GitHubUsername author) throws IOException {
		throw new UnsupportedOperationException();
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