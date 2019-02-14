package io.github.oliviercailloux.st_projects.services.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.github.oliviercailloux.json.JsonStringToObjectWrapper;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;

class JsonStudentTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentTest.class);

	@Test
	public void studentMyCourseReadJson() throws Exception {
		final StudentOnMyCourse expected = getStudentOnMyCourse();
		final PrintableJsonObject readObj = readObject("Student MyCourse.json");
		final StudentOnMyCourse read = StudentOnMyCourse.fromJson(readObj);
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	@Test
	public void studentMyCourseWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Student MyCourse.json"),
				StandardCharsets.UTF_8);

		final StudentOnMyCourse student = getStudentOnMyCourse();
		final String written = student.asJson().toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	@Test
	public void studentGitHubWriteJson() throws Exception {
		final String expected = Resources.toString(this.getClass().getResource("Student GitHub.json"),
				StandardCharsets.UTF_8);

		final StudentOnGitHubKnown studentGH = getStudentOnGitHubKnown();
		final String written = JsonStudentOnGitHubKnown.asJson(studentGH).toString();
		LOGGER.info("Serialized pretty json: {}.", written);
		assertEquals(expected, written);
	}

	private StudentOnGitHubKnown getStudentOnGitHubKnown() {
		final StudentOnMyCourse studentMC = getStudentOnMyCourse();
		final StudentOnGitHubKnown studentGH = StudentOnGitHubKnown.with(studentMC, "g");
		return studentGH;
	}

	private StudentOnMyCourse getStudentOnMyCourse() {
		final StudentOnMyCourse studentMC = StudentOnMyCourse.with(1, "f", "l", "u");
		return studentMC;
	}

	@Test
	public void studentGitHubReadJson() throws Exception {
		final StudentOnGitHubKnown expected = getStudentOnGitHubKnown();
		final PrintableJsonObject readObj = readObject("Student GitHub.json");
		final StudentOnGitHubKnown read = JsonStudentOnGitHubKnown.asStudentOnGitHubKnown(readObj);
		LOGGER.info("Deserialized: {}.", read);
		assertEquals(expected, read);
	}

	public PrintableJsonObject readObject(String resource) throws IOException {
		return JsonStringToObjectWrapper
				.wrapPrettyPrinted(Resources.toString(this.getClass().getResource(resource), StandardCharsets.UTF_8));
	}

}
