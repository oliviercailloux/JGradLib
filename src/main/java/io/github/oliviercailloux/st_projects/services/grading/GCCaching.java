package io.github.oliviercailloux.st_projects.services.grading;

import java.util.function.Supplier;

import io.github.oliviercailloux.st_projects.model.GradingContexter;

class GCCaching<T> implements GradingContexter {
	private final Supplier<T> s;
	private T cached;

	GCCaching(Supplier<T> s) {
		this.s = s;
		cached = null;
	}

	@Override
	public void init() throws GradingException {
		cached = s.get();
	}

	@Override
	public void clear() {
//				TODO();
	}

	public T getCached() {
		return cached;
	}
}