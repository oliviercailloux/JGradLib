package io.github.oliviercailloux.samples.scorers;

public interface ScoreManager extends ScoreModifier {
	public ScoreKeeper getScoreMultiplier();

	public void addListener(ScoreListener listener);
}
