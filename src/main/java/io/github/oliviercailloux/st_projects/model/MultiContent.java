package io.github.oliviercailloux.st_projects.model;

import java.nio.file.Path;

import com.google.common.collect.ImmutableMap;

public interface MultiContent {
	/**
	 * @return the paths are relative to the project directory.
	 */
	public ImmutableMap<Path, String> getContents();
}
