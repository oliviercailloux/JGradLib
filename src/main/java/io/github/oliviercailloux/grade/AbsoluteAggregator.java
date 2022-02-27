package io.github.oliviercailloux.grade;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Objects;
import java.util.Set;

/**
 * Rejects nothing. Binds the result within [−1, 1].
 */
public final class AbsoluteAggregator implements CriteriaWeighter {
	public static final AbsoluteAggregator INSTANCE = new AbsoluteAggregator();

	@Override
	public ImmutableMap<Criterion, Double> weightsFromCriteria(Set<Criterion> criteria) {
		return Maps.toMap(criteria, c -> 1d);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof AbsoluteAggregator)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getClass());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).toString();
	}
}
