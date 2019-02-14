package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.Mark;
import io.github.oliviercailloux.st_projects.model.GitContext;

public class GitMarker implements CriterionMarker {
	private GitContext context;
	private Function<GitContext, Mark> f;

	public static CriterionMarker using(GitContext context, Function<GitContext, Mark> f) {
		return new GitMarker(context, f);
	}

	private GitMarker(GitContext context, Function<GitContext, Mark> f) {
		this.context = requireNonNull(context);
		this.f = requireNonNull(f);
	}

	@Override
	public Mark mark() {
		return f.apply(context);
	}

	public static CriterionMarker repoMarker(Criterion criterion, GitContext context) {
		return () -> markRepo(context, criterion);
	}

	private static Mark markRepo(GitContext context, Criterion criterion) {
		final Client client = context.getClient();

		final Mark grade;
		if (!client.existsCached()) {
			grade = Mark.min(criterion, "Repository not found");
		} else if (!client.hasContentCached()) {
			grade = Mark.min(criterion, "Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			grade = Mark.min(criterion, "Repository found with content but no suitable commit found");
		} else {
			grade = Mark.max(criterion);
		}

		return grade;
	}
}
