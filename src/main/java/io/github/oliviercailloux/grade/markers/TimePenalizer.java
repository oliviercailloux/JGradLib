package io.github.oliviercailloux.grade.markers;

import java.time.Duration;

public interface TimePenalizer {
	public static TimePenalizer linear(double lostPerSecond) {
		return new LinearTimePenalizer(lostPerSecond);
	}

	/**
	 * @return zero if tardiness is negative or null, one if tardiness is greater than or equal to
	 *         {@link #getOvertimeBound()}.
	 */
	public double penalty(Duration tardiness);

	/**
	 * @return a strictly positive duration such that the penalty is maximal at that point.
	 */
	public Duration getTardinessBound();
}
