package io.github.oliviercailloux.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.base.VerifyException;

import io.github.oliviercailloux.exceptions.durian.Throwing;

/**
 * <p>
 * An object able to transform functional interfaces that throw checked
 * exceptions to functional interfaces that throw only runtime exceptions; and
 * able to invoke function interfaces that throw checked exceptions.
 * </p>
 * <p>
 * Instances of this class hold a function that transforms any checked exception
 * to unchecked exceptions.
 * </p>
 * <p>
 * Instances of this class are immutable.
 * </p>
 *
 * @param <EF> the checked exception type that this object will accept
 * @param <ET> the type of unchecked exception that this object will throw in
 *             place of the checked exception
 */
public class Unchecker<EF extends Exception, ET extends RuntimeException> {
	/**
	 * An object that accepts functional interfaces that throw {@link IOException}
	 * instances; and that will throw {@link UncheckedIOException} instances
	 * instead.
	 */
	public static final Unchecker<IOException, UncheckedIOException> IO_UNCHECKER = Unchecker
			.wrappingWith(UncheckedIOException::new);

	/**
	 * An object that accepts functional interfaces that throw
	 * {@link URISyntaxException} instances; and that will throw
	 * {@link VerifyException} instances instead.
	 */
	public static final Unchecker<URISyntaxException, VerifyException> URI_UNCHECKER = Unchecker
			.wrappingWith(VerifyException::new);

	/**
	 * Returns an object that will use the given wrapper function to transform
	 * checked exceptions to unchecked ones, if any checked exception happens.
	 */
	public static <EF extends Exception, ET extends RuntimeException> Unchecker<EF, ET> wrappingWith(
			Function<EF, ET> wrapper) {
		return new Unchecker<>(wrapper);
	}

	private Function<EF, ET> wrapper;

	private Unchecker(Function<EF, ET> wrapper) {
		this.wrapper = checkNotNull(wrapper);
	}

	/**
	 * Calls the given runnable; if it throws a checked exception, throws an
	 * unchecked exception instead, applying the wrapper; if the runnable throws an
	 * unchecked exception, the exception is thrown unchanged.
	 */
	public void call(Throwing.Specific.Runnable<EF> runnable) {
		try {
			runnable.run();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			@SuppressWarnings("unchecked")
			final EF ef = (EF) e;
			throw wrapper.apply(ef);
		}
	}

	/**
	 * Attempts to get and return a result from the given supplier; if the supplier
	 * throws a checked exception, throws an unchecked exception instead, applying
	 * the wrapper; if the supplier throws an unchecked exception, the exception is
	 * thrown unchanged.
	 */
	public <T> T getUsing(Throwing.Specific.Supplier<T, EF> supplier) {
		try {
			return supplier.get();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			@SuppressWarnings("unchecked")
			final EF ef = (EF) e;
			throw wrapper.apply(ef);
		}
	}

	/**
	 * Returns a runnable that delegates to the given runnable, except that any
	 * checked exception thrown by the given runnable is instead thrown by the
	 * returned runnable as an unchecked exception, applying the wrapper to
	 * transform it.
	 */
	public Runnable wrapRunnable(Throwing.Specific.Runnable<EF> runnable) {
		return () -> {
			try {
				runnable.run();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				@SuppressWarnings("unchecked")
				final EF ef = (EF) e;
				throw wrapper.apply(ef);
			}
		};
	}

	/**
	 * Returns a function that simply delegates to the given function, except that
	 * any checked exception thrown by the given function is instead thrown by the
	 * returned function as an unchecked exception, applying the wrapper to
	 * transform it.
	 */
	public <F, T> Function<F, T> wrapFunction(Throwing.Specific.Function<F, T, EF> function) {
		return arg -> {
			try {
				return function.apply(arg);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				@SuppressWarnings("unchecked")
				final EF ef = (EF) e;
				throw wrapper.apply(ef);
			}
		};
	}

	/**
	 * Returns a supplier that simply delegates to the given supplier, except that
	 * any checked exception thrown by the given supplier is instead thrown by the
	 * returned supplier as an unchecked exception, applying the wrapper to
	 * transform it.
	 */
	public <T> Supplier<T> wrapSupplier(Throwing.Specific.Supplier<T, EF> supplier) {
		return () -> {
			try {
				return supplier.get();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				@SuppressWarnings("unchecked")
				final EF ef = (EF) e;
				throw wrapper.apply(ef);
			}
		};
	}

	/**
	 * Returns a predicate that simply delegates to the given predicate, except that
	 * any checked exception thrown by the given predicate is instead thrown by the
	 * returned predicate as an unchecked exception, applying the wrapper to
	 * transform it.
	 */
	public <F> Predicate<F> wrapPredicate(Throwing.Specific.Predicate<F, EF> predicate) {
		return (arg) -> {
			try {
				return predicate.test(arg);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				@SuppressWarnings("unchecked")
				final EF ef = (EF) e;
				throw wrapper.apply(ef);
			}
		};
	}
}
