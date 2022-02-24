package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Set;

/**
 * Rejects nothing. Binds the result within [−1, 1].
 */
public class AbsoluteAggregator implements CriteriaWeighter {

	private static final AbsoluteAggregator INSTANCE = new AbsoluteAggregator();

	public static AbsoluteAggregator instance() {
		return INSTANCE;
	}

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria)
			throws IllegalArgumentException {
		return Maps.toMap(criteria, c -> 1d);
	}

}
