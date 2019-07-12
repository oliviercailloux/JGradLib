package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import com.google.common.collect.ImmutableMap;

public class Mark implements IGrade {

	public static Mark of(double points, String comment) {
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

}
