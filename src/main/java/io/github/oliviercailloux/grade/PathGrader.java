package io.github.oliviercailloux.grade;

import java.nio.file.Path;

public interface PathGrader<X extends Exception> {
	MarksTree grade(Path workDir) throws X;

	public GradeAggregator getAggregator();

}
