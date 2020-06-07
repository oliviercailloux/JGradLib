package io.github.oliviercailloux.samples.scorers;

@FunctionalInterface
public interface ScoreKeeper {
	public int getCurrentScore();
}
