package io.github.oliviercailloux.grade.json;

import static io.github.oliviercailloux.grade.json.TestCriterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.json.JsonbUtils;

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
		final String written = JsonbUtils.toJsonObject(mark).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	void markReadJson() throws Exception {
		final Mark expected = Mark.given(1d, "");
		final String json = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);
		final Mark read = JsonbUtils.fromJson(json, Mark.class);
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
		final Criterion criterion = Criterion.given("criterion");
		final ImmutableMap<Criterion, IGrade> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
		final WeightingGrade grade = WeightingGrade.from(subMarks, weights);

		final String written = JsonbUtils.toJsonObject(grade, JsonCriterion.asAdapter()).toString();
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
		final WeightingGrade read = JsonbUtils.fromJson(json, WeightingGrade.class, JsonCriterion.asAdapter(),
				JsonGrade.asAdapter());
		assertEquals(expected, read);
	}

	@Test
	void criterionGradeWeightSingletonRead() throws Exception {
		final Criterion criterion = Criterion.given("criterion");
		final CriterionGradeWeight expected = CriterionGradeWeight.from(criterion, Mark.given(1d, ""), 1d);

		final String json = Resources.toString(this.getClass().getResource("CriterionGradeWeight.json"),
				StandardCharsets.UTF_8);
		final CriterionGradeWeight read = JsonbUtils.fromJson(json, CriterionGradeWeight.class,
				JsonCriterion.asAdapter());
		assertEquals(expected, read);
	}
}
