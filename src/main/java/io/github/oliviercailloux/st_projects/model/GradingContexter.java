package io.github.oliviercailloux.st_projects.model;

import io.github.oliviercailloux.st_projects.services.grading.GradingException;

public interface GradingContexter {
	public void clear();

	public void init() throws GradingException;
}
