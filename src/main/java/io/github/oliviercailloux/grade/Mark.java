package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;

public record Mark(double points, String comment) implements Grade {

	public Mark {
		checkArgument(Double.isFinite(points));
		checkNotNull(comment);
	}

	@Override
	public boolean isMark() {
		return true;
	}

	@Override
	public boolean isComposite() {
		return false;
	}

	@Override
	public ImmutableSet<Criterion> getCriteria() {
		return ImmutableSet.of();
	}

	@Override
	public Grade getGrade(Criterion criterion) {
		throw new IllegalArgumentException();
	}

	@Override
	public SubGrade getSubGrade(Criterion criterion) {
		throw new IllegalArgumentException();
	}

	@Override
	public ImmutableSet<GradePath> getPathsToMarks() {
		return ImmutableSet.of(GradePath.ROOT);
	}

	@Override
	public Grade getGrade(GradePath path) {
		checkArgument(path.isRoot());
		return this;
	}

	@Override
	public Mark getMark(GradePath path) {
		checkArgument(path.isRoot());
		return this;
	}

}
