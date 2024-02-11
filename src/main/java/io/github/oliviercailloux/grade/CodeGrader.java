package io.github.oliviercailloux.grade;

import io.github.oliviercailloux.java_grade.bytecode.Instanciator;

public interface CodeGrader<X extends Exception> {

  MarksTree gradeCode(Instanciator project) throws X;

  GradeAggregator getCodeAggregator();
}
