package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.context.GitContext;

/**
 * Should be deleted, git context should contain the real project directory
 * (unless there can be more than one and it should be decided dynamically?).
 *
 * @author Olivier Cailloux
 *
 */
public class GitAndBaseToSourcer {
	public static String given(GitContext context, Path path) {
		requireNonNull(path);
		final Client client = context.getClient();
		final String content;
		try {
			content = client.fetchBlobOrEmpty(path);
		} catch (IOException e) {
			throw new GradingException(e);
		}
		return content;
	}
}
