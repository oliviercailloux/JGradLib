package io.github.oliviercailloux.grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.format.json.JsonGradeTests;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		final GradePath c1SlashCriterion = GradePath.from(ImmutableList.of(c1, criterion));
		final Patch patch = Patch.create(c1SlashCriterion, Mark.zero("A comment"));

		final WeightingGrade complexGrade = GradeTestsHelper.getComplexGrade();
		assertEquals(Mark.one(), complexGrade.getGrade(c1SlashCriterion).get());

		final IGrade patched = complexGrade.withPatches(ImmutableSet.of(patch));
		assertEquals(complexGrade.getSubGrades().get(c2), patched.getSubGrades().get(c2));
		assertEquals(Mark.zero("A comment"), patched.getGrade(c1SlashCriterion).get());
	}

	@Test
	void testTree() throws Exception {
		final IGrade grade = JsonGrade.asGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(JsonGradeTests.class.getResource("ComplexGrade.json"), StandardCharsets.UTF_8)));
//		final IGrade grade = JsonGrade
//				.asGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(Files.readString(Path.of("grade.json"))));
		final GradeStructure actual = grade.toTree();
		LOGGER.info("Structure: {}.", actual);
		assertEquals(GradeStructure.from(ImmutableSet.of("C1/criterion", "C2")), actual);
	}

	@Test
	void testBuildKeepsOrderOfKeys() throws Exception {
		final IGrade grade = WeightingGrade.from(ImmutableMap.of(GradePath.from("user.name"),
				WeightedGrade.given(Mark.one(), 1d), GradePath.from("main"), WeightedGrade.given(Mark.one(), 1d)));
		assertEquals(ImmutableList.of(Criterion.given("user.name"), Criterion.given("main")),
				grade.getSubGrades().keySet().asList());
		final GradeStructure tree = grade.toTree();
		assertEquals(ImmutableList.of(GradePath.ROOT, GradePath.from("user.name"), GradePath.from("main")),
				ImmutableList.copyOf(tree.asGraph().nodes()));
		assertEquals(ImmutableList.of(Criterion.given("user.name"), Criterion.given("main")),
				tree.getSuccessorCriteria(GradePath.ROOT).asList());
	}

	@Test
	void testDissolve() throws Exception {
		final IGrade grade = WeightingGrade
				.from(ImmutableMap.of(GradePath.from("c1"), WeightedGrade.given(Mark.given(0.5d, ""), 4d),
						GradePath.from("c2"), WeightedGrade.given(Mark.one(), 1d)));
		final Criterion c1 = Criterion.given("c1");
		final Criterion c2 = Criterion.given("c2");
		final IGrade dissolved = grade.withDissolved(c2);

		final GradeStructure expectedTree = GradeStructure.from(ImmutableSet.of("c1/c1", "c1/c2"));
		assertEquals(expectedTree, dissolved.toTree());

		assertEquals(1d, dissolved.getWeights().get(c1), 1e-6d);
		assertEquals(4d / 5d, dissolved.getLocalWeight(GradePath.from("c1/c1")), 1e-6d);
		assertEquals(1d / 5d, dissolved.getLocalWeight(GradePath.from("c1/c2")), 1e-6d);

		assertEquals(3d / 5d, grade.getPoints(), 1e-6d);
		assertEquals(3d / 5d, dissolved.getPoints(), 1e-6d);
	}

	/**
	 * TODO this fails, presumably because of the zero VS one weight.
	 */
	@Test
	void testDissolveLate() throws Exception {
		final IGrade grade = JsonGrade.asGrade(PrintableJsonObjectFactory.wrapPrettyPrintedString(
				Resources.toString(getClass().getResource("Late grade.json"), StandardCharsets.UTF_8)));
		final Criterion cGrade = Criterion.given("grade");
		final Criterion cUser = Criterion.given("user.name");
		final Criterion cTime = Criterion.given("Time penalty");
		final IGrade dissolved = grade.withDissolved(cTime);
		LOGGER.debug("Dissolved: {}.", dissolved);
		assertEquals(1d / 20d, dissolved.getSubGrades().get(cGrade).getWeights().get(cUser), 1e-6d);
	}

	@Test
	void testDissolveStructure() throws Exception {
		final WeightedGrade one = WeightedGrade.given(Mark.one(), 1d);
		final GradePath w = GradePath.from("Warnings");
		final IGrade grade = WeightingGrade
				.from(ImmutableMap.of(GradePath.from("Impl/Class1"), one, GradePath.from("Impl/Class2"), one, w, one));
		final Criterion wc = Criterion.given("Warnings");
		final IGrade dissolved = grade.withDissolved(wc);

		final GradeStructure expectedTree = GradeStructure.from(ImmutableSet.of("Impl/Class1/Class1",
				"Impl/Class1/Warnings", "Impl/Class2/Class2", "Impl/Class2/Warnings"));
		assertEquals(expectedTree, dissolved.toTree());
	}
}
