package io.github.oliviercailloux.grade;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Booleans;

/**
 *
 * A special kind of Grade: one that has no sub-grades.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "criterion", "points", "comment" })
public class Mark extends Grade {
	private Mark(Criterion criterion, double points, String comment) {
		super(criterion, points, comment);
	}

	public static Mark proportional(Criterion criterion, boolean firstTest, boolean... tests) {
		final int nbTests = 1 + tests.length;
		final int nbOk = (firstTest ? 1 : 0) + Booleans.countTrue(tests);
		return proportional(criterion, nbOk, nbTests);
	}

	public static Mark proportional(Criterion criterion, int nbOk, int nbTests) {
		return proportional(criterion, nbOk, nbTests, "nbOk (" + nbOk + ") / nbTests (" + nbTests + ")");
	}

	public static Mark proportional(Criterion criterion, int nbOk, int nbTests, String comment) {
		final double weightOk = (double) nbOk / (double) nbTests;
		final double weightKo = 1d - weightOk;
		final double pts = criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk;
		LOGGER.debug("For {}, obtained proportion of {} right and {} wrong, points are {}.", criterion, weightOk,
				weightKo, pts);
		return Mark.of(criterion, pts, comment);
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

	public static Mark min(Criterion criterion, String comment) {
		return new Mark(criterion, criterion.getMinPoints(), comment);
	}

	public static Mark max(Criterion criterion) {
		return new Mark(criterion, criterion.getMaxPoints(), "");
	}
}
