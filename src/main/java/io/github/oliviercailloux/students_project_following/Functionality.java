package io.github.oliviercailloux.students_project_following;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;

import javax.json.bind.annotation.JsonbPropertyOrder;

import com.google.common.base.MoreObjects;

/**
 * Immutable.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "name", "description", "difficulty" })
public class Functionality {
	/**
	 * Not <code>null</code>, not empty.
	 */
	private final String description;

	/**
	 * > 0
	 */
	private final BigDecimal difficulty;

	/**
	 * Not <code>null</code>, not empty.
	 */
	private final String name;

	public Functionality(String name, String description, BigDecimal difficulty) {
		this.name = requireNonNull(name);
		this.description = requireNonNull(description);
		checkArgument(!name.isEmpty());
		checkArgument(!description.isEmpty());
		checkArgument(difficulty.compareTo(BigDecimal.ZERO) > 0);
		this.difficulty = difficulty;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getDifficulty() {
		return difficulty;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).add("Difficulty", difficulty).toString();
	}
}
