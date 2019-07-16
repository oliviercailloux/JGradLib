package io.github.oliviercailloux.grade;

public interface Criterion {

	public static Criterion given(String name) {
		return new CriterionImpl(name);
	}

	public String getName();

	/**
	 * Two criteria are equal iff they have the same name.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o2);
}
