package io.github.oliviercailloux.grade.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
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
}
