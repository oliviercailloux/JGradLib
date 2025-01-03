package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c11;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c12;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c13;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c21;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c22;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.old.Mark;
import java.util.Map.Entry;

public class GradeTestsHelper {

  public static ImmutableMap<Criterion, Double> asWeights(ImmutableMap<String, Double> weights) {
    return weights.entrySet().stream()
        .collect(ImmutableMap.toImmutableMap((e) -> Criterion.given(e.getKey()), Entry::getValue));
  }

  public static ImmutableMap<Criterion, Mark> asMarks(ImmutableMap<String, Double> marks) {
    return marks.entrySet().stream().collect(ImmutableMap
        .toImmutableMap((e) -> Criterion.given(e.getKey()), (e) -> Mark.given(e.getValue(), "")));
  }

  public static ImmutableMap<Criterion, IGrade> asGrades(ImmutableMap<String, IGrade> grades) {
    return grades.entrySet().stream()
        .collect(ImmutableMap.toImmutableMap((e) -> Criterion.given(e.getKey()), Entry::getValue));
  }

  public static WeightingGrade getSingletonWeightingGrade() {
    final Criterion criterion = Criterion.given("criterion");
    final ImmutableMap<Criterion, IGrade> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
    final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
    final WeightingGrade grade = WeightingGrade.from(subMarks, weights, "A comment");
    return grade;
  }

