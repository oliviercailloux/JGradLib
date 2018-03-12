package io.github.oliviercailloux.git_hub;

import static java.util.Objects.requireNonNull;

public class IssueCoordinates {
	public static IssueCoordinates from(String owner, String repo, int issue) {
		return new IssueCoordinates(owner, repo, issue);
	}

	private final int issue;

	private final String owner;

	private final String repo;

	private IssueCoordinates(String owner, String repo, int issue) {
		this.owner = requireNonNull(owner);
		this.repo = requireNonNull(repo);
		this.issue = issue;
	}

	public int getIssueNumber() {
		return issue;
	}

	public String getOwner() {
		return owner;
	}

	public RepositoryCoordinates getRepositoryCoordinates() {
		return RepositoryCoordinates.from(owner, repo);
	}

	public String getRepositoryName() {
		return repo;
	}
}
