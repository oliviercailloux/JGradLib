package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.Mark;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JsonGradeTests {
	@Test
	void testWriteStructure() throws Exception {
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final Criterion c3 = Criterion.given("c3");
		final GradeStructure emptyAbs = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final GradeStructure emptyMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(), ImmutableMap.of());
		final GradeStructure oneMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(c1), ImmutableMap.of());
		final GradeStructure toWrite = GradeStructure.givenWeights(ImmutableMap.of(c1, 1d),
				ImmutableMap.of(c1, emptyAbs, c2, emptyMax, c3, oneMax));

		assertEquals(Resources.toString(this.getClass().getResource("GradeStructure.json"), StandardCharsets.UTF_8),
				JsonSimpleGrade.toJson(toWrite));
	}

	@Test
	void testReadStructure() throws Exception {
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final Criterion c3 = Criterion.given("c3");
		final GradeStructure emptyAbs = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final GradeStructure emptyMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(), ImmutableMap.of());
		final GradeStructure oneMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(c1), ImmutableMap.of());
		final GradeStructure expected = GradeStructure.givenWeights(ImmutableMap.of(c1, 1d),
				ImmutableMap.of(c1, emptyAbs, c2, emptyMax, c3, oneMax));

		assertEquals(expected, JsonSimpleGrade.asStructure(
				Resources.toString(this.getClass().getResource("GradeStructure.json"), StandardCharsets.UTF_8)));
	}

	@Test
	void testWriteGrade() throws Exception {
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final Criterion c3 = Criterion.given("c3");
		final Mark gradeC1 = Mark.one();
		final Mark gradeC2C1 = Mark.zero("Zero!");
		final Mark gradeC2C3 = Mark.zero();
		final Grade gradeC2 = Grade.composite(ImmutableMap.of(c1, gradeC2C1, c3, gradeC2C3));
		final Grade grade = Grade.composite(ImmutableMap.of(c1, gradeC1, c2, gradeC2));

//		assertEquals("", JsonSimpleGrade.toJson(gradeC1));
		assertEquals(Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8),
				JsonSimpleGrade.toJson(grade));
	}

	@Test
	void testReadGrade() throws Exception {
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final Criterion c3 = Criterion.given("c3");
		final Mark gradeC1 = Mark.one();
		final Mark gradeC2C1 = Mark.zero("Zero!");
		final Mark gradeC2C3 = Mark.zero();
		final Grade gradeC2 = Grade.composite(ImmutableMap.of(c1, gradeC2C1, c3, gradeC2C3));
		final Grade expected = Grade.composite(ImmutableMap.of(c1, gradeC1, c2, gradeC2));

		assertTrue(gradeC1.equals(Mark.one()));
		assertTrue(gradeC2C1.equals(Mark.zero("Zero!")));
		assertTrue(gradeC2C3.equals(Mark.zero()));
		assertTrue(gradeC2.equals(Grade.composite(ImmutableMap.of(c1, gradeC2C1, c3, gradeC2C3))));
		assertEquals("Mark{points=1.0, comment=}", gradeC1.toString());
		assertEquals(
				"CompositeGrade{subGrades={Criterion{c1}=SubGrade{criterion=Criterion{c1}, grade=Mark{points=0.0, comment=Zero!}}, Criterion{c3}=SubGrade{criterion=Criterion{c3}, grade=Mark{points=0.0, comment=}}}}",
				gradeC2.toString());
		assertTrue(expected.toString().startsWith(
				"CompositeGrade{subGrades={Criterion{c1}=SubGrade{criterion=Criterion{c1}, grade=Mark{points=1.0, comment=}}"),
				expected.toString());
		final Grade readAsGrade = JsonSimpleGrade
				.asGrade(Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8));
		assertEquals(expected.toString(), readAsGrade.toString());
		assertEquals(expected, Grade.composite(ImmutableMap.of(c1, gradeC1, c2, gradeC2)));
		assertEquals(expected.getGrade(c1), readAsGrade.getGrade(c1));
		assertEquals(expected, readAsGrade);
	}
}
