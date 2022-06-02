package io.github.oliviercailloux.grade;

public interface GitFsGrader<X extends Exception> {
	public MarksTree grade(GitFileSystemHistory data) throws X;

	public GradeAggregator getAggregator();
}
