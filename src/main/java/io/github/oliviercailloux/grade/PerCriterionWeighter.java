package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import java.util.Set;

/**
 * A criteria weighter able to decide whether a given criterion is accepted (not
 * depending on the rest of the set of criteria); and to attribute a fixed
 * weight to each criterion, independently of the rest of the criteria or of the
 * mark.
 */
public sealed interface PerCriterionWeighter extends CriteriaWeighter permits AbsoluteAggregator,StaticWeighter {

	/**
	 * @throws AggregatorException iff the given criterion is rejected.
	 */
	public double weight(Criterion criterion) throws AggregatorException;

	@Override
	default ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) throws AggregatorException {
		return criteria.stream().collect(ImmutableMap.toImmutableMap(c -> c, this::weight));
	}

}
