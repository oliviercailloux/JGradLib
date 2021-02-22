package io.github.oliviercailloux.grade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;

public class CompressorTests {
	@Test
	void testCompress() throws Exception {
		final WeightingGrade deflatedGrade = (WeightingGrade) JsonGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("DeflatedGrade.json").toURI())));
		final IGrade compressedGrade = JsonGrade
				.asGrade(Files.readString(Path.of(getClass().getResource("CompressedGrade.json").toURI())));
		final IGrade obtained = Compressor.compress(deflatedGrade, asStructure(ImmutableSet.of("g1", "g2")));
		assertEquals(compressedGrade, obtained);
	}

	public static GradePath asPath(String pathString) {
		return GradePath.from(ImmutableList.copyOf(pathString.split("/")).stream().map(Criterion::given)
				.collect(ImmutableList.toImmutableList()));
	}

	public static GradeStructure asStructure(Set<String> paths) {
		return GradeStructure.given(paths.stream().map(CompressorTests::asPath).collect(ImmutableSet.toImmutableSet()));
	}
}
