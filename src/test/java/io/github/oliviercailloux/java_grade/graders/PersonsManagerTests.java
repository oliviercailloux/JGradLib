package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.utils.Utils;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonsManagerTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PersonsManagerTests.class);

	@Test
	void testBad() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "PersonsManager/Bad/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, PersonsManagerGrader::grade);
//		Files.writeString(Path.of("out.html"), XmlUtils.toString(HtmlGrades.asHtml(codeGrade, "Bad", 19.5d)));
		/* Counter starts at zero. */
		assertEquals(0.5d / 19.5d, codeGrade.getPoints(), 1e-6d);
	}

	@Test
	void testPerfect() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "PersonsManager/Perfect/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, PersonsManagerGrader::grade);
		assertEquals(1d, codeGrade.getPoints());
	}
}
