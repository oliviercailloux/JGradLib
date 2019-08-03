package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

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

	/**
	 * TODO replace with Booleans.countTrue at usage site; change for an AndGrade
	 * when involves several conditions.
	 */
	public static Mark ifPasses(boolean passes) {
		return passes ? one() : zero();
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
