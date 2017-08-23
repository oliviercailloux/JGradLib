package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;

import com.google.common.base.MoreObjects;

public class Project {
	/**
	 * Not <code>null</code>.
	 */
	private final List<Functionality> functionalities = new LinkedList<>();

	/**
	 * Not <code>null</code>, not empty.
	 */
	private String name;

	public Project(String name) {
		this.name = requireNonNull(name);
		checkArgument(!name.isEmpty());
	}

	public List<Functionality> getFunctionalities() {
		return functionalities;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).addValue(functionalities).toString();
	}

}
