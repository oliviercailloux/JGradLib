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
 * the same for every students. May contain no criteria (represent a simple mark
 * structure).
 * <p>
 * explains how the grade is decomposed (where points have been earned or lost;
 * what justifies the final grade)
 * <p>
 * Required with a composite grade to compute the points weights AND/OR OWA
 * AND/OR “absolute” marks
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
			ImmutableSet.of(), ImmutableSet.of(), ImmutableMap.of());

	public static GradeStructure given(Map<Criterion, Double> weights, Set<OwaStructure> owas, Set<Criterion> absolutes,
			Set<Criterion> criteria, Map<Criterion, GradeStructure> subStructures) {
		return new GradeStructure(weights, owas, absolutes, criteria, subStructures);
	}

	private final ImmutableMap<Criterion, Double> weights;
	private final ImmutableSet<OwaStructure> owas;
	private final ImmutableSet<Criterion> absolutes;
	/**
	 * Indicates the ordering; must equal the union of the criteria in weights, owa
	 * and absolutes.
	 */
	private final ImmutableSet<Criterion> criteria;

	/**
	 * not all criteria: some are simple marks
	 */
	private final ImmutableMap<Criterion, GradeStructure> subStructures;

	private GradeStructure(Map<Criterion, Double> weights, Set<OwaStructure> owas, Set<Criterion> absolutes,
			Set<Criterion> criteria, Map<Criterion, GradeStructure> subStructures) {
		this.weights = ImmutableMap.copyOf(weights);
		this.owas = ImmutableSet.copyOf(owas);
		this.absolutes = ImmutableSet.copyOf(absolutes);
		this.criteria = ImmutableSet.copyOf(criteria);
		this.subStructures = ImmutableMap.copyOf(subStructures);

		checkArgument(Sets.intersection(weights.keySet(), absolutes).isEmpty());
		checkArgument(owas.stream().map(OwaStructure::getCriteria)
				.allMatch(c -> Sets.intersection(weights.keySet(), c).isEmpty()));
		checkArgument(
				owas.stream().map(OwaStructure::getCriteria).allMatch(c -> Sets.intersection(absolutes, c).isEmpty()));
		checkArgument(criteria.containsAll(subStructures.keySet()));

		final ImmutableSet.Builder<Criterion> builder = ImmutableSet.builder();
		builder.addAll(weights.keySet());
		builder.addAll(absolutes);
		owas.stream().map(OwaStructure::getCriteria).forEach(builder::addAll);
		checkArgument(criteria.equals(builder.build()));
	}

	public ImmutableSet<Criterion> getOrderedCriteria() {
		return criteria;
	}

	public boolean isAbsolute(Criterion criterion) {
		checkArgument(criteria.contains(criterion));
		return absolutes.contains(criterion);
	}

	public boolean hasWeight(Criterion criterion) {
		checkArgument(criteria.contains(criterion));
		return weights.containsKey(criterion);
	}

	public boolean isInOwa(Criterion criterion) {
		checkArgument(criteria.contains(criterion));
		return owas.stream().flatMap(o -> o.getCriteria().stream()).anyMatch(Predicate.isEqual(criterion));
	}

	public double getWeight(Criterion criterion) {
		checkArgument(weights.containsKey(criterion));
		return weights.get(criterion);
	}

	public OwaStructure getOwaStructure(Criterion criterion) {
		checkArgument(criteria.contains(criterion));
		return owas.stream().filter(o -> o.getCriteria().contains(criterion)).collect(MoreCollectors.onlyElement());
	}

	public ImmutableSet<Criterion> getStructuredCriteria() {
		return subStructures.keySet();
	}

	public GradeStructure getStructure(Criterion criterion) {
		checkArgument(criteria.contains(criterion));
		return subStructures.getOrDefault(criterion, GradeStructure.EMPTY);
	}

}
