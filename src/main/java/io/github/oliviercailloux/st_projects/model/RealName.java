package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

/**
 * Immutable.
 *
 * @author Olivier Cailloux
 *
 */
public class RealName {
	private String firstName;

	private String lastName;

	public RealName(String firstName, String lastName) {
		this.firstName = requireNonNull(firstName);
		this.lastName = requireNonNull(lastName);
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}
}
