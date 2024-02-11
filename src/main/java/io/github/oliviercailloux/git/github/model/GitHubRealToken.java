package io.github.oliviercailloux.git.github.model;

import static com.google.common.base.Preconditions.checkState;

public class GitHubRealToken extends GitHubToken {

  public GitHubRealToken(String token) {
    super(token);
    checkState(token.length() >= 1);
  }
}
