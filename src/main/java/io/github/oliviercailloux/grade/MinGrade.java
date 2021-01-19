package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

public class MinGrade implements IGrade {
	public static MinGrade given(Map<Criterion, ? extends IGrade> subGrades) {
		return new MinGrade(subGrades);
	}

	/**
	 * Not empty.
	 */
	private final ImmutableMap<Criterion, IGrade> subGrades;

	protected MinGrade(Map<Criterion, ? extends IGrade> subGrades) {
		checkNotNull(subGrades);
		checkArgument(!subGrades.isEmpty());
		this.subGrades = ImmutableMap.copyOf(subGrades);
	}

	@Override
	public double getPoints() {
		return subGrades.values().stream().map(IGrade::getPoints).mapToDouble(Double::doubleValue).min().getAsDouble();
	}

	@Override
	public String getComment() {
		return "Using the minimal value among the sub-grades";
	}

	@Override
	public ImmutableMap<Criterion, IGrade> getSubGrades() {
		return subGrades;
	}

	@Override
	public IGrade limitedDepth(int depth) {
		checkArgument(depth >= 0);
		if (depth == 0) {
			return Mark.given(getPoints(), getComment());
		}
		return MinGrade.given(subGrades.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> subGrades.get(c).limitedDepth(depth - 1))));
	}

	@Override
	public MinGrade withSubGrade(Criterion criterion, IGrade newSubGrade) {
		return new MinGrade(GradeUtils.withUpdatedEntry(subGrades, criterion, newSubGrade));
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
		return MoreObjects.toStringHelper(this).add("points", getPoints()).add("comment", getComment())
				.add("subGrades", getSubGrades()).toString();
	}

}
