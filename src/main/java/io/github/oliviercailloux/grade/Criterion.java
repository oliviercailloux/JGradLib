package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class Criterion {

	public static Criterion given(String name) {
		return new Criterion(checkNotNull(name));
	}

	private final String name;

	Criterion(String name) {
		this.name = checkNotNull(name);
	}

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
		return MoreObjects.toStringHelper(this).addValue(name).toString();
	}
}
