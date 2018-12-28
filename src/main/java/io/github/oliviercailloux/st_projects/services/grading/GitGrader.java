package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;

public class GitGrader implements CriterionGrader {
	private GitContext context;
	private Function<GitContext, CriterionGrade> f;

	public static CriterionGrader using(GitContext context, Function<GitContext, CriterionGrade> f) {
		return new GitGrader(context, f);
	}

	private GitGrader(GitContext context, Function<GitContext, CriterionGrade> f) {
		this.context = requireNonNull(context);
		this.f = requireNonNull(f);
	}

	@Override
	public CriterionGrade grade() {
		return f.apply(context);
	}

	public static CriterionGrader repoGrader(Criterion criterion, GitContext context) {
		return () -> gradeRepo(context, criterion);
	}

	private static CriterionGrade gradeRepo(GitContext context, Criterion criterion) {
		final Client client = context.getClient();

		final CriterionGrade grade;
		if (!client.existsCached()) {
			grade = CriterionGrade.min(criterion, "Repository not found");
		} else if (!client.hasContentCached()) {
			grade = CriterionGrade.min(criterion, "Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			grade = CriterionGrade.min(criterion, "Repository found with content but no suitable commit found");
		} else {
			grade = CriterionGrade.max(criterion);
		}

		return grade;
	}
}
