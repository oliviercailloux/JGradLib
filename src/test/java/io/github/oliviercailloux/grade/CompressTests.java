package io.github.oliviercailloux.grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.utils.Summarize;

public class CompressTests {
	@Test
	void testCompress() throws Exception {
		final WeightingGrade deflatedGrade = (WeightingGrade) JsonGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("DeflatedGrade.json").toURI())));
		final IGrade compressedGrade = JsonGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("CompressedGrade.json").toURI())));
		final IGrade obtained = Summarize.compress(deflatedGrade);
		assertEquals(compressedGrade, obtained);
	}
}
