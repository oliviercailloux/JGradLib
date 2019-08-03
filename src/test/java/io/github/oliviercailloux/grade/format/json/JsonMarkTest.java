package io.github.oliviercailloux.grade.format.json;

import static io.github.oliviercailloux.grade.format.json.TestCriterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.format.json.JsonMarkWithCriterion;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

public class JsonMarkTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMarkTest.class);

	@Test
	public void markWithCriterionWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("MarkAndCriterion.json"),
				StandardCharsets.UTF_8);

		final CriterionAndMark grade = CriterionAndMark.max(ENC);
		final String written = JsonMarkWithCriterion.asJson(grade).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void markWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);

		final Mark mark = Mark.given(1d, "");
		final String written = JsonGrade.asJson(mark).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	void markReadJson() throws Exception {
		final Mark expected = Mark.given(1d, "");
		final String json = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);
		final Mark read = JsonGrade.asMark(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	@Test
	public void markWithCriterionReadJson() throws Exception {
		final CriterionAndMark expected = CriterionAndMark.max(ENC);
		final String json = Resources.toString(this.getClass().getResource("MarkAndCriterion.json"),
				StandardCharsets.UTF_8);
		final CriterionAndMark read = JsonMarkWithCriterion.asMark(json);
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	@Test
	void gradeSingletonWrite() throws Exception {
		final WeightingGrade grade = GradeTestsHelper.getSingletonWeightingGrade();

		final String written = JsonGrade.asJson(grade).toString();
		assertEquals(Resources.toString(getClass().getResource("SingletonGrade.json"), StandardCharsets.UTF_8),
				written);
	}

	@Test
	void gradeSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final ImmutableMap<Criterion, Mark> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
		final WeightingGrade expected = WeightingGrade.from(subMarks, weights);

		final String json = Resources.toString(this.getClass().getResource("SingletonGrade.json"),
				StandardCharsets.UTF_8);
		final WeightingGrade read = JsonGrade
				.asWeightingGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(expected, read);
	}

	@Test
	void criterionGradeWeightSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final CriterionGradeWeight expected = CriterionGradeWeight.from(criterion, Mark.given(1d, ""), 1d);

		final String json = Resources.toString(this.getClass().getResource("CriterionGradeWeight.json"),
				StandardCharsets.UTF_8);
		final CriterionGradeWeight read = JsonGrade.toCriterionGradeWeightAdapter()
				.adaptFromJson(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(expected, read);
	}

	@Test
	void gradeComplexWrite() throws Exception {
		final WeightingGrade composite = GradeTestsHelper.getComplexGrade();
		final PrintableJsonObject writtenJson = JsonGrade.asJson(composite);
		final JsonObject writtenJsonNoPoints = Json.createObjectBuilder(writtenJson).remove("points").build();

		final PrintableJsonObject jsonComplexGrade = PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(getClass().getResource("ComplexGrade.json"), StandardCharsets.UTF_8));
		final JsonObject jsonComplexGradeNoPoints = Json.createObjectBuilder(jsonComplexGrade).remove("points").build();

		assertEquals(jsonComplexGradeNoPoints, writtenJsonNoPoints);
	}

	@Test
	void gradeComplexRead() throws Exception {
		final WeightingGrade expected = GradeTestsHelper.getComplexGrade();

		final PrintableJsonObject jsonComplexGrade = PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(getClass().getResource("ComplexGrade.json"), StandardCharsets.UTF_8));
		final IGrade read = JsonGrade.asGrade(jsonComplexGrade);
		assertEquals(expected, read);
	}
}
