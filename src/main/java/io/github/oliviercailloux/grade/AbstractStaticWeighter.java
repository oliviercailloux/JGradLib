package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rejects unknown criteria.
 */
public abstract class AbstractStaticWeighter implements CriteriaWeighter {

	protected final ImmutableMap<Criterion, Double> weights;

	protected AbstractStaticWeighter(Map<Criterion, Double> weights) {
		this.weights = ImmutableMap.copyOf(weights);
		checkArgument(weights.values().stream().allMatch(Double::isFinite));
		checkArgument(weights.values().stream().allMatch(w -> w >= 0d));
	}

	public ImmutableMap<Criterion, Double> weights() {
		return weights;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof AbstractStaticWeighter)) {
			return false;
		}
		final AbstractStaticWeighter t2 = (AbstractStaticWeighter) o2;
		/*
		 * If weights is empty, then equality is possible even with different classes: a
		 * StaticWeighter using empty weights that is not a VoidAggregator has the same
		 * behavior as a VoidAggregator.
		 */
		return (getClass().equals(t2.getClass()) || weights.isEmpty()) && weights.equals(t2.weights);
	}

	@Override
	public int hashCode() {
		return Objects.hash(weights);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("weights", weights).toString();
	}
}
