package io.github.oliviercailloux.grade.json;

import static io.github.oliviercailloux.grade.json.TestCriterion.ANNOT;
import static io.github.oliviercailloux.grade.json.TestCriterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

public class JsonGradeTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonGradeTest.class);

	@Test
	public void anonymousGradesWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grades.json"), StandardCharsets.UTF_8);

		final CriterionAndMark mark1 = CriterionAndMark.max(ENC);
		final StudentOnGitHubKnown student1 = getStudentOnGitHubKnown("g1", 1);
		final AnonymousGrade grade1 = Grade.anonymous(ImmutableSet.of(mark1));
		final CriterionAndMark mark2 = CriterionAndMark.min(ANNOT);
		final StudentOnGitHubKnown student2 = getStudentOnGitHubKnown("g2", 2);
		final AnonymousGrade grade2 = Grade.anonymous(ImmutableSet.of(mark2));
		final ImmutableMap<StudentOnGitHubKnown, AnonymousGrade> grades = ImmutableMap.of(student1, grade1, student2,
				grade2);
		final String written = JsonGrade.asJsonArrayWithStudents(grades).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void gradesReadJson() throws Exception {
		final CriterionAndMark mark1 = CriterionAndMark.max(ENC);
		final Grade grade1 = Grade.of(getStudentOnGitHubKnown("g1", 1).asStudentOnGitHub(), ImmutableSet.of(mark1));
		final CriterionAndMark mark2 = CriterionAndMark.min(ANNOT);
		final Grade grade2 = Grade.of(getStudentOnGitHubKnown("g2", 2).asStudentOnGitHub(), ImmutableSet.of(mark2));
		final ImmutableSet<Grade> expected = ImmutableSet.of(grade1, grade2);

		{
			final String json = Resources.toString(this.getClass().getResource("Grades.json"), StandardCharsets.UTF_8);
			final ImmutableSet<Grade> read = JsonGrade.asGrades(json);
			LOGGER.debug("Deserialized: {}.", read);
			assertEquals(expected, read);
			assertEquals(expected.asList(), read.asList());
		}

		/** TODO is not always read in the right order! */
//		{
//			final String multJson = Files.readString(Paths.get("allgrades jpa.json"));
//			final ImmutableSet<Grade> read = JsonGrade.asGrades(multJson);
//			assertEquals("ArnCLAUDEL", read.iterator().next().getStudent().getGitHubUsername());
//		}
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
	public void anonymousGradeWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);

		final CriterionAndMark grade1 = CriterionAndMark.max(ENC);
		final CriterionAndMark grade2 = CriterionAndMark.min(ANNOT);
		final StudentOnGitHubKnown student = getStudentOnGitHubKnown("g", 1);
		final AnonymousGrade grade = Grade.anonymous(ImmutableSet.of(grade1, grade2));
		final String written = JsonGrade.asJson(student, grade).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void gradeReadJson() throws Exception {
		final CriterionAndMark grade1 = CriterionAndMark.max(ENC);
		final CriterionAndMark grade2 = CriterionAndMark.min(ANNOT);
		final Grade expected = Grade.of(getStudentOnGitHubKnown("g", 1).asStudentOnGitHub(),
				ImmutableSet.of(grade1, grade2));
		final String json = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);
		final Grade read = JsonGrade.asGrade(json);
		LOGGER.debug("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	@Test
	public void gradeWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);

		final CriterionAndMark grade1 = CriterionAndMark.max(ENC);
		final CriterionAndMark grade2 = CriterionAndMark.min(ANNOT);
		final Grade grade = Grade.of(getStudentOnGitHubKnown("g", 1).asStudentOnGitHub(),
				ImmutableSet.of(grade1, grade2));
		final String written = JsonGrade.asJson(grade).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void gradesWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Grades.json"), StandardCharsets.UTF_8);

		final CriterionAndMark mark1 = CriterionAndMark.max(ENC);
		final Grade grade1 = Grade.of(getStudentOnGitHubKnown("g1", 1).asStudentOnGitHub(), ImmutableSet.of(mark1));
		final CriterionAndMark mark2 = CriterionAndMark.min(ANNOT);
		final Grade grade2 = Grade.of(getStudentOnGitHubKnown("g2", 2).asStudentOnGitHub(), ImmutableSet.of(mark2));
		final ImmutableSet<Grade> grades = ImmutableSet.of(grade1, grade2);
		final String written = JsonGrade.asJsonArray(grades).toString();
		LOGGER.debug("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

}
