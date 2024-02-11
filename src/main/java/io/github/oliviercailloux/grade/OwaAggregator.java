package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OwaAggregator implements OwaWeighter {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(OwaAggregator.class);

	public static OwaAggregator given(List<Double> weights) {
		return new OwaAggregator(weights);
	}

	private final List<Double> weights;
	private final double sum;

	private OwaAggregator(List<Double> weights) {
		this.weights = checkNotNull(weights);
		sum = weights.stream().mapToDouble(w -> w).sum();
	}

	public List<Double> weights() {
		return weights;
	}

	@Override
	public Stream<Double> weights(int size) {
		return weights.stream().map(w -> w / sum);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof OwaAggregator)) {
			return false;
		}
		final OwaAggregator t2 = (OwaAggregator) o2;
		return weights.equals(t2.weights);
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
