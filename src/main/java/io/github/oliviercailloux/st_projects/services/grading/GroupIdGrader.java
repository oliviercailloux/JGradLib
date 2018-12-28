package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.PomContext;

class GroupIdGrader implements CriterionGrader {

	private Criterion criterion;
	private PomContext context;

	public GroupIdGrader(Criterion criterion, PomContext context) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(context);
	}

	@Override
	public CriterionGrade grade() {
		return CriterionGrade.binary(criterion, context.isGroupIdValid());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(criterion).add("Pom context", context).toString();
	}
}
