package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Patch;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.JsonbException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonGradeTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonGradeTests.class);

	@Test
	public void markWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);

		final Mark mark = Mark.given(1d, "");
		final String written = JsonbGrade.asJson(mark).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	void markReadJson() throws Exception {
		final Mark expected = Mark.given(1d, "");
		final String json = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);
		final Mark read = JsonbGrade.asMark(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	@Test
	void gradeSingletonWrite() throws Exception {
		final WeightingGrade grade = GradeTestsHelper.getSingletonWeightingGrade();

		final String written = JsonbGrade.asJson(grade).toString();
		assertEquals(Resources.toString(getClass().getResource("SingletonGrade.json"), StandardCharsets.UTF_8),
				written);
	}

	@Test
	void gradeNameAndSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final ImmutableMap<Criterion, Mark> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
		final WeightingGrade expectedGrade = WeightingGrade.from(subMarks, weights, "A comment");
		final ImmutableMap<String, WeightingGrade> expected = ImmutableMap.of("student name", expectedGrade);

		@SuppressWarnings("serial")
		final Type type = new LinkedHashMap<String, WeightingGrade>() {
		}.getClass().getGenericSuperclass();
		@SuppressWarnings("serial")
		final Type typeVague = new LinkedHashMap<String, IGrade>() {
		}.getClass().getGenericSuperclass();

		final String json = Resources.toString(this.getClass().getResource("NameAndSingletonGrade.json"),
				StandardCharsets.UTF_8);
		final Map<String, IGrade> read = JsonbUtils.fromJson(json, type, JsonbGrade.asAdapter());
		assertEquals(expected, read);

		final Map<String, IGrade> readVague = JsonbUtils.fromJson(json, typeVague, JsonbGrade.asAdapter());
		assertEquals(expected, readVague);
	}

	/**
	 * Tests whether reading weights that slightly do not sum to one results in
	 * computing the points in a different way than when exporting initially. This
	 * could be due to normalizing the weights again after reading. We want, on the
	 * contrary, that exporting then reading again rounds-trip.
	 */
	@Test
	void gradePrecisionRoundTrip() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("PrecisionGrade.json"),
				StandardCharsets.UTF_8);
		final WeightingGrade read = JsonbGrade
				.asWeightingGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		final double pointsReadAndComputed = read.getPoints();
		final PrintableJsonObject exported = JsonbGrade.asJson(read);
		final WeightingGrade readAgain = JsonbGrade.asWeightingGrade(exported);
		assertEquals(pointsReadAndComputed, readAgain.getPoints());
	}

	@Test
	void gradeSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final ImmutableMap<Criterion, Mark> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
		final WeightingGrade expected = WeightingGrade.from(subMarks, weights, "A comment");

		final String json = Resources.toString(this.getClass().getResource("SingletonGrade.json"),
				StandardCharsets.UTF_8);
		final WeightingGrade read = JsonbGrade
				.asWeightingGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(expected, read);

		final IGrade direct = JsonbUtils.fromJson(json, WeightingGrade.class, JsonbGrade.asAdapter());
		assertEquals(expected, direct);
	}

	@Test
	void gradeSingletonNoNameRead() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("SingletonGradeNoName.json"),
				StandardCharsets.UTF_8);
		assertThrows(JsonbException.class,
				() -> JsonbGrade.asWeightingGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(json)));
	}

	@Test
	void criterionGradeWeightSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final CriterionGradeWeight expected = CriterionGradeWeight.from(criterion, Mark.given(1d, ""), 1d);

		final String json = Resources.toString(this.getClass().getResource("CriterionGradeWeight.json"),
				StandardCharsets.UTF_8);
		final CriterionGradeWeight read = JsonbGrade.create().toCriterionGradeWeightAdapter()
				.adaptFromJson(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(expected, read);
	}

	@Test
	void gradeComplexWrite() throws Exception {
		final WeightingGrade composite = GradeTestsHelper.getComplexGrade();
		final PrintableJsonObject writtenJson = JsonbGrade.asJson(composite);
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
		final IGrade read = JsonbGrade.asGrade(jsonComplexGrade);
		assertEquals(expected, read);
	}

	@Test
	void gradeDoubleRead() throws Exception {
		/**
		 * This grade was read in the wrong order when using a set instead of a list as
		 * the json transmission type.
		 */
		final PrintableJsonObject jsonGrade = PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(getClass().getResource("DoubleGrade.json"), StandardCharsets.UTF_8));
		LOGGER.debug("Wrapped: {}.", jsonGrade);
		final WeightingGrade read = (WeightingGrade) JsonbGrade.asGrade(jsonGrade);
		assertEquals(ImmutableList.of(JavaCriterion.COMMIT.asCriterion(), JavaCriterion.ID.asCriterion()),
				read.getSubGrades().keySet().asList());
	}

	@Test
	void patchRead() throws Exception {
		final PrintableJsonObject json = PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(getClass().getResource("Patch.json"), StandardCharsets.UTF_8));
		LOGGER.debug("Wrapped: {}.", json);
		final Patch read = JsonbGrade.asPatch(json.toString());
		final Patch expected = Patch.create(ImmutableList.of(Criterion.given("C1"), Criterion.given("C1-1")),
				Mark.one("A comment"));
		assertEquals(expected, read);
	}

}
