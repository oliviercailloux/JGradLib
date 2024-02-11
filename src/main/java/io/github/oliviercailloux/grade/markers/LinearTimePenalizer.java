package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.math.DoubleMath;
import java.math.RoundingMode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LinearTimePenalizer implements TimePenalizer {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(TimePenalizer.class);

  private final double lostPerSecond;

  LinearTimePenalizer(double lostPerSecond) {
    checkArgument(Double.isFinite(lostPerSecond));
    checkArgument(Double.compare(0d, lostPerSecond) < 0);
    this.lostPerSecond = lostPerSecond;
  }

  @Override
  public double penalty(Duration tardiness) {
    LOGGER.debug("Tardiness: {}.", tardiness);
    final long secondsLate = Math.max(0, tardiness.toSeconds());
    final double penalty = Math.min(1d, lostPerSecond * secondsLate);
    return penalty;
  }

  @Override
  public Duration getTardinessBound() {
    return Duration.ofSeconds(DoubleMath.roundToLong(1d / lostPerSecond, RoundingMode.UP));
  }
}
