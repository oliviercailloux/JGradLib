package io.github.oliviercailloux.grade;

import io.github.oliviercailloux.git.filter.GitHistorySimple;

public interface GitFsGrader<X extends Exception> {
	public MarksTree grade(GitHistorySimple data) throws X;

	public GradeAggregator getAggregator();
}
