package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.grade.IGrade;

class AdminTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AdminTests.class);

	@Test
	void testAlmostEmpty() throws Exception {
		final Path umlSource = Path.of(getClass().getResource("Almost empty.uml").toURI());
		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path di = fs.getPath("stuff.di");
			Files.createFile(di);
			Files.copy(umlSource, fs.getPath("uml.uml"));
			final IGrade grade = AdminManagesUsers.grade(fs.getPath("").toAbsolutePath());
			assertEquals(1d / 19d, grade.getPoints(), 1e-6);
		}
	}

	@Test
	void testBad() throws Exception {
		final Path uml = Path.of(getClass().getResource("Admin manages users.uml").toURI());
		final IGrade grade = AdminManagesUsers.grade(uml);
//		Files.writeString(Path.of("Grade.html"), XmlUtils.toString(HtmlGrades.asHtml(grade, "Test grade")));
		assertEquals((1d + 1.5d + 1d + 1.5d) / 19d, grade.getPoints(), 1e-6);
	}

	@Test
	void testPerfect() throws Exception {
		final Path uml = Path.of(getClass().getResource("Perfect.uml").toURI());
		final IGrade grade = AdminManagesUsers.grade(uml);
		assertEquals(1d, grade.getPoints());
	}

}
