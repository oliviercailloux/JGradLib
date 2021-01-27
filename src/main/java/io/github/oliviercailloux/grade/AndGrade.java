package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class AndGrade extends MinGrade implements IGrade {

	private static final String DEFAULT_COMMENT = "AND: Success iff all the sub-grades are successes";

	public static AndGrade given(Criterion c1, boolean cond1, Criterion c2, boolean cond2, Criterion c3,
			boolean cond3) {
		return new AndGrade(ImmutableMap.of(c1, Mark.binary(cond1), c2, Mark.binary(cond2), c3, Mark.binary(cond3)),
				DEFAULT_COMMENT);
	}

	public static AndGrade given(Map<Criterion, ? extends IGrade> subGrades) {
		return new AndGrade(subGrades, DEFAULT_COMMENT);
	}

	protected AndGrade(Map<Criterion, ? extends IGrade> subGrades, String comment) {
		super(subGrades, comment);
		checkArgument(subGrades.values().stream().map(IGrade::getPoints).allMatch((p) -> p == 0d || p == 1d));
	}

	/**
	 * @return zero or one.
	 */
	@Override
	public double getPoints() {
		final double points = super.getPoints();
		verify(points == 0d || points == 1d);
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

	@Override
	public IGrade withComment(String newComment) {
		return new AndGrade(getSubGrades(), newComment);
	}

	@Override
	public AndGrade withSubGrade(Criterion criterion, IGrade newSubGrade) {
		return new AndGrade(GradeUtils.withUpdatedEntry(getSubGrades(), criterion, newSubGrade), getComment());
	}
}
