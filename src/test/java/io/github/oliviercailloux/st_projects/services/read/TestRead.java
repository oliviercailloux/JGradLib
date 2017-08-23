package io.github.oliviercailloux.st_projects.services.read;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRead {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestRead.class);

	@Test
	public void test() throws IOException {
		LOGGER.info("Started.");
		final ProjectReader reader = new ProjectReader();
		try (InputStreamReader sourceReader = new InputStreamReader(
				getClass().getResourceAsStream("Assisted Board Games.adoc"), StandardCharsets.UTF_8)) {
			reader.read(sourceReader);
		}
	}
}
