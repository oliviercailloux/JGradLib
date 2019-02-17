package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;

import io.github.oliviercailloux.grade.GradingException;

public interface FileContent {
	public Path getPath();

	public String getContent() throws GradingException;
}
