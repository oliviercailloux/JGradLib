package io.github.oliviercailloux.workers;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MyWorkersBad implements Workers {

	public static MyWorkersBad empty() {
		return new MyWorkersBad();
	}

	private MyWorkersBad() {
		/* Empty. */
	}

	@Override
	public void add(Person p) {
		/* Empty. */
	}

	@Override
	public void setPosition(Person p, int newPosition) {
		/* Empty. */
	}

	@Override
	public Optional<Person> get(int pos) {
		return null;
	}

	@Override
	public Set<List<Person>> getAsTeamsOfSize(int n) {
		return null;
	}
}
