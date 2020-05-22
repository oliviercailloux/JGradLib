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

public class Unchecker<EF extends Exception, ET extends RuntimeException> {
	public static final Unchecker<IOException, UncheckedIOException> IO_UNCHECKER = Unchecker
			.wrappingWith(UncheckedIOException::new);

	public static final Unchecker<URISyntaxException, VerifyException> URI_UNCHECKER = Unchecker
			.wrappingWith(VerifyException::new);

	public static <EF extends Exception, ET extends RuntimeException> Unchecker<EF, ET> wrappingWith(
			Function<EF, ET> wrapper) {
		return new Unchecker<>(wrapper);
	}

	private Function<EF, ET> wrapper;

	private Unchecker(Function<EF, ET> wrapper) {
		this.wrapper = checkNotNull(wrapper);
	}

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
