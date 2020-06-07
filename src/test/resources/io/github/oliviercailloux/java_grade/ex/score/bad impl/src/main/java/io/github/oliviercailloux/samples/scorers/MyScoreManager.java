package io.github.oliviercailloux.samples.scorers;

public class MyScoreManager implements ScoreManager {
	/**
	 * <p>
	 * This method, with the given declaration, <b>must</b> be present.
	 * </p>
	 *
	 */
	public static MyScoreManager newInstance() {
		return new MyScoreManager();
	}

	@Override
	public void incrementScore() {
	}

	@Override
	public int getCurrentScore() {
		return 0;
	}

	@Override
	public ScoreKeeper getScoreMultiplier() {
		return null;
	}

	@Override
	public void addListener(ScoreListener listener) {
	}

}
