package io.github.oliviercailloux.grade;

public interface Grader<X extends Exception> {
	public MarksTree grade(GitFileSystemHistory data) throws X;

	public GradeStructure getStructure();
}
