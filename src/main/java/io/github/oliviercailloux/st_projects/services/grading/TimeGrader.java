package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GradeCriterion;

public class TimeGrader<T extends GradeCriterion> {
	private final ContextInitializer context;
	private Instant deadline;
	private double maxGrade;
	private T criterion;

	public static <T extends GradeCriterion> TimeGrader<T> given(T criterion, ContextInitializer init, Instant deadline,
			double maxGrade) {
		return new TimeGrader<>(criterion, init, deadline, maxGrade);
	}

	private TimeGrader(T criterion, ContextInitializer init, Instant deadline, double maxGrade) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(init);
		this.deadline = requireNonNull(deadline);
		this.maxGrade = maxGrade;
		checkArgument(Double.isFinite(maxGrade));
	}

	public CriterionGrade<T> grade() {
		final Client client = context.getClient();

		if (!client.hasContentCached() || !client.getDefaultRevSpec().isPresent()) {
			return CriterionGrade.of(criterion, 0d, "");
		}

		final Instant submitted = context.getSubmittedTime();

		final Duration tardiness = Duration.between(deadline, submitted).minusMinutes(2);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		final CriterionGrade<T> grade;
		if (!tardiness.isNegative()) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final long hoursLate = tardiness.toHours() + 1;
			grade = CriterionGrade.of(criterion, -3d / 20d * maxGrade * hoursLate,
					"Last event after deadline: " + ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", "
							+ hoursLate + " hours late.");
		} else {
			grade = CriterionGrade.of(criterion, 0d, "");
		}
		return grade;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeGrader.class);
}
