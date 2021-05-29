package io.github.oliviercailloux.workers;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * A person, with a name. Two persons are identical, or “equal”, iff they have
 * the same names.
 */
public class Person {
	public static Person named(String name) {
		return new Person(name);
	}

	final private String name;

	private Person(String name) {
		this.name = checkNotNull(name);
	}

	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Person)) {
			return false;
		}
		final Person t2 = (Person) o2;
		return name.equals(t2.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Name", name).toString();
	}
}
