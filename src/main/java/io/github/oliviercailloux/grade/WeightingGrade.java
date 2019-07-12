package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

/**
 * {weights: Map<Criterion, Double>} (non empty, all non null), this
 * implementation has only the (normalized) weights and the marks, and generates
 * the comment (a string repr of the weights and saying that it is a weighted
 * average) and the points.
 *
 * @author Olivier Cailloux
 *
 */
public class WeightingGrade implements IGrade {
	public static WeightingGrade from(Map<Criterion, IGrade> grades, Map<Criterion, Double> weights) {
		return new WeightingGrade(grades, weights);
	}

	/**
	 * The positive ones sum to one.
	 */
	private final ImmutableMap<Criterion, Double> weights;

	/**
	 * Not empty. This key set equals the key set of the weights.
	 */
	private final ImmutableMap<Criterion, IGrade> subGrades;

	private WeightingGrade(Map<Criterion, IGrade> subGrades, Map<Criterion, Double> weights) {
		checkArgument(weights.values().stream().allMatch((d) -> d != 0d && Double.isFinite(d)));
		final double sumPosWeights = weights.values().stream().filter((d) -> d > 0d)
				.collect(Collectors.summingDouble((d) -> d));
		this.weights = weights.entrySet().stream().collect(ImmutableMap.toImmutableMap((e) -> e.getKey(),
				(e) -> e.getValue() > 0d ? e.getValue() / sumPosWeights : e.getValue()));
		this.subGrades = ImmutableMap.copyOf(checkNotNull(subGrades));
		checkArgument(weights.keySet().equals(subGrades.keySet()));
		checkArgument(!weights.isEmpty());
	}

	@Override
	public double getPoints() {
		return subGrades.keySet().stream()
				.collect(Collectors.averagingDouble((c) -> subGrades.get(c).getPoints() * weights.get(c)));
	}

	@Override
	public String getComment() {
		return String.format("Weighted average using weights %s.", weights);
	}

	@Override
	public ImmutableMap<Criterion, IGrade> getSubGrades() {
		return subGrades;
	}

	/**
	 * @return the weights, such that the positive weights sum to one, with no zero
	 *         weights, and not empty.
	 */
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

}
