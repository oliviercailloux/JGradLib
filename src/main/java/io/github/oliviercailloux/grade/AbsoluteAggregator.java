package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Rejects nothing. Binds the result within [−1, 1].
 */
public class AbsoluteAggregator implements MarkAggregator {

	private static final AbsoluteAggregator INSTANCE = new AbsoluteAggregator();

	public static AbsoluteAggregator instance() {
		return INSTANCE;
	}

	@Override
	public ImmutableMap<SubMark, Double> weights(Set<SubMark> marks) {
		final ImmutableSet<Criterion> criteria = marks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(marks.size() == criteria.size());
		return marks.stream().collect(ImmutableMap.toImmutableMap(s -> s, s -> 1d));
	}

}
