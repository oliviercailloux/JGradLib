package io.github.oliviercailloux.git.git_hub.model;

import static com.google.common.base.Preconditions.checkState;

public class GitHubRealToken extends GitHubToken {

	public GitHubRealToken(String token) {
		super(token);
		checkState(token.length() >= 1);
	}

}
