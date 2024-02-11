package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p11;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p12;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p2;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p21;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p22;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c11;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c12;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c21;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c22;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c3;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

public class GradeTests {
  @Test
  void testAggregateMaxAndStatic() throws Exception {
    final GradeAggregator c1A = GradeAggregator.MAX;
    final GradeAggregator c2A = GradeAggregator.staticAggregator(ImmutableMap.of(c21, 1d, c22, 2d),
        ImmutableMap.of());
    final GradeAggregator aggregator = GradeAggregator.max(ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);

    final ImmutableMap<SubMark, Double> weightedSubMarks = grade.getWeightedSubMarks();
    final ImmutableMap<SubMark, Double> expectedWeightedSubMarks = ImmutableMap
        .of(SubMark.given(c1, Mark.given(1d, "")), 1d, SubMark.given(c2, Mark.given(1d / 3d, "")), 0d);
    assertEquals(expectedWeightedSubMarks, weightedSubMarks);

    assertEquals(1d, grade.getGrade(p11).mark().getPoints());
    assertEquals(0d, grade.getGrade(p12).mark().getPoints());
    assertEquals(1d, grade.getGrade(c1).mark().getPoints());
    assertEquals(1d, grade.getGrade(p1).mark().getPoints());
    assertEquals(1d, grade.getGrade(p21).mark().getPoints());
    assertEquals(0d, grade.getGrade(p22).mark().getPoints());
    assertEquals(1d / 3d, grade.getGrade(c2).mark().getPoints(), 1e-6d);
    assertEquals(1d / 3d, grade.getGrade(p2).mark().getPoints(), 1e-6d);
    assertEquals(1d, grade.mark().getPoints());
  }

  @Test
  void testAggregateOwaAndStatic() throws Exception {
    final GradeAggregator c1A = GradeAggregator.owa(ImmutableList.of(1d, 3d));
    final GradeAggregator c2A = GradeAggregator.staticAggregator(ImmutableMap.of(c21, 1d, c22, 2d),
        ImmutableMap.of());
    final GradeAggregator aggregator = GradeAggregator.max(ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);

    assertEquals(1d, grade.getGrade(p11).mark().getPoints());
    assertEquals(0d, grade.getGrade(p12).mark().getPoints());
    assertEquals(1d / 4d, grade.getGrade(c1).mark().getPoints(), 1e-6d);
    assertEquals(1d / 4d, grade.getGrade(p1).mark().getPoints(), 1e-6d);
    assertEquals(1d, grade.getGrade(p21).mark().getPoints());
    assertEquals(0d, grade.getGrade(p22).mark().getPoints());
    assertEquals(1d / 3d, grade.getGrade(c2).mark().getPoints(), 1e-6d);
    assertEquals(1d / 3d, grade.getGrade(p2).mark().getPoints(), 1e-6d);
    assertEquals(1d / 3d, grade.mark().getPoints(), 1e-6d);

    final ImmutableMap<SubMark, Double> weightedSubMarks = grade.getWeightedSubMarks();
    final ImmutableBiMap<Criterion, SubMark> byCrit = weightedSubMarks.keySet().stream()
        .collect(ImmutableBiMap.toImmutableBiMap(SubMark::getCriterion, s -> s));
    final SubMark s1 = byCrit.get(c1);
    final double ws1 = weightedSubMarks.get(s1);
    final SubMark s2 = byCrit.get(c2);
    final double ws2 = weightedSubMarks.get(s2);
    assertEquals(1d / 4d, s1.getPoints());
    assertEquals(0d, ws1);
    assertEquals(1d / 3d, s2.getPoints());
    assertEquals(1d, ws2);
    final ImmutableMap<SubMark, Double> expectedWeightedSubMarks = ImmutableMap
        .of(SubMark.given(c1, Mark.given(1d / 4d, "")), 0d, SubMark.given(c2, Mark.given(1d / 3d, "")), 1d);
    assertEquals(expectedWeightedSubMarks, weightedSubMarks);
  }

