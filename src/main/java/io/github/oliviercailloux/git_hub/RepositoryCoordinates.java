package io.github.oliviercailloux.git_hub;

import static java.util.Objects.requireNonNull;

import javax.json.JsonObject;

public class RepositoryCoordinates {
	public static RepositoryCoordinates from(String owner, String repo) {
		return new RepositoryCoordinates(owner, repo);
	}

	public static RepositoryCoordinates from(JsonObject json) {
		return new RepositoryCoordinates(json.getJsonObject("owner").getString("login"), json.getString("name"));
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

	public String getSshURLString() {
		return "git@github.com:" + getOwner() + "/" + getRepositoryName() + ".git";
	}

	@Override
	public String toString() {
		return owner + "/" + repo;
	}
}
