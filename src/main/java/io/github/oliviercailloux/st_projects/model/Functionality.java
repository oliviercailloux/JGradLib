package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;

/**
 * Immutable.
 *
 * @author Olivier Cailloux
 *
 */
public class Functionality {
	/**
	 * Not <code>null</code>, not empty.
	 */
	private String description;

	/**
	 * ≥ 1
	 */
	private int difficulty;

	/**
	 * Not <code>null</code>, not empty.
	 */
	private String name;

	public Functionality(String name, String description, int difficulty) {
		this.name = requireNonNull(name);
		this.description = requireNonNull(description);
		checkArgument(!name.isEmpty());
		checkArgument(!description.isEmpty());
		checkArgument(difficulty >= 1);
		this.difficulty = difficulty;
	}

	public String getDescription() {
		return description;
	}

	public int getDifficulty() {
		return difficulty;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).addValue(description).add("Difficulty", difficulty)
				.toString();
	}
}
