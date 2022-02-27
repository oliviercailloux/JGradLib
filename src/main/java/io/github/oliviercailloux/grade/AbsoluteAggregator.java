package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Set;

/**
 * Rejects nothing. Binds the result within [−1, 1].
 */
public final class AbsoluteAggregator implements CriteriaWeighter {

	private static final AbsoluteAggregator INSTANCE = new AbsoluteAggregator();

	public static AbsoluteAggregator instance() {
		return INSTANCE;
	}

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) {
		return Maps.toMap(criteria, c -> 1d);
	}

}
