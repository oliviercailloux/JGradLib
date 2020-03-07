package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

class JsonCriterionTests {
	public static enum TestCriterion implements Criterion {
		TEST_CRITERION;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonCriterionTests.class);

	@Test
	void testWriteSimple() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("SimpleCriterion.json"),
				StandardCharsets.UTF_8);
		final String written = JsonCriterion.asJson(Criterion.given("Simple criterion")).toString();
		assertEquals(expected, written);
	}

	@Test
	void testWriteEnum() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("PomCriterion.json"),
				StandardCharsets.UTF_8);
		final String written = JsonCriterion.asJson(JavaCriterion.POM).toString();
		assertEquals(expected, written);
	}

	@Test
	void testWriteInternalEnum() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("InternalCriterion.json"),
				StandardCharsets.UTF_8);
		final String written = JsonCriterion.asJson(TestCriterion.TEST_CRITERION).toString();
		assertEquals(expected, written);
	}

	@Test
	void testReadInvalid() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("NullClassCriterion.json"),
				StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class,
				() -> JsonCriterion.asCriterion(PrintableJsonObjectFactory.wrapPrettyPrintedString(json)));
	}

	@Test
	void testReadSimple() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("SimpleCriterion.json"),
				StandardCharsets.UTF_8);
		final Criterion criterion = JsonCriterion.asCriterion(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(Criterion.given("Simple criterion"), criterion);
	}

	@Test
	void testReadEnum() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("PomCriterion.json"),
				StandardCharsets.UTF_8);
		final Criterion criterion = JsonCriterion.asCriterion(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(JavaCriterion.POM, criterion);
	}

	@Test
	void testReadEnumUsingAdapter() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("PomCriterion.json"),
				StandardCharsets.UTF_8);
		final Criterion criterion = JsonbUtils.fromJson(json, Criterion.class, JsonCriterion.asAdapter());
		assertEquals(JavaCriterion.POM, criterion);
	}

	@Test
	void testReadInternalEnum() throws Exception {
		final String json = Resources.toString(this.getClass().getResource("InternalCriterion.json"),
				StandardCharsets.UTF_8);
		final Criterion criterion = JsonCriterion.asCriterion(PrintableJsonObjectFactory.wrapPrettyPrintedString(json));
		assertEquals(TestCriterion.TEST_CRITERION, criterion);
	}

}
