package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;

public class ExamTestsHelper {
  public static Exam get3Plus2() {
    final Grade grade1 = GradeTestsHelper.get3Plus2();
    return new Exam(grade1.toAggregator(), ImmutableMap.of(GitHubUsername.given("u1"), grade1.toMarksTree(),
        GitHubUsername.given("u2"), MarksTreeTestsHelper.get3Plus2Variant()));
  }
}
