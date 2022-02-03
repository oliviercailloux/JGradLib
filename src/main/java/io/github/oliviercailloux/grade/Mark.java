package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public record Mark(double points, String comment) implements Grade {

	public static Mark zero() {
		return new Mark(0d, "");
	}

	public static Mark zero(String comment) {
		return new Mark(0d, comment);
	}

	public static Mark one() {
		return new Mark(1d, "");
	}

	public static Mark one(String comment) {
		return new Mark(1d, comment);
	}

	public static Mark binary(boolean condition) {
		return condition ? one() : zero();
	}

	public static Mark binary(boolean criterion, String okComment, String elseComment) {
		return criterion ? one(okComment) : zero(elseComment);
	}

	@JsonbCreator
	public static Mark given(@JsonbProperty("points") double points, @JsonbProperty("comment") String comment) {
		return new Mark(points, comment);
	}

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
