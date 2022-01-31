package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * the same for every students. May contain no criteria
 * <p>
 * explains how the grade is decomposed (where points have been earned or lost;
 * what justifies the final grade)
 * <p>
 * Required with a composite grade to compute the points
 * <p>
 * Has a “default” mode: points associated to unknown criteria are aggregated
 * using the default mode, which, currently, is necessarily MAX (thus OWA 1, 0,
 * 0, …). Could be generalized to parameterized aggregation: using average;
 * using absolutes; using zero weights; using MIN…
 * <p>
 * weights AND/OR OWA AND/OR “absolute” marks
 * <p>
 * Negative may be interpreted as a variable proportion of what comes before
 * (such as a linear lateness penality) or as an absolute penalty (such as −1
 * for terrible spelling) or as a proportional penalty that is not linear; in
 * any case, seems better to indicate the percentage or other explanation as a
 * comment as it will depend.
 * <p>
 * Example
 * <ul>
 * <li>crits => c1 w1, c2 w2, c3 REST, c4 NEG, c5 OWA W1, c6 OWA W2, c7 OWAbis
 * WW1, c8 OWAbis WW2, c9 OWAbis WWREST.</li>
 * <li>weights: list: {c1, c2, c3}</li>
 * <li>OWA: set: {c5, c6}</li>
 * <li>OWAbis: {c7, c8, c9}</li>
 * <li>NEG: set {c4}</li>
 * </ul>
 */
public class GradeStructure {
	public static final GradeStructure EMPTY = GradeStructure.given(ImmutableMap.of(), ImmutableSet.of(),
			ImmutableSet.of(), ImmutableMap.of());

	public static GradeStructure given(Map<Criterion, Double> weights, Set<OwaStructure> owas, Set<Criterion> absolutes,
			Map<Criterion, GradeStructure> subStructures) {
		return new GradeStructure(weights, owas, absolutes, subStructures);
	}

	private final ImmutableMap<Criterion, Double> weights;
	private final ImmutableSet<OwaStructure> owas;
	private final ImmutableSet<Criterion> absolutes;

	/**
	 * All the above
	 */
	private final ImmutableSet<Criterion> knownCriteria;
	/**
	 * not all criteria: some are simple marks
	 */
	private final ImmutableMap<Criterion, GradeStructure> subStructures;

	private GradeStructure(Map<Criterion, Double> weights, Set<OwaStructure> owas, Set<Criterion> absolutes,
			Map<Criterion, GradeStructure> subStructures) {
		this.weights = ImmutableMap.copyOf(weights);
		this.owas = ImmutableSet.copyOf(owas);
		this.absolutes = ImmutableSet.copyOf(absolutes);
		this.subStructures = ImmutableMap.copyOf(subStructures);

		checkArgument(Sets.intersection(weights.keySet(), absolutes).isEmpty());
		checkArgument(owas.stream().map(OwaStructure::getCriteria)
				.allMatch(c -> Sets.intersection(weights.keySet(), c).isEmpty()));
		checkArgument(
				owas.stream().map(OwaStructure::getCriteria).allMatch(c -> Sets.intersection(absolutes, c).isEmpty()));

		final ImmutableSet.Builder<Criterion> builder = ImmutableSet.builder();
		builder.addAll(weights.keySet());
		builder.addAll(absolutes);
		owas.stream().map(OwaStructure::getCriteria).forEach(builder::addAll);
		this.knownCriteria = builder.build();
	}

	public ImmutableSet<Criterion> getKnownCriteria() {
		return knownCriteria;
	}

	public boolean isAbsolute(Criterion criterion) {
		return absolutes.contains(criterion);
	}

	public boolean hasWeight(Criterion criterion) {
		return weights.containsKey(criterion);
	}

	public boolean isInOwa(Criterion criterion) {
		return owas.stream().flatMap(o -> o.getCriteria().stream()).anyMatch(Predicate.isEqual(criterion));
	}

	public boolean isKnown(Criterion criterion) {
		return knownCriteria.contains(criterion);
	}

	public double getWeight(Criterion criterion) {
		checkArgument(weights.containsKey(criterion));
		return weights.get(criterion);
	}

	public OwaStructure getOwaStructure(Criterion criterion) {
		return owas.stream().filter(o -> o.getCriteria().contains(criterion)).collect(MoreCollectors.onlyElement());
	}

	public double getDefaultOwaWeightForPosition(int positionByLargestMarks) {
		if (positionByLargestMarks == 0) {
			return 1d;
		}
		return 0d;
	}

	public ImmutableSet<Criterion> getStructuredCriteria() {
		return subStructures.keySet();
	}

	public GradeStructure getStructure(Criterion criterion) {
		return subStructures.getOrDefault(criterion, GradeStructure.EMPTY);
	}

}
