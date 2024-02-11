package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;

/**
 * Longer term, could make this a choice function: when some crits are missing from the grade, we
 * aggregate with a reduced list of weights (indicate which ones get dropped using a preference
 * ordering).
 */
public class OwaStructure {
	/**
	 * Must have the same size.
	 *
	 * @param criteria
	 * @param weights sum to one, zeroes allowed
	 */
	public static OwaStructure given(Set<Criterion> criteria, List<Double> weights) {
		return new OwaStructure(criteria, weights);
	}

	private final ImmutableSet<Criterion> criteria;
	private final ImmutableList<Double> weights;

	private OwaStructure(Set<Criterion> criteria, List<Double> weights) {
		this.criteria = ImmutableSet.copyOf(criteria);
		this.weights = ImmutableList.copyOf(weights);
	}

	public ImmutableSet<Criterion> getCriteria() {
		return criteria;
	}

	public ImmutableList<Double> getWeights() {
		return weights;
	}

	public double getWeightForPosition(int positionByLargestMarks) {
		return weights.get(positionByLargestMarks);
	}
}
