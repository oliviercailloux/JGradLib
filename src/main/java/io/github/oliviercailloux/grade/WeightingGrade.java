package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;
import javax.json.bind.annotation.JsonbVisibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * containing positive points only and strictly positive and strictly negative
 * weights (one per sub-grade). May order the grades (we may not want all
 * positive then all negative but interleaving!). The positive weights are
 * normalized internally. The negative weights must be in [−1, 0). A negative
 * weight represents the prop. of the total points that can be lost on the
 * corresponding criterion. Example: weight is −2/20, grade is 1 (means no
 * penalty) or 0.5 (means −1/20) or 0 (means −2/20). A sub grade in the map may
 * be an AdditiveGrade (even if it’s a penalty).
 *
 * {weights: Map<CriterionAndPoints, Double>} (non empty, all non null), this
 * implementation has only the (normalized) weights and the marks, and generates
 * the comment (a string repr of the weights and saying that it is a weighted
 * average with penalty) and the points.
 *
 * As the penalty has an absolute meaning, it is necessary that this object
 * knows the best possible marks. As a convention, it is considered to be one
 * for all sub-grades.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "points", "comment", "subGrades" })
@JsonbVisibility(MethodVisibility.class)
public class WeightingGrade implements IGrade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(WeightingGrade.class);

	/**
	 * @param grades  its key set iteration order is used to determine the order of
	 *                the sub-grades.
	 * @param weights must have the same keys as the grades (but the iteration order
	 *                is not used).
	 */
	public static WeightingGrade from(Map<Criterion, ? extends IGrade> grades, Map<Criterion, Double> weights) {
		return new WeightingGrade(grades, weights);
	}

	/**
	 * @param grades its iteration order is used to determine the order of the
	 *               sub-grades.
	 */
	@JsonbCreator
	public static WeightingGrade fromList(@JsonbProperty("subGrades") List<CriterionGradeWeight> grades) {
		/**
		 * The list type (rather than set) is required for json to deserialize in the
		 * right order.
		 */
		return from(grades);
	}

	/**
	 * @param grades its iteration order is used to determine the order of the
	 *               sub-grades.
	 */
	public static WeightingGrade from(Collection<CriterionGradeWeight> grades) {
		final Object gr = grades.iterator().next();
		LOGGER.debug("Grade: {}, type: {}.", gr, gr.getClass());
		final ImmutableMap<Criterion, IGrade> gradesByCriterion = grades.stream()
				.collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getGrade()));
		final ImmutableMap<Criterion, Double> weights = grades.stream()
				.collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getWeight()));
		return new WeightingGrade(gradesByCriterion, weights);
	}

	public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2) {
		return WeightingGrade.from(ImmutableMap.of(c1, g1, c2, g2), ImmutableMap.of(c1, 0.5d, c2, 0.5d));
	}

	public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2, Criterion c3,
			IGrade g3) {
		return WeightingGrade.from(ImmutableMap.of(c1, g1, c2, g2, c3, g3),
				ImmutableMap.of(c1, 1d / 3d, c2, 1d / 3d, c3, 1d / 3d));
	}

	private static final double MAX_MARK = 1d;

	/**
	 * Not empty. This key set equals the key set of the weights.
	 */
	private final ImmutableMap<Criterion, IGrade> subGrades;

	/**
	 * The positive ones sum to one. No zero values.
	 */
	private final ImmutableMap<Criterion, Double> weights;

	private WeightingGrade(Map<Criterion, ? extends IGrade> subGrades, Map<Criterion, Double> weights) {
		checkArgument(weights.values().stream().allMatch((d) -> d != 0d && Double.isFinite(d)));
		checkArgument(weights.values().stream().anyMatch((d) -> d > 0d));
		checkArgument(subGrades.values().stream().allMatch((g) -> 0d <= g.getPoints() && g.getPoints() <= 1d));
		checkArgument(subGrades.keySet().equals(weights.keySet()),
				String.format("Sub grades have keys: %s, weights have keys: %s", subGrades.keySet(), weights.keySet()));
		final double sumPosWeights = weights.values().stream().filter((d) -> d > 0d)
				.collect(Collectors.summingDouble((d) -> d));
		/**
		 * I iterate over the sub grades key set in order to guarantee iteration order
		 * of the weights reflects the order of the sub-grades.
		 */
		this.weights = subGrades.keySet().stream().collect(ImmutableMap.toImmutableMap((c) -> c,
				(c) -> weights.get(c) > 0d ? weights.get(c) / sumPosWeights : weights.get(c)));
		this.subGrades = ImmutableMap.copyOf(subGrades);
	}

	@Override
	public double getPoints() {
		final double positivePoints = weights.entrySet().stream().filter((e) -> e.getValue() > 0d).map(Entry::getKey)
				.collect(Collectors.summingDouble((c) -> subGrades.get(c).getPoints() * weights.get(c)));
		final double negativePoints = weights.entrySet().stream().filter((e) -> e.getValue() < 0d).map(Entry::getKey)
				.collect(Collectors.summingDouble((c) -> (MAX_MARK - subGrades.get(c).getPoints()) * weights.get(c)));
		Verify.verify(negativePoints <= 0d);
		final double totalPoints = Math.max(0d, positivePoints + negativePoints);
		Verify.verify(0d <= totalPoints && totalPoints <= 1d);
		return totalPoints;
	}

	@Override
	public String getComment() {
		return String.format("Weighted average");
	}

	@JsonbTransient
	@Override
	public ImmutableMap<Criterion, IGrade> getSubGrades() {
		return subGrades;
	}

	/**
	 * @return iterates in the order of the sub-grades.
	 */
	@JsonbProperty("subGrades")
	public ImmutableSet<CriterionGradeWeight> getSubGradesAsSet() {
		return subGrades.keySet().stream().map((c) -> CriterionGradeWeight.from(c, subGrades.get(c), weights.get(c)))
				.collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @return the weights, such that the positive weights sum to one, with no zero
	 *         weights, and not empty. Iterates in the order of the sub-grades.
	 */
	@JsonbTransient
	public ImmutableMap<Criterion, Double> getWeights() {
		return weights;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof IGrade)) {
			return false;
		}
		IGrade g2 = (IGrade) o2;
		return getPoints() == g2.getPoints() && getComment().equals(g2.getComment())
				&& getSubGrades().equals(g2.getSubGrades());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getPoints(), getComment(), getSubGrades());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("points", getPoints()).add("comment", getComment())
				.add("subGrades", getSubGradesAsSet()).toString();
	}

}
