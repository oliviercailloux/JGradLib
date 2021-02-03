package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;

public class RepositoryFetcher {
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

	public RepositoryFetcher setRepositoriesFilter(Predicate<RepositoryCoordinatesWithPrefix> accepted) {
		repositoriesFilter = accepted;
		return this;
	}

	public ImmutableSet<RepositoryCoordinatesWithPrefix> fetch() {
		final ImmutableSet<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", prefix).stream()
					.filter(repositoriesFilter).collect(ImmutableSet.toImmutableSet());
		}
		return repositories;
	}

}