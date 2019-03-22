package io.github.oliviercailloux.grade.mycourse.other_formats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

class MyCourseHomeworkReaderTest {

	@Test
	void test() throws IOException {
		final List<String> lines = Resources.readLines(
				getClass().getResource("Homework name GitHub_username_Tentative_2019-03-11-00-00-47.txt"),
				StandardCharsets.UTF_8);
		LOGGER.info("Testing from {}.", lines);
		final MyCourseHomeworkReader reader = new MyCourseHomeworkReader(null, null, null);
		reader.readAuthor(lines);
		assertEquals("FIRSTNAME LAST NAME IN SEVERAL WORDS", reader.getLastNameRead());
		assertEquals("username", reader.getLastUsernameRead());
		assertEquals(
				"<span style=\"color: rgb(34,34,34);font-family: Consolas , &quot;Lucida Console&quot; , &quot;Courier New&quot; , monospace;\">Some content</span>",
				reader.getContent(lines));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MyCourseHomeworkReaderTest.class);

}
