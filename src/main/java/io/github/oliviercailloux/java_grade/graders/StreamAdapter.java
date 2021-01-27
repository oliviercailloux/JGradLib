package io.github.oliviercailloux.java_grade.graders;

import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import name.falgout.jeffrey.throwing.ThrowingFunction;
import name.falgout.jeffrey.throwing.ThrowingPredicate;
import name.falgout.jeffrey.throwing.ThrowingSupplier;

class StreamAdapter<T, X extends Throwable> {
	@SuppressWarnings("serial")
	private static class AdapterException extends RuntimeException {
		public AdapterException(Throwable cause) {
			super(cause);
		}
	}

	private final Stream<T> delegate;

	StreamAdapter(Stream<T> delegate) {
		this.delegate = delegate;
	}

	private <R> R maskException(ThrowingSupplier<R, X> method) {
		try {
			return method.get();
		} catch (Throwable t) {
			if (t instanceof RuntimeException) {
				throw (RuntimeException) t;
			}
			throw new AdapterException(t);
		}

	}

	public StreamAdapter<T, X> filter(ThrowingPredicate<T, X> predicate) {
		return new StreamAdapter<>(delegate.filter(t -> maskException(() -> predicate.test(t))));
	}

	public <R> StreamAdapter<R, X> map(ThrowingFunction<T, R, X> mapper) {
		return new StreamAdapter<>(delegate.map(t -> maskException(() -> mapper.apply(t))));
	}

	@SuppressWarnings("unchecked")
	private <R> R unmaskException(Supplier<R> method) throws X {
		try {
			return method.get();
		} catch (AdapterException e) {
			throw (X) e.getCause();
		}
	}

	public <A, R> R collect(Collector<T, A, R> collector) throws X {
		return unmaskException(() -> delegate.collect(collector));
	}
}