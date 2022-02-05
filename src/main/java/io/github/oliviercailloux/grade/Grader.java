package io.github.oliviercailloux.grade;

public interface Grader<X extends Exception> {
	public Grade grade(GitFileSystemHistory data) throws X;

	public GradeStructure getStructure();
}
