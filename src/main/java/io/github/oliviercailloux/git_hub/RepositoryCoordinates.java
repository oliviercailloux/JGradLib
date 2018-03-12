package io.github.oliviercailloux.git_hub;

import static java.util.Objects.requireNonNull;

public class RepositoryCoordinates {
	public static RepositoryCoordinates from(String owner, String repo) {
		return new RepositoryCoordinates(owner, repo);
	}

	private final String owner;

	private final String repo;

	private RepositoryCoordinates(String owner, String repo) {
		this.owner = requireNonNull(owner);
		this.repo = requireNonNull(repo);
	}

	public String getOwner() {
		return owner;
	}

	public String getRepositoryName() {
		return repo;
	}

	@Override
	public String toString() {
		return owner + "/" + repo;
	}
}
