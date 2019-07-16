package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

class CriterionImpl implements Criterion {

	private final String name;

	CriterionImpl(String name) {
		this.name = checkNotNull(name);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Criterion)) {
			return false;
		}
		final Criterion c2 = (Criterion) o2;
		return getName().equals(c2.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		return getName();
	}
}
