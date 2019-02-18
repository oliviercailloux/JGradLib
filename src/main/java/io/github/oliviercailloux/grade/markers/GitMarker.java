package io.github.oliviercailloux.grade.markers;

import java.util.function.Function;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitContext;

public class GitMarker {
	public static Mark using(GitContext context, Function<GitContext, Mark> f) {
		return f.apply(context);
	}

	public static Mark repoMark(Criterion criterion, GitContext context) {
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