  public static WeightingGrade getComplexGrade() {
    final WeightingGrade subGrade1 = getSingletonWeightingGrade();
    final Mark subGrade2 = Mark.given(0.3d, "comment");
    final Criterion criterion1 = Criterion.given("C1");
    final Criterion criterion2 = Criterion.given("C2");
    final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion1, 2d, criterion2, 3d);
    final ImmutableMap<Criterion, IGrade> subGrades =
        ImmutableMap.of(criterion1, subGrade1, criterion2, subGrade2);
    final WeightingGrade composite = WeightingGrade.from(subGrades, weights, "Complex grade");
    return composite;
  }

  public static WeightingGrade getComplexGradeWithPenalty() {
    final WeightingGrade subGrade1 = getSingletonWeightingGrade();
    final Mark subGrade2 = Mark.given(0.3d, "comment");
    final Mark subGradeTrap1 = Mark.given(0.3d, "not great");
    final Mark subGradeTrap2 = Mark.given(1d, "well done buddy");
    final Criterion criterion1 = Criterion.given("C1");
    final Criterion criterion2 = Criterion.given("C2");
    final Criterion trap1 = Criterion.given("Trap 1");
    final Criterion trap2 = Criterion.given("Trap 2");
    /** On trap1, lost 70 % × 75 % = 0.525 points */
    final ImmutableMap<Criterion, Double> weights =
        ImmutableMap.of(criterion1, 2d, criterion2, 3d, trap1, -0.75d, trap2, -1d);
    final ImmutableMap<Criterion, IGrade> subGrades = ImmutableMap.of(criterion1, subGrade1,
        criterion2, subGrade2, trap1, subGradeTrap1, trap2, subGradeTrap2);
    final WeightingGrade composite = WeightingGrade.from(subGrades, weights);
    return composite;
  }

  public static WeightingGrade getMinGrade() {
    final Criterion cSubMin1 = Criterion.given("SubMin1");
    final Criterion cSubMin2 = Criterion.given("SubMin2");
    final Mark subMin1 = Mark.given(0.3d, "sub min 1");
    final Mark subMin2 = Mark.given(0.4d, "sub min 2");
    return WeightingGrade.from(ImmutableMap.of(cSubMin1, subMin1, cSubMin2, subMin2),
        ImmutableMap.of(cSubMin1, 1d, cSubMin2, 0d));
  }

  public static WeightingGrade getEclecticWeightedGrade() {
    final WeightingGrade subGrade1 = getSingletonWeightingGrade();

    final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(c1, 0.9d, c2, 0.1d);
    final ImmutableMap<Criterion, IGrade> subGrades =
        ImmutableMap.of(c1, subGrade1, c2, getMinGrade());
    final WeightingGrade composite = WeightingGrade.from(subGrades, weights);
    return composite;
  }

  /**
   * <ul>
   * <li>C1: 0.54 [w=0.1]</li>
   * <ul>
   * <li>C1.1: 0.3 [w=1/5]</li>
   * <li>C1.2: 0.2 [w=2/5]</li>
   * <li>C1.3: 1.0 [w=2/5]</li>
   * </ul>
   * <li>C2: 0.4 [w=0.9]</li>
   * <ul>
   * <li>C2.1: 0 [w=0.5]</li>
   * <li>C2.2: 0.8 [w=0.5]</li>
   * </ul>
   * <li>points: 0.414.</li>
   * </ul>
   *
   */
  public static WeightingGrade getGrade3Plus2() {
    final WeightingGrade g1 =
        WeightingGrade.from(asMarks(ImmutableMap.of("C1.1", 0.3d, "C1.2", 0.2d, "C1.3", 1.0d)),
            asWeights(ImmutableMap.of("C1.1", 0.2d, "C1.2", 0.4d, "C1.3", 0.4d)));
    final WeightingGrade g2 =
        WeightingGrade.from(asMarks(ImmutableMap.of("C2.1", 0d, "C2.2", 0.8d)),
            asWeights(ImmutableMap.of("C2.1", 0.5d, "C2.2", 0.5d)));
    final WeightingGrade main = WeightingGrade.from(asGrades(ImmutableMap.of("C1", g1, "C2", g2)),
        asWeights(ImmutableMap.of("C1", 0.1d, "C2", 0.9d)));
    Verify.verify(DoubleMath.fuzzyEquals(main.getPoints(), 0.414d, 1e-5d));
    return main;
  }

  /**
   * <ul>
   * <li>C1: 0.54 [w=0.1] ⇒ 1.08 / 2</li>
   * <ul>
   * <li>C1.1: 0.3 [w=1/5] ⇒ 0.12 / 0.4</li>
   * <li>C1.2: 0.2 [w=2/5] ⇒ 0.16 / 0.8</li>
   * <li>C1.3: 1.0 [w=2/5] ⇒ 0.8 / 0.8</li>
   * </ul>
   * <li>C2: 0.4 [w=0.9] ⇒ 7.2 / 18</li>
   * <ul>
   * <li>C2.1: 0 [w=0.5] ⇒ 0 / 9</li>
   * <li>C2.2: 0.8 [w=0.5] ⇒ 7.2 / 9</li>
   * </ul>
   * <li>points: 0.414 ⇒ 8.28 / 20</li>
   * </ul>
   *
   */
  public static Grade get3Plus2() {
    final WeightingGradeAggregator a1 = WeightingGradeAggregator
        .weightingStaticAggregator(ImmutableMap.of(c11, 1d, c12, 2d, c13, 2d), ImmutableMap.of());
    final WeightingGradeAggregator a2 = WeightingGradeAggregator
        .weightingStaticAggregator(ImmutableMap.of(c21, 1d, c22, 1d), ImmutableMap.of());
    final WeightingGradeAggregator a = WeightingGradeAggregator.weightingStaticAggregator(
        ImmutableMap.of(c1, 1d, c2, 9d), ImmutableMap.of(c1, a1, c2, a2));
    final Grade grade = Grade.given(a, MarksTreeTestsHelper.get3Plus2());
    verify(DoubleMath.fuzzyEquals(grade.mark().getPoints(), 0.414d, 1e-6d),
        "" + grade.mark().getPoints());
    return grade;
  }

  public static WeightingGrade getGrade3Plus2Alt() {
    final WeightingGrade g1 =
        WeightingGrade.from(asMarks(ImmutableMap.of("C1.1", 0.8d, "C1.2", 0.9d, "C1.3", 1.0d)),
            asWeights(ImmutableMap.of("C1.1", 0.2d, "C1.2", 0.4d, "C1.3", 0.4d)));
    final WeightingGrade g2 =
        WeightingGrade.from(asMarks(ImmutableMap.of("C2.1", 0.7d, "C2.2", 0.8d)),
            asWeights(ImmutableMap.of("C2.1", 0.5d, "C2.2", 0.5d)));
    final WeightingGrade main = WeightingGrade.from(asGrades(ImmutableMap.of("C1", g1, "C2", g2)),
        asWeights(ImmutableMap.of("C1", 0.1d, "C2", 0.9d)));
    return main;
  }
}
