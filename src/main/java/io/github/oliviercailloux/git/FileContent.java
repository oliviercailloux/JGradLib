package io.github.oliviercailloux.git;

import java.nio.file.Path;

import io.github.oliviercailloux.grade.GradingException;

public interface FileContent {
	public Path getPath();

	public String getContent();

	public static FileContent of(Path path, String content) {
		return new FileContent() {
			@Override
			public Path getPath() {
				return path;
			}

			@Override
			public String getContent() throws GradingException {
				return content;
			}
		};
	}
}
