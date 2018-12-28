package io.github.oliviercailloux.st_projects.model;

import io.github.oliviercailloux.st_projects.services.grading.GradingException;

public interface GradingContext {
	public void clear();

	public void init() throws GradingException;
}
