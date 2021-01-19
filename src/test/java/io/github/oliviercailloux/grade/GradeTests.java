package io.github.oliviercailloux.grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GradeTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradeTests.class);

	@Test
	void testPatchReplace() throws Exception {
		final Patch patch = Patch.create(ImmutableList.of(), Mark.one("A comment"));
		final IGrade patched = GradeTestsHelper.getMinGrade().withPatches(ImmutableSet.of(patch));
		assertEquals(Mark.one("A comment"), patched);
	}

	@Test
	void testPatchReplaceComplex() throws Exception {
		final Patch patch = Patch.create(ImmutableList.of(), Mark.one("A comment"));
		final IGrade patched = GradeTestsHelper.getComplexGrade().withPatches(ImmutableSet.of(patch));
		assertEquals(Mark.one("A comment"), patched);
	}

	@Test
	void testPatchComplex() throws Exception {
		final Criterion c1 = Criterion.given("C1");
		final Criterion c2 = Criterion.given("C2");
		final Criterion criterion = Criterion.given("criterion");
		final ImmutableList<Criterion> c1SlashCriterion = ImmutableList.of(c1, criterion);
		final Patch patch = Patch.create(c1SlashCriterion, Mark.zero("A comment"));

		final WeightingGrade complexGrade = GradeTestsHelper.getComplexGrade();
		assertEquals(Mark.one(), complexGrade.getGrade(c1SlashCriterion).get());

		final IGrade patched = complexGrade.withPatches(ImmutableSet.of(patch));
		assertEquals(complexGrade.getSubGrades().get(c2), patched.getSubGrades().get(c2));
		assertEquals(Mark.zero("A comment"), patched.getGrade(c1SlashCriterion).get());
	}
}
