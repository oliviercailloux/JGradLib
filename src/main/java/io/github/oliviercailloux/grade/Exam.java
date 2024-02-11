package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import jakarta.json.bind.annotation.JsonbPropertyOrder;

/**
 * TODO a student may have an overriding mark, example, student was absent but submitted anyway, we
 * want to give 0 but still give the information about the counterfactual grade.
 *
 * So in fact any tree may be added an overriding mark, and this still conforms to the same
 * aggregation procedure, it seems? (In the sense of being the same exam.)
 */
@JsonbPropertyOrder({"aggregator", "grades"})
public record Exam (GradeAggregator aggregator, ImmutableMap<GitHubUsername, MarksTree> grades) {
  public Exam(GradeAggregator aggregator, ImmutableMap<GitHubUsername, MarksTree> grades) {
    this.aggregator = aggregator;
    this.grades = grades;
    /* Just to check that it doesn’t throw. */
    getUsernames().stream().forEach(this::getGrade);
  }

  public ImmutableSet<GitHubUsername> getUsernames() {
    return grades.keySet();
  }

  public Grade getGrade(GitHubUsername username) {
    return Grade.given(aggregator, grades.get(username));
  }
}
