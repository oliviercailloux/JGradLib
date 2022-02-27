package io.github.oliviercailloux.grade;

import java.util.stream.Stream;

public final class MaxAggregator implements OwaWeighter {

	@Override
	public Stream<Double> weights(int size) {
		if (size == 0) {
			return Stream.empty();
		}
		return Stream.concat(Stream.of(1d), Stream.generate(() -> 0d)).limit(size - 1);
	}

}
