package io.github.oliviercailloux.st_projects.model;

public interface Criterion {

	public String getRequirement();

	public double getMaxPoints();

	public default double getMinPoints() {
		return 0d;
	}

}
