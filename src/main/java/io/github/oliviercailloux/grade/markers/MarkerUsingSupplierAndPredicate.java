package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;

public class MarkerUsingSupplierAndPredicate<T> implements CriterionMarker {
	private Supplier<? extends T> supplier;
	private Predicate<? super T> conditionForPoints;
	private Criterion criterion;

	public MarkerUsingSupplierAndPredicate(Criterion criterion, Supplier<? extends T> supplier,
			Predicate<? super T> conditionForPoints) {
		this.criterion = requireNonNull(criterion);
		this.supplier = requireNonNull(supplier);
		this.conditionForPoints = requireNonNull(conditionForPoints);
	}

	@Override
	public Mark mark() {
		final T content = supplier.get();
		final boolean result = conditionForPoints.test(content);
		return Mark.binary(criterion, result);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkerUsingSupplierAndPredicate.class);

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Criterion", criterion).add("Supplier", supplier)
				.add("Condition for points", conditionForPoints).toString();
	}

}
