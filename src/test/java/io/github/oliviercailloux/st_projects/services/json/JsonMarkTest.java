package io.github.oliviercailloux.st_projects.services.json;

import static io.github.oliviercailloux.st_projects.ex2.Ex2Criterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.github.oliviercailloux.st_projects.model.Mark;

public class JsonMarkTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMarkTest.class);

	@Test
	public void markWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);

		final Mark grade = Mark.max(ENC);
		final String written = JsonMark.asJson(grade).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void markReadJson() throws Exception {
		final Mark expected = Mark.max(ENC);
		final String json = Resources.toString(this.getClass().getResource("Mark.json"), StandardCharsets.UTF_8);
		final Mark read = JsonMark.asMark(json);
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

}
