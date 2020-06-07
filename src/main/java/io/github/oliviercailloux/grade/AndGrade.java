package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;

public class AndGrade extends MinGrade implements IGrade {

	public static AndGrade given(Criterion c1, boolean cond1, Criterion c2, boolean cond2, Criterion c3,
			boolean cond3) {
		return new AndGrade(ImmutableMap.of(c1, Mark.binary(cond1), c2, Mark.binary(cond2), c3, Mark.binary(cond3)));
	}

	public static AndGrade given(Map<Criterion, ? extends IGrade> subGrades) {
		return new AndGrade(subGrades);
	}

	protected AndGrade(Map<Criterion, ? extends IGrade> subGrades) {
		super(subGrades);
		checkArgument(subGrades.values().stream().map(IGrade::getPoints).allMatch((p) -> p == 0d || p == 1d));
	}

	@Override
	public String getComment() {
		return "AND: Success iff all the sub-grades are successes";
	}

	/**
	 * @return zero or one.
	 */
	@Override
	public double getPoints() {
		final double points = super.getPoints();
		Verify.verify(points == 0d || points == 1d);
		return points;
	}

	@Override
	public IGrade limitedDepth(int depth) {
		checkArgument(depth >= 0);
		if (depth == 0) {
			return Mark.given(getPoints(), getComment());
		}
		return AndGrade.given(getSubGrades().keySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> getSubGrades().get(c).limitedDepth(depth - 1))));
	}
}
