package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkState;

import java.util.function.Supplier;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;

public class BoxSupplier implements Supplier<RepositoryCoordinates> {

	private RepositoryCoordinates coordinates;

	public BoxSupplier() {
		coordinates = null;
	}

	@Override
	public RepositoryCoordinates get() {
		checkState(coordinates != null);
		return coordinates;
	}

	public void set(RepositoryCoordinates coordinates) {
		this.coordinates = coordinates;
	}

}
