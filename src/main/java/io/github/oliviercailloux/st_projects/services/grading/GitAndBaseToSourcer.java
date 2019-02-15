package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;

/**
 * Should be deleted, git context should contain the real project directory
 * (unless there can be more than one and it should be decided dynamically?).
 *
 * @author Olivier Cailloux
 *
 */
public class GitAndBaseToSourcer implements GradingContexter, ContentSupplier {
	public static ContentSupplier given(GitContext context, PomSupplier pomSupplier, Path relativePath) {
		final GitAndBaseToSourcer sourcer = new GitAndBaseToSourcer(context, pomSupplier, relativePath);
		sourcer.init();
		return sourcer;
	}

	private final GitContext context;
	private String content;
	private final Path relativePath;
	private PomSupplier pomSupplier;

	private GitAndBaseToSourcer(GitContext context, PomSupplier pomSupplier, Path relativePath) {
		this.context = requireNonNull(context);
		this.pomSupplier = requireNonNull(pomSupplier);
		this.relativePath = requireNonNull(relativePath);
		clear();
	}

	@Override
	public String getContent() {
		assert content != null;
		return content;
	}

	@Override
	public void clear() {
		content = null;
	}

	@Override
	public void init() throws GradingException {
		final Client client = context.getClient();
		try {
			content = client
					.fetchBlobOrEmpty(pomSupplier.getProjectRelativeRoot().orElse(Paths.get("")).resolve(relativePath));
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}
}
