package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c11;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c111;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c12;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c13;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c21;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c22;

import com.google.common.collect.ImmutableMap;

public class MarksTreeTestsHelper {

	public static MarksTree get11_111() {
		final MarksTree m111 = MarksTree.composite(ImmutableMap.of(c111, Mark.one()));
		return MarksTree.composite(ImmutableMap.of(c11, m111));
	}

	public static MarksTree get1_11_111() {
		return MarksTree.composite(ImmutableMap.of(c1, get11_111()));
	}

	public static MarksTree get1_1() {
		final MarksTree m1 = MarksTree.composite(ImmutableMap.of(c1, Mark.one()));
		return MarksTree.composite(ImmutableMap.of(c1, m1));
	}

	public static MarksTree get1_1_1() {
		return MarksTree.composite(ImmutableMap.of(c1, get1_1()));
	}

	/**
	 * Returns the following tree.
	 * <ul>
	 * <li>c11: 1</li>
	 * <li>c12: 0</li>
	 * <li>c21: 1</li>
	 * <li>c22: 0</li>
	 * </ul>
	 */
	public static MarksTree get1_11And1_12And2_21And2_22() {
		final MarksTree m1 = MarksTree.composite(ImmutableMap.of(c11, Mark.one(), c12, Mark.zero()));
		final MarksTree m2 = MarksTree.composite(ImmutableMap.of(c21, Mark.one(), c22, Mark.zero()));
		return MarksTree.composite(ImmutableMap.of(c1, m1, c2, m2));
	}

	/**
	 * Returns the following tree.
	 * <ul>
	 * <li>c11: 1</li>
	 * <li>c12: 0.5</li>
	 * <li>c21: 1</li>
	 * <li>c22: 0.5</li>
	 * </ul>
	 */
	public static MarksTree get1_11And1_12And2_21And2_22Half() {
		final MarksTree m1 = MarksTree.composite(ImmutableMap.of(c11, Mark.one(), c12, Mark.given(0.5d, "")));
		final MarksTree m2 = MarksTree.composite(ImmutableMap.of(c21, Mark.one(), c22, Mark.given(0.5d, "")));
		return MarksTree.composite(ImmutableMap.of(c1, m1, c2, m2));
	}

	public static MarksTree get1_1And1_2And2_2And2_1() {
		final MarksTree m1 = MarksTree.composite(ImmutableMap.of(c1, Mark.one(), c2, Mark.zero()));
		final MarksTree m2 = MarksTree.composite(ImmutableMap.of(c2, Mark.one(), c1, Mark.zero()));
		return MarksTree.composite(ImmutableMap.of(c1, m1, c2, m2));
	}

	/**
	 * <ul>
	 * <li>C1</li>
	 * <ul>
	 * <li>C1.1: 0.3</li>
	 * <li>C1.2: 0.2</li>
	 * <li>C1.3: 1.0</li>
	 * </ul>
	 * <li>C2</li>
	 * <ul>
	 * <li>C2.1: 0</li>
	 * <li>C2.2: 0.8</li>
	 * </ul>
	 * </ul>
	 *
	 */
	public static MarksTree get3Plus2() {
		final MarksTree t1 = MarksTree.composite(
				ImmutableMap.of(c11, Mark.given(0.3d, ""), c12, Mark.given(0.2d, ""), c13, Mark.given(1.0d, "")));
		final MarksTree t2 = MarksTree.composite(ImmutableMap.of(c21, Mark.given(0d, ""), c22, Mark.given(0.8d, "")));
		return MarksTree.composite(ImmutableMap.of(c1, t1, c2, t2));
	}

}
