package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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

	public static WeightingGrade from(Map<Criterion, ? extends IGrade> grades, Map<Criterion, Double> weights) {
		return new WeightingGrade(grades, weights);
	}

	@JsonbCreator
	public static WeightingGrade from(@JsonbProperty("subGrades") Set<CriterionGradeWeight> grades) {
		final Object gr = grades.iterator().next();
		LOGGER.info("Grade: {}, type: {}.", gr, gr.getClass());
		final ImmutableMap<Criterion, IGrade> gradesByCriterion = grades.stream()
				.collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getGrade()));
		final ImmutableMap<Criterion, Double> weights = grades.stream()
				.collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getWeight()));
		return new WeightingGrade(gradesByCriterion, weights);
	}

	private static final double MAX_MARK = 1d;

	/**
	 * Not empty. This key set equals the key set of the weights.
	 */
	private final ImmutableMap<Criterion, IGrade> subGrades;

	/**
	 * The positive ones sum to one.
	 */
	private final ImmutableMap<Criterion, Double> weights;

	private WeightingGrade(Map<Criterion, ? extends IGrade> subGrades, Map<Criterion, Double> weights) {
		checkArgument(weights.values().stream().allMatch((d) -> d != 0d && Double.isFinite(d)));
		checkArgument(weights.keySet().equals(subGrades.keySet()));
		checkArgument(weights.values().stream().anyMatch((d) -> d > 0d));
		checkArgument(subGrades.values().stream().allMatch((g) -> 0d <= g.getPoints() && g.getPoints() <= 1d));
		final double sumPosWeights = weights.values().stream().filter((d) -> d > 0d)
				.collect(Collectors.summingDouble((d) -> d));
		this.weights = weights.entrySet().stream().collect(ImmutableMap.toImmutableMap((e) -> e.getKey(),
				(e) -> e.getValue() > 0d ? e.getValue() / sumPosWeights : e.getValue()));
		this.subGrades = ImmutableMap.copyOf(subGrades);
	}

	@Override
	public double getPoints() {
		final double positivePoints = weights.entrySet().stream().filter((e) -> e.getValue() > 0d).map(Entry::getKey)
				.collect(Collectors.summingDouble((c) -> subGrades.get(c).getPoints() * weights.get(c)));
		final double negativePoints = weights.entrySet().stream().filter((e) -> e.getValue() < 0d).map(Entry::getKey)
				.collect(Collectors.summingDouble((c) -> (MAX_MARK - subGrades.get(c).getPoints()) * weights.get(c)));
		final double totalPoints = Math.max(0d, positivePoints - negativePoints);
		Verify.verify(0d <= totalPoints && totalPoints <= 1d);
		return totalPoints;
	}

	@Override
	public String getComment() {
		return String.format("Weighted average using weights %s with penalties using weights %s.",
				weights.entrySet().stream().filter((e) -> e.getValue() > 0d)
						.collect(ImmutableMap.toImmutableMap((e) -> e.getKey(), (e) -> e.getValue())),
				weights.entrySet().stream().filter((e) -> e.getValue() < 0d)
						.collect(ImmutableMap.toImmutableMap((e) -> e.getKey(), (e) -> e.getValue())));
	}

	@JsonbTransient
	@Override
	public ImmutableMap<Criterion, IGrade> getSubGrades() {
		return subGrades;
	}

	@JsonbProperty("subGrades")
	ImmutableSet<CriterionGradeWeight> getSubGradesAsSet() {
		return subGrades.keySet().stream().map((c) -> CriterionGradeWeight.from(c, subGrades.get(c), weights.get(c)))
				.collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @return the weights, such that the positive weights sum to one, with no zero
	 *         weights, and not empty.
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
