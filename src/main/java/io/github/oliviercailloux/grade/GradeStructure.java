package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * the same for every students.
 * <p>
 * explains how the grade is decomposed (where points have been earned or lost;
 * what justifies the final grade)
 * <p>
 * Required with a composite grade to compute the points
 * <p>
 * Has a “default” mode: points associated to unknown criteria are aggregated
 * using the default mode: using average; using absolutes; using zero weights;
 * using MIN…
 * <p>
 * Negative may be interpreted as a variable proportion of what comes before
 * (such as a linear lateness penality) or as an absolute penalty (such as −1
 * for terrible spelling) or as a proportional penalty that is not linear; in
 * any case, seems better to indicate the percentage or other explanation as a
 * comment as it will depend.
 */
public interface GradeStructure {

	public static enum DefaultAggregation {
		AVERAGE, MAX, MIN, ABSOLUTE
	}

	public static GradeStructure givenWeights(Map<Criterion, Double> weights,
			Map<Criterion, GradeStructure> subStructures) {
		return new FixedWeightsGradeStructure(weights, subStructures);
	}

	public static GradeStructure maxWithGivenAbsolutes(Set<Criterion> absolutes,
			Map<Criterion, GradeStructure> subStructures) {
		return new MaxGradeStructure(absolutes, Optional.empty(), subStructures);
	}

	public static GradeStructure maxWithDefault(Set<Criterion> absolutes, Optional<GradeStructure> defaultSubStructure,
			Map<Criterion, GradeStructure> subStructures) {
		return new MaxGradeStructure(absolutes, defaultSubStructure, subStructures);
	}

	/**
	 * @return absolute if {@link #getFixedWeights()} is not empty
	 */
	public DefaultAggregation getDefaultAggregation();

	public Optional<GradeStructure> getDefaultSubStructure();

	public ImmutableSet<Criterion> getAbsolutes();

	public boolean isAbsolute(Criterion criterion);

	/**
	 * @return if default != abs, an empty map.
	 */
	public ImmutableMap<Criterion, Double> getFixedWeights();

	/**
	 * NB a method double getWeight(Criterion criterion, Set<SubMark> subMarks) does
	 * not work here: if two crit have same mark (say, rank 1 and 2), then when
	 * returning weights individually we’d need to average the corresponding
	 * weights; but we prefer having one weight 1d and one 0d than two 0.5d when
	 * showing the results to the student.
	 *
	 * @param subMarks may not contain absolute criteria (weight counts as one when
	 *                 multiplying to get the weighted sum, but counts as zero when
	 *                 summing to get the sum of weights to use in normalization,
	 *                 misleading)
	 * @return the weights
	 */
	public ImmutableMap<SubMark, Double> getWeights(Set<SubMark> subMarks);

	/**
	 * Then weighted sum divided by sum of weights not counting absolute ones; plus
	 * the absolute ones
	 *
	 * @param subMarks must contain at least one strictly positively weighted
	 *                 criterion or at least one absolute criterion.
	 */
	public Mark getMark(Set<SubMark> subMarks);

	public ImmutableMap<Criterion, GradeStructure> getSubStructures();

	public GradeStructure getStructure(Criterion criterion);

	/**
	 * @return true iff the given object is a grade structure with the same default
	 *         aggregation, absolutes, fixed weights and sub structures.
	 */
	@Override
	boolean equals(Object obj);

}
