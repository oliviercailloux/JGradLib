package io.github.oliviercailloux.grade;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinAggregator implements OwaWeighter {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MinAggregator.class);

	public static final MinAggregator INSTANCE = new MinAggregator();

	private MinAggregator() {
	}

	@Override
	public Stream<Double> weights(int size) {
		if (size == 0) {
			return Stream.empty();
		}
		return Stream.concat(Stream.generate(() -> 0d).limit(size - 1), Stream.of(1d));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof MinAggregator)) {
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
