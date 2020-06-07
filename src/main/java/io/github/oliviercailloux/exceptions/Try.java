package io.github.oliviercailloux.exceptions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 *
 * Heavily inspired by https://github.com/vavr-io/vavr.
 */
public class Try<E> {
	public static <E> Try<E> of(Supplier<E> supplier) {
		try {
			return success(supplier.get());
		} catch (Throwable t) {
			return Try.failure(t);
		}
	}

	public static <E> Try<E> success(E e) {
		return new Try<>(e, null);
	}

	public static <E> Try<E> failure(Throwable t) {
		return new Try<>(null, t);
	}

	private final E e;
	private final Throwable t;

	private Try(E e, Throwable t) {
		final boolean thown = e == null;
		final boolean resulted = t == null;
		checkArgument(resulted == !thown);
		this.t = t;
		this.e = e;
	}

	public boolean isSuccess() {
		return e != null;
	}

	public boolean isFailure() {
		return t != null;
	}

	public E get() {
		checkState(isSuccess());
		return e;
	}

	public Throwable getCause() {
		checkState(isFailure());
		return t;
	}

	public <E2> Try<E2> map(Function<E, E2> transformation) {
		return isFailure() ? Try.failure(t) : Try.of(() -> transformation.apply(e));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Try)) {
			return false;
		}

		final Try<?> t2 = (Try<?>) o2;
		return Objects.equals(e, t2.e) && Objects.equals(t, t2.t);
	}

	@Override
	public int hashCode() {
		return Objects.hash(e, t);
	}

	@Override
	public String toString() {
		final ToStringHelper stringHelper = MoreObjects.toStringHelper(this);
		if (isSuccess()) {
			stringHelper.add("e", e);
		} else {
			stringHelper.add("t", t);
		}
		return stringHelper.toString();
	}

}
