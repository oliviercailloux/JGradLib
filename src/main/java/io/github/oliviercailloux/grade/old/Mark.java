package io.github.oliviercailloux.grade.old;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import java.util.Objects;

@JsonbPropertyOrder({ "points", "comments" })
public class Mark implements IGrade {

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

	private Mark(double points, String comment) {
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = checkNotNull(comment);
	}

	@Override
	public double getPoints() {
		return points;
	}

	@Override
	public String getComment() {
		return comment;
	}

	/**
	 * Returns the empty map.
	 */
	@JsonbTransient
	@Override
	public ImmutableMap<Criterion, IGrade> getSubGrades() {
		return ImmutableMap.of();
	}

	@Override
	@JsonbTransient
	public ImmutableSet<CriterionGradeWeight> getSubGradesAsSet() {
		return ImmutableSet.of();
	}

	@Override
	@JsonbTransient
	public ImmutableMap<Criterion, Double> getWeights() {
		return ImmutableMap.of();
	}

	@Override
	public IGrade limitedDepth(int depth) {
		checkArgument(depth >= 0);
		return this;
	}

	@Override
	public IGrade withComment(String newComment) {
		return new Mark(points, newComment);
	}

	@Override
	public IGrade withSubGrade(Criterion criterion, IGrade newSubGrade) {
		throw new IllegalArgumentException();
	}

	public io.github.oliviercailloux.grade.Mark asNew() {
		return io.github.oliviercailloux.grade.Mark.given(points, comment);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof IGrade)) {
			return false;
		}
		IGrade g2 = (IGrade) o2;
		return getPoints() == g2.getPoints() && getComment().equals(g2.getComment())
				&& getSubGrades().equals(g2.getSubGrades());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getPoints(), getComment(), getSubGrades());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("points", points).add("comment", comment).toString();
	}

}
