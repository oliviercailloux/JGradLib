package io.github.oliviercailloux.st_projects.services.grading;

import java.nio.file.Path;

public interface FileContent {
	public Path getPath();

	public String getContent() throws GradingException;
}
