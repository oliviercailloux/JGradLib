package io.github.oliviercailloux.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class TryVoid {
	public static TryVoid run(Runnable runnable) {
		try {
			runnable.run();
			return success();
		} catch (Throwable t) {
			return TryVoid.failure(t);
		}
	}

	public static TryVoid success() {
		return new TryVoid(null);
	}

	public static TryVoid failure(Throwable t) {
		return new TryVoid(checkNotNull(t));
	}

	private final Throwable t;

	private TryVoid(Throwable t) {
		this.t = t;
	}

	public boolean isSuccess() {
		return t == null;
	}

	public boolean isFailure() {
		return t != null;
	}

	public Throwable getCause() {
		checkState(isFailure());
		return t;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof TryVoid)) {
			return false;
		}

		final TryVoid t2 = (TryVoid) o2;
		return Objects.equals(t, t2.t);
	}

	@Override
	public int hashCode() {
		return Objects.hash(t);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("t", t).toString();
	}

}
