package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;

/**
 * Accepts only the empty set of marks.
 */
public final class VoidAggregator extends StaticWeighter {
	private VoidAggregator() {
		super(ImmutableMap.of());
	}
}
