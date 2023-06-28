package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradePenalizer implements GradeModifier {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradePenalizer.class);

	public static final Criterion C_MAIN = Criterion.given("Main");

	public static final Criterion C_LATENESS = Criterion.given("Timing");

	public static GradePenalizer using(LinearPenalizer penalizer, Instant deadline) {
		return new GradePenalizer(penalizer::getFractionRemaining, deadline);
	}

	public static GradePenalizer usingFunction(Function<Duration, Mark> penalizer, Instant deadline) {
		return new GradePenalizer(penalizer, deadline);
	}

	private final Function<Duration, Mark> penalizer;

	private final Instant deadline;

	private GradePenalizer(Function<Duration, Mark> penalizer, Instant deadline) {
		this.penalizer = checkNotNull(penalizer);
		this.deadline = checkNotNull(deadline);
	}

	@Override
	public MarksTree modify(MarksTree original, Instant timeCap) {
		final Duration lateness = Duration.between(deadline, timeCap);
		final Mark remaining = penalizer.apply(lateness);
		LOGGER.debug("Lateness from {} to {} equal to {}; remaining {}.", deadline, timeCap, lateness, remaining);

		return MarksTree.composite(ImmutableMap.of(C_MAIN, original, C_LATENESS, remaining));
	}
}
