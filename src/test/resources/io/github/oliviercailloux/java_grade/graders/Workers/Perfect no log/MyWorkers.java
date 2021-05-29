package io.github.oliviercailloux.workers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyWorkers implements Workers {


	public static MyWorkers empty() {
		return new MyWorkers();
	}

	private final List<Person> persons;

	private MyWorkers() {
		persons = new ArrayList<>();
	}

	@Override
	public void add(Person p) {
		persons.add(p);
	}

	@Override
	public void setPosition(Person p, int newPosition) {
		final boolean removed = persons.remove(p);
		checkState(removed);
		persons.add(newPosition, p);
	}

	@Override
	public Optional<Person> get(int pos) {
		return pos < persons.size() ? Optional.of(persons.get(pos)) : Optional.empty();
	}

	@Override
	public Set<List<Person>> getAsTeamsOfSize(int n) {
		checkArgument(n >= 1);
		checkArgument(persons.size() % n == 0, "Does not divide (" + persons.size() + ")");
		final ImmutableSet.Builder<List<Person>> teamsBuilder = ImmutableSet.builder();
		for (int i = 0; i < persons.size(); i += n) {
			teamsBuilder.add(persons.subList(i, i + n));
		}

		return teamsBuilder.build();
	}

	@Override
	public String toString() {
		return "A list of " + persons.size() + " workers";
	}
}
