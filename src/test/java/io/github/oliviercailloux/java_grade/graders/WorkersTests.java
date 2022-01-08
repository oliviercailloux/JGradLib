package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.utils.Utils;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkersTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(WorkersTests.class);

	@Test
	void testNull() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "Workers/Null/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, WorkersGrader::grade);
		assertEquals(0d, codeGrade.getPoints(), 1e-6d);
	}

	@Test
	void testBad() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "Workers/Bad/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, WorkersGrader::grade);
		assertEquals(0d, codeGrade.getPoints(), 1e-6d);
	}

	@Test
	void testPerfect() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "Workers/Perfect/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, WorkersGrader::grade);
		assertEquals(1d, codeGrade.getPoints());
	}

	@Test
	void testPerfectNoLog() throws Exception {
		final Path compiledDir = Utils.getTempUniqueDirectory("compile");
		final String subPath = "Workers/Perfect no log/";
		new CoffeeTests().compile(subPath, compiledDir);

		final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, WorkersGrader::grade);
//		Files.writeString(Path.of("out.html"), XmlUtils.toString(HtmlGrades.asHtml(codeGrade, "Perfectnl", 19.5d)));
//		assertEquals(7.5d / 9.5d, codeGrade.getPoints(), 1e-6d);
		assertTrue(codeGrade.getPoints() > 0.9d);
	}
}
