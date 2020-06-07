package io.github.oliviercailloux.java_grade.ex;

import io.github.oliviercailloux.samples.scorers.ScoreListener;

/**
 *
 * Just keeps an integer value that counts how often it has been called, for
 * test purposes.
 *
 */
public class ScoreIntegerListener implements ScoreListener {
	private int countCalled;

	public static ScoreIntegerListener newInstance() {
		return new ScoreIntegerListener();
	}

	private ScoreIntegerListener() {
		countCalled = 0;
	}

	@Override
	public void scoreIncremented() {
		++countCalled;
	}

	public int getCountCalled() {
		return countCalled;
	}
}
