package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import jakarta.json.bind.annotation.JsonbPropertyOrder;

@JsonbPropertyOrder({ "structure", "grades" })
public record Exam(GradeStructure structure, ImmutableMap<GitHubUsername, Grade> grades) {
	public StructuredGrade getStructuredGrade(GitHubUsername username) {
		return StructuredGrade.given(grades.get(username), structure);
	}

	public ImmutableSet<GitHubUsername> getUsernames() {
		return grades.keySet();
	}
}
