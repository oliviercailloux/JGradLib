package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitFullContext;

public class TimeMarker implements CriterionMarker {
	private final GitFullContext context;
	private Instant deadline;
	private double maxGrade;
	private Criterion criterion;

	public static TimeMarker given(Criterion criterion, GitFullContext contextSupplier, Instant deadline,
			double maxGrade) {
		return new TimeMarker(criterion, contextSupplier, deadline, maxGrade);
	}

	private TimeMarker(Criterion criterion, GitFullContext contextSupplier, Instant deadline, double maxGrade) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(contextSupplier);
		this.deadline = requireNonNull(deadline);
		this.maxGrade = maxGrade;
		checkArgument(Double.isFinite(maxGrade));
	}

	@Override
	public Mark mark() {
		final Client client = context.getClient();

		if (!client.hasContentCached() || !client.getDefaultRevSpec().isPresent()) {
			return Mark.of(criterion, 0d, "");
		}

		final Instant submitted = context.getSubmittedTime();

		final Duration tardiness = Duration.between(deadline, submitted).minusMinutes(2);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		final Mark grade;
		if (!tardiness.isNegative()) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final long hoursLate = tardiness.toHours() + 1;
			grade = Mark.of(criterion, -3d / 20d * maxGrade * hoursLate,
					"Last event after deadline: " + ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", "
							+ hoursLate + " hours late.");
		} else {
			grade = Mark.of(criterion, 0d, "");
		}
		return grade;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeMarker.class);
}