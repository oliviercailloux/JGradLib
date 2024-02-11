package io.github.oliviercailloux.grade.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.ExamTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.format.json.JsonbGradeTests;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CsvGradesTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CsvGradesTests.class);

  @Test
  void writeTest() throws Exception {
    // final Grade grade = GradeTestsHelper.get3Plus2();
    // final CsvGrades<String> csvGrades = CsvGrades.newInstance(CsvGrades.STUDENT_NAME_FUNCTION,
    // 20d);
    // final ImmutableMap<String, MarksTree> grades = ImmutableMap.of("u1", grade.toMarksTree());

    final Exam exam = ExamTestsHelper.get3Plus2();
    final CsvGrades<GitHubUsername> csvGrades =
        CsvGrades.newInstance(CsvGrades.STUDENT_USERNAME_FUNCTION, 20d);
    final String written = csvGrades.gradesToCsv(exam.aggregator(), exam.grades());
    Files.writeString(Path.of("out.csv"), written);
    final String expected =
        Resources.toString(getClass().getResource("TwoStudentsGrades.csv"), StandardCharsets.UTF_8);
    assertEquals(expected, written);
  }

  @Test
  void writeVeryComplexGradeTest() throws Exception {
    final String jsonGrade = Resources.toString(
        JsonbGradeTests.class.getResource("VeryComplexGrade.json"), StandardCharsets.UTF_8);
    final IGrade grade = JsonGrade.asGrade(jsonGrade);
    final CsvGrades<StudentOnGitHub> csvGrades =
        CsvGrades.newInstance(CsvGrades.STUDENT_IDENTITY_FUNCTION, CsvGrades.DEFAULT_DENOMINATOR);
    final String written = csvGrades.toCsv(ImmutableMap.of(StudentOnGitHub.with("u"), grade));

    final String expected =
        Resources.toString(getClass().getResource("VeryComplexGrade.csv"), StandardCharsets.UTF_8);
    assertEquals(expected, written);
  }
}
