package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GitContext;

public class GitToSourcer implements ContentSupplier {

	private final GitContext context;
	private String content;
	private final Path path;

	public GitToSourcer(GitContext context, Path path) {
		this.context = requireNonNull(context);
		this.path = path;
		clear();
	}

	@Override
	public String getContent() {
		assert content != null;
		return content;
	}

	public void clear() {
		content = null;
	}

	public void init() throws GradingException {
		final Client client = context.getClient();
		try {
			content = client.fetchBlobOrEmpty(path);
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}
}
