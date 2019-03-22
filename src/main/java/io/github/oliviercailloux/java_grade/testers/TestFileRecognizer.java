package io.github.oliviercailloux.java_grade.testers;

import java.nio.file.Path;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.context.FilesSource;

public class TestFileRecognizer {

	private final static Pattern HAS_JUNIT_TEST_CONTENT = Pattern
			.compile("(\\h*@Test)|(org\\.junit\\.jupiter\\.api\\.Assertions)");

	public static boolean isSurefireTestFile(Path path) {
		final boolean ok = path.startsWith("src/test/java")
				&& path.getFileName().toString().matches("(Test.*)|(.*Test)\\.java");
		LOGGER.debug("Testing whether is Surefire test file on {}: {}.", path, ok);
		return ok;
	}

	public static boolean isTestFile(FileContent f) {
		final Path path = f.getPath();
		if (isSurefireTestFile(path)) {
			return true;
		}
		/**
		 * We must do it in two steps because we do not want to call getContent()
		 * unnecessarily.
		 */
		return HAS_JUNIT_TEST_CONTENT.matcher(f.getContent()).find();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFileRecognizer.class);

	public static FilesSource getTestFiles(FilesSource f) {
		return f.filter(TestFileRecognizer::isTestFile);
	}
}
