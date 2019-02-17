package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.PomContext;

class GroupIdMarker implements CriterionMarker {

	private Criterion criterion;
	private PomContext context;

	public GroupIdMarker(Criterion criterion, PomContext context) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(context);
	}

	@Override
	public Mark mark() {
		return Mark.binary(criterion, context.isGroupIdValid());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(criterion).add("Pom context", context).toString();
	}
}
