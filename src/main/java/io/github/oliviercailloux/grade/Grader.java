package io.github.oliviercailloux.grade;

import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;

public interface Grader<X extends Exception> {
  public MarksTree grade(GitHubUsername author, GitHistorySimple data) throws X;

  public GradeAggregator getAggregator();
}
