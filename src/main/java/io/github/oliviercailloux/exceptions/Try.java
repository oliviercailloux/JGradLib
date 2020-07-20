package io.github.oliviercailloux.exceptions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * <p>
 * An instance of this class keeps either a result (in which case it is called a
 * “success”) or a cause of type {@link Throwable} (in which case it is called a
 * “failure”).
 * </p>
 * <p>
 * Instances of this class are immutable.
 * </p>
 * <p>
 * Heavily inspired by https://github.com/vavr-io/vavr.
 * </p>
 *
 * @param <T> the type of result possibly kept in this object.
 */
public class Try<T> {
	/**
	 * Attempts to get and encapsulate a result from the given supplier.
	 *
	 * @param <T>      the type of result supplied by this supplier
	 * @param supplier the supplier to get a result from
	 * @return a success encapsulating the result if the supplier returns a result;
	 *         a failure encapsulating the exception if the supplier throws an
	 *         exception
	 */
	public static <T> Try<T> of(Supplier<T> supplier) {
		try {
			return success(supplier.get());
		} catch (Throwable t) {
			return Try.failure(t);
		}
	}

	/**
	 * Returns a success encapsulating the given result.
	 *
	 * @param <T> the type of result to encapsulate
	 * @param t   the result to encapsulate
	 */
	public static <T> Try<T> success(T t) {
		return new Try<>(t, null);
	}

	/**
	 * Returns a failure encapsulating the given cause.
	 *
	 * @param <T>   the type that parameterize the returned instance (unused, as it
	 *              only determines which result this instance can hold, but it
	 *              holds none)
	 * @param cause the cause to encapsulate
	 */
	public static <T> Try<T> failure(Throwable cause) {
		return new Try<>(null, cause);
	}

	private final T result;
	private final Throwable cause;

	private Try(T t, Throwable cause) {
		final boolean thrown = t == null;
		final boolean resulted = cause == null;
		checkArgument(resulted == !thrown);
		this.cause = cause;
		this.result = t;
	}

	/**
	 * Returns <code>true</code> iff this object encapsulates a result (and not a
	 * cause).
	 *
	 * @return <code>true</code> iff {@link #isFailure()} returns <code>false</code>
	 */
	public boolean isSuccess() {
		return result != null;
	}

	/**
	 * Return <code>true</code> iff this object encapsulates a cause (and not a
	 * result).
	 *
	 * @return <code>true</code> iff {@link #isSuccess()} returns <code>false</code>
	 */
	public boolean isFailure() {
		return cause != null;
	}

	/**
	 * If this instance is a success, returns the result it encapsulates.
	 *
	 * @throws IllegalStateException if this instance is a failure, thus,
	 *                               encapsulates a cause but not a result.
	 * @see #isSuccess()
	 */
	public T get() throws IllegalStateException {
		checkState(isSuccess());
		return result;
	}

	/**
	 * If this object is a failure, returns the cause it encapsulates.
	 *
	 * @throws IllegalStateException if this object is a success, thus, encapsulates
	 *                               a result but not a cause.
	 * @see #isFailure()
	 */
	public Throwable getCause() {
		checkState(isFailure());
		return cause;
	}

	/**
	 * If this instance is a success, returns a {@link Try} that encapsulates the
	 * result of applying the given transformation to the result this instance
	 * encapsulates. If this instance is a failure, returns this instance.
	 *
	 * @param <E2>           the type of result the transformation produces.
	 * @param transformation the function to apply to the result this instance
	 *                       holds, if it is a success
	 * @return a success iff this instance is a success
	 */
	public <E2> Try<E2> map(Function<T, E2> transformation) {
		final Try<E2> newResult;
		if (isFailure()) {
			@SuppressWarnings("unchecked")
			final Try<E2> casted = (Try<E2>) this;
			newResult = casted;
		} else {
			newResult = Try.of(() -> transformation.apply(result));
		}
		return newResult;
	}

	/**
	 * Returns <code>true</code> iff the given object is a {@link Try}; is a success
	 * or a failure according to whether this instance is a success or a failure;
	 * and holds an equal result or cause.
	 */
	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Try)) {
			return false;
		}

		final Try<?> t2 = (Try<?>) o2;
		return Objects.equals(result, t2.result) && Objects.equals(cause, t2.cause);
	}

	@Override
	public int hashCode() {
		return Objects.hash(result, cause);
	}

	/**
	 * Returns a string representation of this object, suitable for debug.
	 */
	@Override
	public String toString() {
		final ToStringHelper stringHelper = MoreObjects.toStringHelper(this);
		if (isSuccess()) {
			stringHelper.add("result", result);
		} else {
			stringHelper.add("cause", cause);
		}
		return stringHelper.toString();
	}

}
