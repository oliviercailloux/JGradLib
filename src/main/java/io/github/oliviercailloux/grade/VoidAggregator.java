package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;

/**
 * Accepts only the empty set of marks.
 */
public final class VoidAggregator extends StaticWeighter {
	public static VoidAggregator INSTANCE = new VoidAggregator();

	private VoidAggregator() {
		super(ImmutableMap.of());
	}

}
