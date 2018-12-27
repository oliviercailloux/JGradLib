package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradeCriterion;

public class SimpleGrader implements CriterionGrader<GradeCriterion> {
	private GitContext context;
	private Function<GitContext, CriterionGrade<GradeCriterion>> f;

	public static CriterionGrader<? extends GradeCriterion> using(GitContext context,
			Function<GitContext, CriterionGrade<GradeCriterion>> f) {
		return new SimpleGrader(context, f);
	}

	private SimpleGrader(GitContext context, Function<GitContext, CriterionGrade<GradeCriterion>> f) {
		this.context = requireNonNull(context);
		this.f = requireNonNull(f);
	}

	@Override
	public CriterionGrade<GradeCriterion> grade() {
		return f.apply(context);
	}

}
