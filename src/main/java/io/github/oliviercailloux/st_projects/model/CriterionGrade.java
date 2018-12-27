package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

@JsonbPropertyOrder({ "criterion", "points", "comment" })
public class CriterionGrade<T extends GradeCriterion> {
	private T criterion;

	public T getCriterion() {
		return criterion;
	}

	public double getPoints() {
		return points;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof CriterionGrade)) {
			return false;
		}
		final CriterionGrade<?> s2 = (CriterionGrade<?>) o2;
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

	public static <T extends GradeCriterion> CriterionGrade<T> zero(T criterion) {
		return new CriterionGrade<>(criterion, 0d, "");
	}

	@JsonbCreator
	public static <T extends GradeCriterion> CriterionGrade<T> of(@JsonbProperty("criterion") T criterion,
			@JsonbProperty("points") double points, @JsonbProperty("comment") String comment) {
		final CriterionGrade<T> g = new CriterionGrade<>(criterion, points, comment);
		return g;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CriterionGrade.class);

	/**
	 * May be negative (penalty); may be greater than maxPoints of the corresponding
	 * criterion (bonus).
	 */
	private double points;
	private String comment;

	private CriterionGrade(T criterion, double points, String comment) {
		this.criterion = requireNonNull(criterion);
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = requireNonNull(comment);
	}

	public static <T extends GradeCriterion> CriterionGrade<T> zero(T criterion, String comment) {
		return new CriterionGrade<>(criterion, 0d, comment);
	}

	public static <T extends GradeCriterion> CriterionGrade<T> max(T criterion) {
		return new CriterionGrade<>(criterion, criterion.getMaxPoints(), "");
	}
}
