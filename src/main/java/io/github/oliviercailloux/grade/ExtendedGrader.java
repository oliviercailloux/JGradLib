package io.github.oliviercailloux.grade;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;

public interface ExtendedGrader<X extends Exception> {
	public MarksTree grade(GitHubUsername author, GitFileSystemHistory data) throws X;

	public GradeAggregator getAggregator();
}
