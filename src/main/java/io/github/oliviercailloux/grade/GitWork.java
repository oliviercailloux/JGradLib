package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import java.util.Objects;

public class GitWork {
  public static GitWork given(GitHubUsername author, GitHistorySimple history) {
    return new GitWork(author, history);
  }

  private final GitHubUsername author;
  private final GitHistorySimple history;

  public GitWork(GitHubUsername author, GitHistorySimple history) {
    this.author = checkNotNull(author);
    this.history = checkNotNull(history);
  }

  public GitHubUsername getAuthor() {
    return author;
  }

  public GitHistorySimple getHistory() {
    return history;
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof GitWork)) {
      return false;
    }
    final GitWork t2 = (GitWork) o2;
    return author.equals(t2.author) && history.equals(t2.history);
  }

  @Override
  public int hashCode() {
    return Objects.hash(author, history);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Author", author).add("History", history)
        .toString();
  }
}
