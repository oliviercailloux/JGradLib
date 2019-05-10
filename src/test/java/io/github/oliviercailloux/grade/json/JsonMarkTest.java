package io.github.oliviercailloux.grade.json;

import static io.github.oliviercailloux.grade.json.TestCriterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.json.JsonMark;

public class JsonMarkTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMarkTest.class);

	@Test
	public void markWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);

		final Grade grade = Grade.max(ENC);
		final String written = JsonMark.asJson(grade).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void markReadJson() throws Exception {
		final Grade expected = Grade.max(ENC);
		final String json = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);
		final Grade read = JsonMark.asMark(json);
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

}
