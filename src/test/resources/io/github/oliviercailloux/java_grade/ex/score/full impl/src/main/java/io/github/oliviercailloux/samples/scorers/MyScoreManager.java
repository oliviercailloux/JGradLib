package io.github.oliviercailloux.samples.scorers;

import java.util.ArrayList;
import java.util.List;

public class MyScoreManager implements ScoreManager {
	private int score;
	private final List<ScoreListener> listeners;
	private int multiplier;

	/**
	 * <p>
	 * This method, with the given declaration, <b>must</b> be present.
	 * </p>
	 *
	 */
	public static MyScoreManager newInstance() {
		return new MyScoreManager();
	}

	private MyScoreManager() {
		score = 10;
		listeners = new ArrayList<>();
		multiplier = 1;
	}

	@Override
	public void incrementScore() {
		++score;
		for (ScoreListener listener : listeners) {
			listener.scoreIncremented();
		}
	}

	@Override
	public int getCurrentScore() {
		return score;
	}

	@Override
	public ScoreKeeper getScoreMultiplier() {
		final int currentMultiplier = multiplier;
		++multiplier;
		return () -> getCurrentScore() * currentMultiplier;
	}

	@Override
	public void addListener(ScoreListener listener) {
		listeners.add(listener);
	}

}
