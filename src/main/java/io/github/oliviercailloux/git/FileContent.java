package io.github.oliviercailloux.git;

import java.nio.file.Path;

public interface FileContent {
	public Path getPath();

	public String getContent();
}
