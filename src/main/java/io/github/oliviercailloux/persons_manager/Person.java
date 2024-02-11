package io.github.oliviercailloux.persons_manager;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * A person, with an id and a name. Two persons are identical, or “equal”, iff they have the same id
 * and equal names.
 */
public class Person {
	public static Person given(int id, String name) {
		return new Person(id, name);
	}

	final private int id;
	final private String name;

	private Person(int id, String name) {
		this.id = id;
		this.name = checkNotNull(name);
	}

	public int getId() {
		return id;
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
		return id == t2.id && name.equals(t2.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, name);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Id", id).add("Name", name).toString();
	}
}
