package io.github.oliviercailloux.grade.markers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Jimfs;

class MarksTests {

	@Test
	void testFileMatching() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path file = fs.getPath("file.txt");
			final String exactTarget = "Hello, world";
			final Pattern approximateTarget = Pattern.compile("[\\h\\v]*\"?Hello,?\\h*world\"?[\\h\\v]*",
					Pattern.CASE_INSENSITIVE);

			Files.writeString(file, "Hello, world");
			assertEquals(1.0d, Marks.fileMatchesGrade(file, exactTarget, approximateTarget).getPoints());

			Files.writeString(file, "Hello, world \n");
			assertEquals(1.0d, Marks.fileMatchesGrade(file, exactTarget, approximateTarget).getPoints());

			Files.writeString(file, "  hello world \r\n");
			assertEquals(0.9d, Marks.fileMatchesGrade(file, exactTarget, approximateTarget).getPoints());

			Files.writeString(file, "phello world \r\n");
			assertEquals(0.5d, Marks.fileMatchesGrade(file, exactTarget, approximateTarget).getPoints());

			assertEquals(0d, Marks.fileMatchesGrade(fs.getPath("nonexistentfile.txt"), exactTarget, approximateTarget)
					.getPoints());
		}
	}

}
