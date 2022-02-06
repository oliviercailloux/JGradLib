package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import jakarta.json.bind.annotation.JsonbPropertyOrder;

@JsonbPropertyOrder({ "aggregator", "grades" })
public record Exam(GradeAggregator aggregator, ImmutableMap<GitHubUsername, MarksTree> grades) {
	public Grade getGrade(GitHubUsername username) {
		return Grade.given(aggregator, grades.get(username));
	}

	public ImmutableSet<GitHubUsername> getUsernames() {
		return grades.keySet();
	}
}
