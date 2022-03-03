package io.github.oliviercailloux.grade.format.json;

import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.MarkAggregator;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MarksTreeTestsHelper;
import io.github.oliviercailloux.grade.NormalizingStaticWeighter;
import io.github.oliviercailloux.grade.ParametricWeighter;
import io.github.oliviercailloux.grade.old.GradeStructure;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JsonGradeTests {
	private static final Criterion C1 = Criterion.given("c1");
	private static final Criterion C2 = Criterion.given("c2");
	private static final Criterion C3 = Criterion.given("c3");

	private GradeStructure getStructure() {
		final GradeStructure emptyAbs = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final GradeStructure defaultMax = GradeStructure.maxWithDefault(ImmutableSet.of(), Optional.of(emptyAbs),
				ImmutableMap.of());
		final GradeStructure oneMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(C1), ImmutableMap.of());
		final GradeStructure toWrite = GradeStructure.givenWeights(ImmutableMap.of(C1, 1d),
				ImmutableMap.of(C1, emptyAbs, C2, defaultMax, C3, oneMax));
		return toWrite;
	}

	private Exam getExam() {
		return new Exam(getStructure(),
				ImmutableMap.of(GitHubUsername.given("g1"), getGrade(), GitHubUsername.given("g2"), getGrade2()));
	}

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
	void testWriteMarksTree() throws Exception {
		final MarksTree tree = MarksTreeTestsHelper.get3Plus2();
		final String json = JsonSimpleGrade.toJson(tree);

		final String expected = Resources.toString(this.getClass().getResource("3Plus2.json"), StandardCharsets.UTF_8);
		assertEquals(expected, json);
	}

	@Test
	void testReadMarksTree() throws Exception {
		final MarksTree read = JsonSimpleGrade
				.asMarksTree(Resources.toString(this.getClass().getResource("3Plus2.json"), StandardCharsets.UTF_8));

		final MarksTree expected = MarksTreeTestsHelper.get3Plus2();
		assertEquals(expected, read);
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
