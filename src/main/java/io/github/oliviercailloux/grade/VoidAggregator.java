package io.github.oliviercailloux.grade;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Objects;

/**
 * Accepts only the empty set of marks.
 */
public final class VoidAggregator extends StaticWeighter {
	public static VoidAggregator INSTANCE = new VoidAggregator();

	private VoidAggregator() {
		super(ImmutableMap.of());
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof VoidAggregator)) {
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
