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
public class Mark {
	private final Criterion criterion;

	public Criterion getCriterion() {
		return criterion;
	}

	public double getPoints() {
		return points;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Mark)) {
			return false;
		}
		final Mark s2 = (Mark) o2;
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

	public static Mark min(Criterion criterion) {
		return new Mark(criterion, criterion.getMinPoints(), "");
	}

	@JsonbCreator
	public static Mark of(@JsonbProperty("criterion") Criterion criterion, @JsonbProperty("points") double points,
			@JsonbProperty("comment") String comment) {
		final Mark g = new Mark(criterion, points, comment);
		return g;
	}

	public static Mark binary(Criterion criterion, boolean conditionForPoints) {
		return new Mark(criterion, conditionForPoints ? criterion.getMaxPoints() : criterion.getMinPoints(), "");
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Mark.class);

	/**
	 * May be negative (penalty); may be greater than maxPoints of the corresponding
	 * criterion (bonus).
	 */
	private final double points;
	private final String comment;

	private Mark(Criterion criterion, double points, String comment) {
		this.criterion = requireNonNull(criterion);
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = requireNonNull(comment);
	}

	public static Mark min(Criterion criterion, String comment) {
		return new Mark(criterion, criterion.getMinPoints(), comment);
	}

	public static Mark max(Criterion criterion) {
		return new Mark(criterion, criterion.getMaxPoints(), "");
	}
}
