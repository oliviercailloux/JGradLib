package io.github.oliviercailloux.grade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.format.json.JsonbGrade;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.grade.old.Mark;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class CompressorTests {
	@Test
	void testCompress() throws Exception {
		final WeightingGrade deflatedGrade = (WeightingGrade) JsonbGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("DeflatedGrade.json").toURI())));
		final IGrade compressedGrade = JsonbGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("CompressedGrade.json").toURI())));
		final IGrade obtained = Compressor.compress(deflatedGrade, GradeStructure.from(ImmutableSet.of("g1", "g2")));
		assertEquals(compressedGrade, obtained);
	}

	@Test
	void testCompressStruct() throws Exception {
		final GradeStructure model = GradeStructure.from(ImmutableSet.of("c1", "c2/sub"));
		final IGrade grade = WeightingGrade
				.from(ImmutableMap.of(GradePath.from("c1"), WeightedGrade.given(Mark.one(), 1d),
						GradePath.from("c2/Spurious/sub"), WeightedGrade.given(Mark.one(), 1d)));
		final IGrade expected = WeightingGrade.from(ImmutableMap.of(GradePath.from("c1"),
				WeightedGrade.given(Mark.one(), 1d), GradePath.from("c2/sub"), WeightedGrade.given(Mark.one(), 1d)));
		final IGrade obtained = Compressor.compress(grade, model);
		assertEquals(expected, obtained);
	}

	@Test
	void testCompressTwoTwo() throws Exception {
		final GradeStructure model = GradeStructure.from(ImmutableSet.of("user.name", "main/Class1", "main/Class2"));
		final WeightedGrade one = WeightedGrade.given(Mark.one(), 1d);
		final IGrade grade = WeightingGrade
				.from(ImmutableMap.of(GradePath.from("user.name"), one, GradePath.from("main/Impl/Class1"), one,
						GradePath.from("main/Impl/Class2"), one, GradePath.from("main/Warnings"), one));
		final IGrade obtained = Compressor.compress(grade, model);
		/* Fails currently, not sure whether it’s a bug or a feature. */
		assertEquals(model, obtained.toTree());
	}
}
