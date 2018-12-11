package io.github.oliviercailloux.st_projects.ex2;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import com.google.common.base.MoreObjects;

@JsonbPropertyOrder({ "criterion", "points", "comment" })
public class SingleGrade {
	private GradeCriterion criterion;

	public GradeCriterion getCriterion() {
		return criterion;
	}

	public double getPoints() {
		return points;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof SingleGrade)) {
			return false;
		}
		final SingleGrade s2 = (SingleGrade) o2;
		return criterion.equals(s2.criterion) && points == s2.points && comment.equals(s2.comment);
	}

	@Override
	public int hashCode() {
		return Objects.hash(criterion, points, comment);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("criterion", criterion).add("points", points)
				.add("comment", comment).toString();
	}

	public String getComment() {
		return comment;
	}

	public static SingleGrade zero(GradeCriterion criterion) {
		return new SingleGrade(criterion, 0d, "");
	}

	@JsonbCreator
	public static SingleGrade of(@JsonbProperty("criterion") GradeCriterion criterion,
			@JsonbProperty("points") double points, @JsonbProperty("comment") String comment) {
		return new SingleGrade(criterion, points, comment);
	}

	/**
	 * May be negative (penalty). points ≤ maxPoints of the corresponding criterion.
	 */
	private double points;
	private String comment;

	private SingleGrade(GradeCriterion criterion, double points, String comment) {
		this.criterion = requireNonNull(criterion);
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = requireNonNull(comment);
	}

	public static SingleGrade zero(GradeCriterion criterion, String comment) {
		return new SingleGrade(criterion, 0d, comment);
	}

	public static SingleGrade max(GradeCriterion criterion) {
		return new SingleGrade(criterion, criterion.getMaxPoints(), "");
	}
}