  @Test
  void testParametricAndAbsolute() throws Exception {
    final GradeAggregator c1A = GradeAggregator.parametric(c11, c12, GradeAggregator.TRIVIAL);
    final GradeAggregator c2A = GradeAggregator.ABSOLUTE;
    final GradeAggregator aggregator = GradeAggregator.parametric(c1, c2, ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);
    assertEquals(1d, grade.getGrade(p11).mark().getPoints());
    assertEquals(0d, grade.getGrade(p12).mark().getPoints());
    assertEquals(0d, grade.getGrade(c1).mark().getPoints());
    assertEquals(0d, grade.getGrade(p1).mark().getPoints());
    assertEquals(1d, grade.getGrade(p21).mark().getPoints());
    assertEquals(0d, grade.getGrade(p22).mark().getPoints());
    assertEquals(1d, grade.getGrade(c2).mark().getPoints(), 1e-6d);
    assertEquals(1d, grade.getGrade(p2).mark().getPoints(), 1e-6d);
    assertEquals(0d, grade.mark().getPoints());
  }

  @Test
  void testParametricCascadeDraft() throws Exception {
    final GradeAggregator c1A = GradeAggregator.parametric(c11, c12, GradeAggregator.TRIVIAL);
    checkNotNull(WeightingGradeAggregator.ABSOLUTE_WEIGHTING);
    checkNotNull(GradeAggregator.ABSOLUTE);
    final GradeAggregator c2A = GradeAggregator.ABSOLUTE;
    checkNotNull(c2A);
    final GradeAggregator aggregator = GradeAggregator.parametric(c1, c2, c1A, c2A);
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);
    assertEquals(1d, grade.getGrade(p11).mark().getPoints());
    assertEquals(0d, grade.getGrade(p12).mark().getPoints());
    assertEquals(0d, grade.getGrade(c1).mark().getPoints());
    assertEquals(0d, grade.getGrade(p1).mark().getPoints());
    assertEquals(1d, grade.getGrade(p21).mark().getPoints());
    assertEquals(0d, grade.getGrade(p22).mark().getPoints());
    assertEquals(1d, grade.getGrade(c2).mark().getPoints(), 1e-6d);
    assertEquals(1d, grade.getGrade(p2).mark().getPoints(), 1e-6d);
    assertEquals(0d, grade.mark().getPoints());
  }

  @Test
  void testParametricAndAbsoluteHalf() throws Exception {
    final GradeAggregator c1A = GradeAggregator.parametric(c11, c12, GradeAggregator.TRIVIAL);
    final GradeAggregator c2A = GradeAggregator.ABSOLUTE;
    final GradeAggregator aggregator = GradeAggregator.parametric(c1, c2, ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22Half();
    final Grade grade = Grade.given(aggregator, marks);
    assertEquals(1d, grade.getGrade(p11).mark().getPoints());
    assertEquals(0.5d, grade.getGrade(p12).mark().getPoints());
    assertEquals(0.5d, grade.getGrade(c1).mark().getPoints());
    assertEquals(0.5d, grade.getGrade(p1).mark().getPoints());
    assertEquals(1d, grade.getGrade(p21).mark().getPoints());
    assertEquals(0.5d, grade.getGrade(p22).mark().getPoints());
    assertEquals(1d, grade.getGrade(c2).mark().getPoints(), 1e-6d);
    assertEquals(1d, grade.getGrade(p2).mark().getPoints(), 1e-6d);
    assertEquals(0.5d, grade.mark().getPoints());
  }

  @Test
  void testTransformMaxAndStaticToCriteriaWeighter() throws Exception {
    final GradeAggregator c1A = GradeAggregator.MAX;
    final GradeAggregator c2A = GradeAggregator.staticAggregator(ImmutableMap.of(c21, 1d, c22, 2d),
        ImmutableMap.of());
    final GradeAggregator aggregator = GradeAggregator.max(ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);

    final GradeAggregator expectedAggregator = GradeAggregator.ABSOLUTE;
    final MarksTree expectedMarks = MarksTree.composite(ImmutableMap.of(c1, Mark.one(), c2, Mark.zero()));
    final Grade expected = Grade.given(expectedAggregator, expectedMarks);

    final Grade transformed = Grade.transformToPerCriterionWeighting(grade);
    assertEquals(expected.toMarksTree(), transformed.toMarksTree());
  }

  @Test
  void testTransformStaticAndMaxToCriteriaWeighter() throws Exception {
    final GradeAggregator c1A = GradeAggregator.max(ImmutableMap.of(c3, GradeAggregator.MAX));
    final GradeAggregator c2A = GradeAggregator.staticAggregator(ImmutableMap.of(c21, 1d, c22, 2d),
        ImmutableMap.of());
    final GradeAggregator aggregator = GradeAggregator.staticAggregator(ImmutableMap.of(c1, 5d, c2, 2d),
        ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
    final Grade grade = Grade.given(aggregator, marks);

    /* The MAX at c3 gets reduced to its default sub, which is TRIVIAL. */
    final GradeAggregator expectedc1A = GradeAggregator.absolute(ImmutableMap.of(c3, GradeAggregator.TRIVIAL),
        GradeAggregator.TRIVIAL);
    final GradeAggregator expectedAggregator = GradeAggregator.staticAggregator(ImmutableMap.of(c1, 5d, c2, 2d),
        ImmutableMap.of(c1, expectedc1A, c2, c2A));
    final MarksTree expectedMarksC1 = MarksTree.composite(ImmutableMap.of(c11, Mark.one(), c12, Mark.zero()));
    final MarksTree expectedMarksC2 = MarksTree.composite(ImmutableMap.of(c21, Mark.one(), c22, Mark.zero()));
    final MarksTree expectedMarks = MarksTree.composite(ImmutableMap.of(c1, expectedMarksC1, c2, expectedMarksC2));
    final Grade expected = Grade.given(expectedAggregator, expectedMarks);

    final Grade transformed = Grade.transformToPerCriterionWeighting(grade);
    assertEquals(expected.toMarksTree(), transformed.toMarksTree());
    assertEquals(expected.toAggregator(), transformed.toAggregator());
  }

  @Test
  void testTransformParametricAndAbsoluteHalf() throws Exception {
    final GradeAggregator c1A = GradeAggregator.parametric(c11, c12, GradeAggregator.TRIVIAL);
    final GradeAggregator c2A = GradeAggregator.ABSOLUTE;
    final GradeAggregator aggregator = GradeAggregator.parametric(c1, c2, ImmutableMap.of(c1, c1A, c2, c2A));
    final MarksTree m1 = MarksTree.composite(ImmutableMap.of(c11, Mark.one(), c12, Mark.given(0.5d, "")));
    final MarksTree m2 = MarksTree
        .composite(ImmutableMap.of(c21, Mark.given(0.5d, ""), c22, Mark.given(0.25d, "")));
    final MarksTree marks = MarksTree.composite(ImmutableMap.of(c1, m1, c2, m2));
    final Grade grade = Grade.given(aggregator, marks);

    final WeightingGradeAggregator expectedc1A = GradeAggregator.ABSOLUTE;
    final WeightingGradeAggregator expectedAggregator = WeightingGradeAggregator.weightingAbsolute(
        ImmutableMap.of(c1, expectedc1A, c2, GradeAggregator.ABSOLUTE), GradeAggregator.TRIVIAL);
    final MarksTree expectedMarksC1 = MarksTree.composite(
        ImmutableMap.of(c11, Mark.one(), ParametricWeighter.toPenalized(c11), Mark.given(-0.5d, "")));
    final MarksTree expectedMarksPenalizedC1 = Mark.given(-(1 - (1d / 2d + 1d / 2d * 1d / 2d)) * 1d / 2d, "");
    final MarksTree expectedMarks = MarksTree.composite(
        ImmutableMap.of(c1, expectedMarksC1, ParametricWeighter.toPenalized(c1), expectedMarksPenalizedC1));
    final Grade expected = Grade.given(expectedAggregator, expectedMarks);

    final Grade transformed = Grade.transformToPerCriterionWeighting(grade);
    assertEquals(expected.toMarksTree(), transformed.toMarksTree());
    assertEquals(expected.toAggregator(), transformed.toAggregator());
  }
}
