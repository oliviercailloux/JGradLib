package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JsonGradeTests {
	@Test
	void testMap() throws Exception {
//		assertEquals("{}", JsonGrade.toJson(GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of())));
		final GradeStructure sub = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final Criterion c1 = Criterion.given("c1");
		final GradeStructure s = GradeStructure.givenWeights(ImmutableMap.of(c1, 1d), ImmutableMap.of(c1, sub));
		assertEquals("{\"c1\":1.0}", JsonGrade.toJson(s));
	}

	@Test
	void testMapB() throws Exception {
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final Criterion c3 = Criterion.given("c3");
		final GradeStructure emptyAbs = GradeStructure.givenWeights(ImmutableMap.of(), ImmutableMap.of());
		final GradeStructure emptyMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(), ImmutableMap.of());
		final GradeStructure oneMax = GradeStructure.maxWithGivenAbsolutes(ImmutableSet.of(c1), ImmutableMap.of());
		final GradeStructure s = GradeStructure.givenWeights(ImmutableMap.of(c1, 1d),
				ImmutableMap.of(c1, emptyAbs, c2, emptyMax, c3, oneMax));

		assertEquals(Resources.toString(this.getClass().getResource("GradeStructure.json"), StandardCharsets.UTF_8),
				JsonbSimpleGrade.toJson(s));
	}
}
