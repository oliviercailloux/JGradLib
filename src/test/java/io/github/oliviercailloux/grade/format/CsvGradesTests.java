package io.github.oliviercailloux.grade.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.format.json.JsonGradeTest;
import io.github.oliviercailloux.java_grade.utils.Summarize;

class CsvGradesTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGradesTests.class);

	@Test
	void writeTest() throws Exception {
		final StudentOnGitHub s1 = StudentOnGitHub.with("u1");
		final StudentOnGitHub s2 = StudentOnGitHub.with(GitHubUsername.given("u2"),
				InstitutionalStudent.withU(1, "user", "first", "last", EmailAddress.given("e@m.com")));
		final WeightingGrade grade1 = GradeTestsHelper.getGrade3Plus2();
		final WeightingGrade grade2 = GradeTestsHelper.getGrade3Plus2Alt();
		final ImmutableMap<StudentOnGitHub, WeightingGrade> grades = ImmutableMap.of(s1, grade1, s2, grade2);
		final String written = CsvGrades.asCsv(grades);
		final String expected = Resources.toString(getClass().getResource("TwoStudentsGrades.csv"),
				StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

	@Test
	void writeVeryComplexGradeTest() throws Exception {
		final String jsonGrade = Resources.toString(JsonGradeTest.class.getResource("VeryComplexGrade.json"),
				StandardCharsets.UTF_8);
		final IGrade grade = JsonGrade.asGrade(jsonGrade);
		final String written = CsvGrades.asCsv(ImmutableMap.of(StudentOnGitHub.with("u"), grade));

		final String expected = Resources.toString(getClass().getResource("VeryComplexGrade.csv"),
				StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

}
