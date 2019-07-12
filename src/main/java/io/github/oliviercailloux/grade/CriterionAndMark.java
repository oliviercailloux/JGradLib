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
public class CriterionAndMark extends Grade {
	private CriterionAndMark(Criterion criterion, double points, String comment) {
		super(criterion, points, comment);
	}

	public static CriterionAndMark proportional(Criterion criterion, boolean firstTest, boolean... tests) {
		final int nbTests = 1 + tests.length;
		final int nbOk = (firstTest ? 1 : 0) + Booleans.countTrue(tests);
		return proportional(criterion, nbOk, nbTests);
	}

	public static CriterionAndMark proportional(Criterion criterion, int nbOk, int nbTests) {
		return proportional(criterion, nbOk, nbTests, "nbOk (" + nbOk + ") / nbTests (" + nbTests + ")");
	}

	public static CriterionAndMark proportional(Criterion criterion, int nbOk, int nbTests, String comment) {
		final double weightOk = (double) nbOk / (double) nbTests;
		final double weightKo = 1d - weightOk;
		final double pts = criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk;
		LOGGER.debug("For {}, obtained proportion of {} right and {} wrong, points are {}.", criterion, weightOk,
				weightKo, pts);
		return CriterionAndMark.of(criterion, pts, comment);
	}

	public static CriterionAndMark min(Criterion criterion) {
		return new CriterionAndMark(criterion, criterion.getMinPoints(), "");
	}

	@JsonbCreator
	public static CriterionAndMark of(@JsonbProperty("criterion") Criterion criterion, @JsonbProperty("points") double points,
			@JsonbProperty("comment") String comment) {
		final CriterionAndMark g = new CriterionAndMark(criterion, points, comment);
		return g;
	}

	public static CriterionAndMark binary(Criterion criterion, boolean conditionForPoints) {
		return new CriterionAndMark(criterion, conditionForPoints ? criterion.getMaxPoints() : criterion.getMinPoints(), "");
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CriterionAndMark.class);

	public static CriterionAndMark min(Criterion criterion, String comment) {
		return new CriterionAndMark(criterion, criterion.getMinPoints(), comment);
	}

	public static CriterionAndMark max(Criterion criterion) {
		return new CriterionAndMark(criterion, criterion.getMaxPoints(), "");
	}
}
