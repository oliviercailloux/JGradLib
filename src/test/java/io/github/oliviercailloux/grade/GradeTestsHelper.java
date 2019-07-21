package io.github.oliviercailloux.grade;

import java.util.Map.Entry;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.DoubleMath;

public class GradeTestsHelper {

	public static ImmutableMap<Criterion, Double> asWeights(ImmutableMap<String, Double> weights) {
		return weights.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> Criterion.given(e.getKey()), Entry::getValue));
	}

	public static ImmutableMap<Criterion, Mark> asMarks(ImmutableMap<String, Double> marks) {
		return marks.entrySet().stream().collect(
				ImmutableMap.toImmutableMap((e) -> Criterion.given(e.getKey()), (e) -> Mark.given(e.getValue(), "")));
	}

	public static ImmutableMap<Criterion, IGrade> asGrades(ImmutableMap<String, IGrade> grades) {
		return grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> Criterion.given(e.getKey()), Entry::getValue));
	}

	public static WeightingGrade getSingletonWeightingGrade() {
		final String criterionName = "criterion";
		final Criterion criterion = Criterion.given(criterionName);
		final ImmutableMap<Criterion, IGrade> subMarks = ImmutableMap.of(criterion, Mark.given(1d, ""));
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion, 1d);
		final WeightingGrade grade = WeightingGrade.from(subMarks, weights);
		return grade;
	}

	public static WeightingGrade getComplexGrade() {
		final WeightingGrade subGrade1 = getSingletonWeightingGrade();
		final Mark subGrade2 = Mark.given(0.3d, "comment");
		final Criterion criterion1 = Criterion.given("C1");
		final Criterion criterion2 = Criterion.given("C2");
		final ImmutableMap<Criterion, Double> weights = ImmutableMap.of(criterion1, 2d, criterion2, 3d);
		final ImmutableMap<Criterion, IGrade> subGrades = ImmutableMap.of(criterion1, subGrade1, criterion2, subGrade2);
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
		final WeightingGrade g1 = WeightingGrade.from(
				asMarks(ImmutableMap.of("C1.1", 0.3d, "C1.2", 0.2d, "C1.3", 1.0d)),
				asWeights(ImmutableMap.of("C1.1", 0.2d, "C1.2", 0.4d, "C1.3", 0.4d)));
		final WeightingGrade g2 = WeightingGrade.from(asMarks(ImmutableMap.of("C2.1", 0d, "C2.2", 0.8d)),
				asWeights(ImmutableMap.of("C2.1", 0.5d, "C2.2", 0.5d)));
		final WeightingGrade main = WeightingGrade.from(asGrades(ImmutableMap.of("C1", g1, "C2", g2)),
				asWeights(ImmutableMap.of("C1", 0.1d, "C2", 0.9d)));
		Verify.verify(DoubleMath.fuzzyEquals(main.getPoints(), 0.414d, 1e-5d));
		return main;
	}

	public static WeightingGrade getGrade3Plus2Alt() {
		final WeightingGrade g1 = WeightingGrade.from(
				asMarks(ImmutableMap.of("C1.1", 0.8d, "C1.2", 0.9d, "C1.3", 1.0d)),
				asWeights(ImmutableMap.of("C1.1", 0.2d, "C1.2", 0.4d, "C1.3", 0.4d)));
		final WeightingGrade g2 = WeightingGrade.from(asMarks(ImmutableMap.of("C2.1", 0.7d, "C2.2", 0.8d)),
				asWeights(ImmutableMap.of("C2.1", 0.5d, "C2.2", 0.5d)));
		final WeightingGrade main = WeightingGrade.from(asGrades(ImmutableMap.of("C1", g1, "C2", g2)),
				asWeights(ImmutableMap.of("C1", 0.1d, "C2", 0.9d)));
		return main;
	}

}
