package io.github.oliviercailloux.st_projects.services.json;

import static io.github.oliviercailloux.st_projects.ex2.Ex2Criterion.ANNOT;
import static io.github.oliviercailloux.st_projects.ex2.Ex2Criterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import io.github.oliviercailloux.git.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.model.Mark;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;

public class JsonGradeTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonGradeTest.class);

	@Test
	public void gradesWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grades.json"), StandardCharsets.UTF_8);

		final Mark mark1 = Mark.max(ENC);
		final StudentGrade grade1 = StudentGrade.of(getStudentOnGitHubKnown("g1", 1).asStudentOnGitHub(),
				ImmutableSet.of(mark1));
		final Mark mark2 = Mark.min(ANNOT);
		final StudentGrade grade2 = StudentGrade.of(getStudentOnGitHubKnown("g2", 2).asStudentOnGitHub(),
				ImmutableSet.of(mark2));
		final ImmutableSet<StudentGrade> grades = ImmutableSet.of(grade1, grade2);
		final String written = JsonUtils.serializeWithJsonB(grades, JsonGrade.asAdapter()).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void gradesReadJson() throws Exception {
		final Mark mark1 = Mark.max(ENC);
		final StudentGrade grade1 = StudentGrade.of(getStudentOnGitHubKnown("g1", 1).asStudentOnGitHub(),
				ImmutableSet.of(mark1));
		final Mark mark2 = Mark.min(ANNOT);
		final StudentGrade grade2 = StudentGrade.of(getStudentOnGitHubKnown("g2", 2).asStudentOnGitHub(),
				ImmutableSet.of(mark2));
		final ImmutableSet<StudentGrade> expected = ImmutableSet.of(grade1, grade2);

		final String json = Resources.toString(this.getClass().getResource("Grades.json"), StandardCharsets.UTF_8);
		final ImmutableSet<StudentGrade> read = JsonGrade.asGrades(json);
		LOGGER.debug("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	private StudentOnGitHubKnown getStudentOnGitHubKnown(String gitHubUsername, int id) {
		final StudentOnMyCourse studentMC = getStudentOnMyCourse(id);
		final StudentOnGitHubKnown studentGH = StudentOnGitHubKnown.with(studentMC, gitHubUsername);
		return studentGH;
	}

	private StudentOnMyCourse getStudentOnMyCourse(int id) {
		final StudentOnMyCourse studentMC = StudentOnMyCourse.with(id, "f", "l", "u");
		return studentMC;
	}

	@Test
	public void gradeWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);

		final Mark grade1 = Mark.max(ENC);
		final Mark grade2 = Mark.min(ANNOT);
		final StudentGrade grade = StudentGrade.of(getStudentOnGitHubKnown("g", 1).asStudentOnGitHub(),
				ImmutableSet.of(grade1, grade2));
		final String written = JsonGrade.asJson(grade).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void gradeReadJson() throws Exception {
		final Mark grade1 = Mark.max(ENC);
		final Mark grade2 = Mark.min(ANNOT);
		final StudentGrade expected = StudentGrade.of(getStudentOnGitHubKnown("g", 1).asStudentOnGitHub(),
				ImmutableSet.of(grade1, grade2));
		final String json = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);
		final StudentGrade read = JsonGrade.asGrade(json);
		LOGGER.debug("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

}
