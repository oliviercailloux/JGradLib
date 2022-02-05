package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.Mark;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JsonGradeTests {
	private static final Criterion C1 = Criterion.given("c1");
	private static final Criterion C2 = Criterion.given("c2");
	private static final Criterion C3 = Criterion.given("c3");

	private GradeStructure getStructure() {
		final GradeStructure emptyAbs = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final GradeStructure emptyMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(), ImmutableMap.of());
		final GradeStructure oneMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(C1), ImmutableMap.of());
		final GradeStructure toWrite = GradeStructure.givenWeights(ImmutableMap.of(C1, 1d),
				ImmutableMap.of(C1, emptyAbs, C2, emptyMax, C3, oneMax));
		return toWrite;
	}

	private Grade getGrade() {
		final Mark gradeC1 = Mark.one();
		final Mark gradeC2C1 = Mark.zero("Zero!");
		final Mark gradeC2C3 = Mark.zero();
		final Grade gradeC2 = Grade.composite(ImmutableMap.of(C1, gradeC2C1, C3, gradeC2C3));
		final Grade grade = Grade.composite(ImmutableMap.of(C1, gradeC1, C2, gradeC2));
		return grade;
	}

	private Grade getGrade2() {
		final Mark gradeC1 = Mark.zero();
		final Mark gradeC2C1 = Mark.one("Not zero!");
		final Mark gradeC2C3 = Mark.zero();
		final Grade gradeC2 = Grade.composite(ImmutableMap.of(C1, gradeC2C1, C3, gradeC2C3));
		final Grade grade = Grade.composite(ImmutableMap.of(C1, gradeC1, C2, gradeC2));
		return grade;
	}

	private Exam getExam() {
		return new Exam(getStructure(),
				ImmutableMap.of(GitHubUsername.given("g1"), getGrade(), GitHubUsername.given("g2"), getGrade2()));
	}

	@Test
	void testWriteStructure() throws Exception {
		final GradeStructure toWrite = getStructure();

		assertEquals(Resources.toString(this.getClass().getResource("GradeStructure.json"), StandardCharsets.UTF_8),
				JsonSimpleGrade.toJson(toWrite));
	}

	@Test
	void testReadStructure() throws Exception {
		final GradeStructure expected = getStructure();

		assertEquals(expected, JsonSimpleGrade.asStructure(
				Resources.toString(this.getClass().getResource("GradeStructure.json"), StandardCharsets.UTF_8)));
	}

	@Test
	void testWriteGrade() throws Exception {
		final Grade grade = getGrade();

//		assertEquals("", JsonSimpleGrade.toJson(gradeC1));
		assertEquals(Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8),
				JsonSimpleGrade.toJson(grade));
	}

	@Test
	void testReadGrade() throws Exception {
		assertEquals(getGrade(), JsonSimpleGrade
				.asGrade(Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8)));
	}

	@Test
	void testWriteExam() throws Exception {
		assertEquals(Resources.toString(this.getClass().getResource("Exam.json"), StandardCharsets.UTF_8),
				JsonSimpleGrade.toJson(getExam()));
	}

	@Test
	void testReadExam() throws Exception {
		assertEquals(getExam(), JsonSimpleGrade
				.asExam(Resources.toString(this.getClass().getResource("Exam.json"), StandardCharsets.UTF_8)));
	}
}
