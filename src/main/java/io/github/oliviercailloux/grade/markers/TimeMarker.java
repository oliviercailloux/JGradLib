package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.context.GitFullContext;

class TimeMarker {
	private final GitFullContext context;
	private Instant deadline;
	private CriterionAndPoints criterion;

	public TimeMarker(CriterionAndPoints criterion, GitFullContext contextSupplier, Instant deadline,
			Function<Duration, Double> penalizer) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(contextSupplier);
		this.deadline = requireNonNull(deadline);
		this.penalizer = requireNonNull(penalizer);
	}

	public CriterionAndMark mark() {
		final Client client = context.getClient();

		if (!client.hasContentCached() || !context.getMainCommit().isPresent()) {
			return CriterionAndMark.of(criterion, 0d, "");
		}

		final Instant submitted = context.getSubmittedTime();

		final Instant tooLate = deadline.plus(Duration.ofSeconds(1));
		final Duration tardiness = Duration.between(tooLate, submitted);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		final CriterionAndMark grade;
		if (tardiness.compareTo(Duration.ZERO) > 0) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final double penalty = penalizer.apply(tardiness);
			checkState(penalty <= 0d);
			grade = CriterionAndMark.of(criterion, penalty, "Last event after deadline: "
					+ ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", " + tardiness + " late.");
		} else {
			grade = CriterionAndMark.max(criterion);
		}
		return grade;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeMarker.class);
	private final Function<Duration, Double> penalizer;
}
