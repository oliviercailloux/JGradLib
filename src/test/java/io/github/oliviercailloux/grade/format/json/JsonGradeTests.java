package io.github.oliviercailloux.grade.format.json;

import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.ExamTestsHelper;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.MarkAggregator;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MarksTreeTestsHelper;
import io.github.oliviercailloux.grade.NormalizingStaticWeighter;
import io.github.oliviercailloux.grade.ParametricWeighter;
import io.github.oliviercailloux.grade.VoidAggregator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JsonGradeTests {
	@Test
	void testWriteParametricWeighter() throws Exception {
		final ParametricWeighter p = ParametricWeighter.given(c1, c2);
		final String json = JsonSimpleGrade.toJson(p);

		final String expected = """
				{
				    "type": "ParametricWeighter",
				    "multiplied": "C1",
				    "weighting": "C2"
				}""";
		assertEquals(expected, json);
	}

	@Test
	void testReadParametricWeighter() throws Exception {
		final String input = """
				{
				    "type": "ParametricWeighter",
				    "multiplied": "m",
				    "weighting": "w"
				}""";
		final MarkAggregator read = JsonSimpleGrade.asMarkAggregator(input);

		final ParametricWeighter expected = ParametricWeighter.given(Criterion.given("m"), Criterion.given("w"));
		assertEquals(expected, read);
	}

	@Test
	void testWriteStaticWeighter() throws Exception {
		final NormalizingStaticWeighter w = NormalizingStaticWeighter.given(ImmutableMap.of(c1, 1d, c2, 2d));
		final String json = JsonSimpleGrade.toJson(w);

		final String expected = """
				{
				    "type": "NormalizingStaticWeighter",
				    "weights": {
				        "C1": 1.0,
				        "C2": 2.0
				    }
				}""";
		assertEquals(expected, json);
	}

	@Test
	void testReadStaticWeighter() throws Exception {
		final String input = """
				{
				    "type": "NormalizingStaticWeighter",
				    "weights": {
				        "C1": 1.0,
				        "C2": 2.0
				    }
				}""";
		final MarkAggregator read = JsonSimpleGrade.asMarkAggregator(input);

		final NormalizingStaticWeighter expected = NormalizingStaticWeighter.given(ImmutableMap.of(c1, 1d, c2, 2d));
		assertEquals(expected, read);
	}

	@Test
	void testWriteVoidAggregator() throws Exception {
		final VoidAggregator a = VoidAggregator.INSTANCE;
		final String json = JsonSimpleGrade.toJson(a);

		final String expected = """
				{
				    "type": "VoidAggregator"
				}""";
		assertEquals(expected, json);
	}

	@Test
	void testReadVoidAggregator() throws Exception {
		final String input = """
				{
				    "type": "VoidAggregator"
				}""";
		final MarkAggregator read = JsonSimpleGrade.asMarkAggregator(input);

		final VoidAggregator expected = VoidAggregator.INSTANCE;
		assertEquals(expected, read);
	}

	@Test
	void testWriteTrivialAggregator() throws Exception {
		final GradeAggregator a = GradeAggregator.TRIVIAL;
		final String json = JsonSimpleGrade.toJson(a);

		final String expected = """
				{
				    "markAggregator": {
				        "type": "VoidAggregator"
				    },
				    "subs": {
				    }
				}""";
		assertEquals(expected, json);
	}

	@Test
	void testReadTrivialAggregator() throws Exception {
		final String input = """
				{
				    "markAggregator": {
				        "type": "VoidAggregator"
				    },
				    "subs": {
				    }
				}""";
		final GradeAggregator read = JsonSimpleGrade.asAggregator(input);

		final GradeAggregator expected = GradeAggregator.TRIVIAL;
		assertEquals(expected, read);
	}

	@Test
	void testWriteAggregator() throws Exception {
		final GradeAggregator aggregator = GradeTestsHelper.get3Plus2().toAggregator();
		final String json = JsonSimpleGrade.toJson(aggregator);

		final String expected = Resources.toString(this.getClass().getResource("3Plus2 adapter.json"),
				StandardCharsets.UTF_8);
		assertEquals(expected, json);
	}

	@Test
	void testReadAggregator() throws Exception {
		final String input = Resources.toString(this.getClass().getResource("3Plus2 adapter.json"),
				StandardCharsets.UTF_8);
		final GradeAggregator read = JsonSimpleGrade.asAggregator(input);

		final GradeAggregator expected = GradeTestsHelper.get3Plus2().toAggregator();
		assertEquals(expected, read);
	}

	@Test
	void testWriteMarksTree() throws Exception {
		final MarksTree tree = MarksTreeTestsHelper.get3Plus2();
		final String json = JsonSimpleGrade.toJson(tree);

		final String expected = Resources.toString(this.getClass().getResource("3Plus2 marks.json"),
				StandardCharsets.UTF_8);
		assertEquals(expected, json);
	}

	@Test
	void testReadMarksTree() throws Exception {
		final MarksTree read = JsonSimpleGrade.asMarksTree(
				Resources.toString(this.getClass().getResource("3Plus2 marks.json"), StandardCharsets.UTF_8));

		final MarksTree expected = MarksTreeTestsHelper.get3Plus2();
		assertEquals(expected, read);
	}

	@Test
	void testWriteGrade() throws Exception {
		final Grade grade = GradeTestsHelper.get3Plus2();
		final String json = JsonSimpleGrade.toJson(grade);

		final String expected = Resources.toString(this.getClass().getResource("3Plus2 grade.json"),
				StandardCharsets.UTF_8);
		assertEquals(expected, json);
	}

	@Test
	void testReadGrade() throws Exception {
		final Grade read = JsonSimpleGrade
				.asGrade(Resources.toString(this.getClass().getResource("3Plus2 grade.json"), StandardCharsets.UTF_8));

		final Grade expected = GradeTestsHelper.get3Plus2();
		assertEquals(expected.toMarksTree(), read.toMarksTree());
		assertEquals(expected.toAggregator(), read.toAggregator());
	}

	@Test
	void testWriteExam() throws Exception {
		final Exam exam = ExamTestsHelper.get3Plus2();
		final String json = JsonSimpleGrade.toJson(exam);

		final String expected = Resources.toString(this.getClass().getResource("3Plus2 exam.json"),
				StandardCharsets.UTF_8);
		assertEquals(expected, json);
	}

	@Test
	void testReadExam() throws Exception {
		final Exam read = JsonSimpleGrade
				.asExam(Resources.toString(this.getClass().getResource("3Plus2 exam.json"), StandardCharsets.UTF_8));

		final Exam expected = ExamTestsHelper.get3Plus2();
		assertEquals(expected, read);
	}
}
