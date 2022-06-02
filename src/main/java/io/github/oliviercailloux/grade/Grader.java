package io.github.oliviercailloux.grade;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;

public interface Grader<X extends Exception> extends ExtendedGrader<X> {
	@Override
	public default MarksTree grade(GitHubUsername author, GitFileSystemHistory data) throws X {
		return grade(data);
	}

	public MarksTree grade(GitFileSystemHistory data) throws X;
}
