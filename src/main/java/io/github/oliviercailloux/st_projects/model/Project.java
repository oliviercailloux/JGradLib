package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public class Project {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Project.class);

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
		LOGGER.info("Created {}.", name);
	}

	public List<Functionality> getFunctionalities() {
		return functionalities;
	}

	public String getGitHubName() {
		return name.replace(' ', '-');
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).addValue(functionalities).toString();
	}

}
