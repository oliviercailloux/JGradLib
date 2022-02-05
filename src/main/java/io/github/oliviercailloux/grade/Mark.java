package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mark implements Grade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Mark.class);

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

	private final double points;
	private final String comment;

	public Mark(double points, String comment) {
		checkArgument(Double.isFinite(points));
		this.comment = checkNotNull(comment);
		this.points = points;
	}

	public double getPoints() {
		return points;
	}

	public String getComment() {
		return comment;
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

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Mark)) {
			return false;
		}
		final Mark m2 = (Mark) o2;
		return getPoints() == m2.getPoints() && getComment().equals(m2.getComment());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getPoints(), getComment());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("points", getPoints()).add("comment", getComment()).toString();
	}

}
