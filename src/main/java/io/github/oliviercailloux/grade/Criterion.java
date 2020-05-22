package io.github.oliviercailloux.grade;

public interface Criterion {

	public static Criterion given(String name) {
		return new CriterionImpl(name);
	}

	public String getName();

	/**
	 * Two criteria are equal iff they have the same name.
	 *
	 * TODO this is not currently implemented and creates problem when deserializing
	 * with simple criteria.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o2);
}
