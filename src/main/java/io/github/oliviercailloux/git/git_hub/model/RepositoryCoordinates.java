package io.github.oliviercailloux.git.git_hub.model;

import static java.util.Objects.requireNonNull;

import java.net.URI;

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
		/**
		 * This is what GitHub suggests to use. Note that (I think that) this is not a
		 * valid part of a URI, because the host contains : but no port number, and the
		 * path does not start with /
		 */
		return "git@github.com:" + getOwner() + "/" + getRepositoryName() + ".git";
	}

	public URI asURI() {
		return URI.create("ssh://git@github.com/" + getOwner() + "/" + getRepositoryName() + ".git");
	}

	@Override
	public String toString() {
		return owner + "/" + repo;
	}
}
