package io.github.oliviercailloux.git.github.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.net.URI;
import java.util.Objects;

public class GitHubUsername {
  public static GitHubUsername given(String gitHubUsername) {
    return new GitHubUsername(gitHubUsername);
  }

  private final String username;

  private GitHubUsername(String username) {
    this.username = checkNotNull(username);
    checkArgument(!username.isEmpty());
  }

  /**
   * @return not empty
   */
  public String getUsername() {
    return username;
  }

  public URI getUrl() {
    return URI.create("https://github.com/" + username);
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof GitHubUsername)) {
      return false;
    }
    final GitHubUsername t2 = (GitHubUsername) o2;
    return username.equals(t2.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Username", username).toString();
  }
}
