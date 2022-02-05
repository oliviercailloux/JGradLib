package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;

public record Exam(GradeStructure structure, ImmutableMap<GitHubUsername, Grade> grades) {

}
