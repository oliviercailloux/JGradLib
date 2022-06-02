package io.github.oliviercailloux.g421;

/**
 * A “cheating” dice roller which produces pre-established results.
 * <p>
 * When {@link #setResult(int, int, int)} is called, it determines the next
 * result that will be obtained by {@link #roll()}. In other words, the next
 * call to {@link #roll()} will result in the state represented by the arguments
 * of {@link #setResult(int, int, int)}. Rolling once more reuses these same
 * numbers but rotating them by one position, the old result of the first die
 * becoming the result of the second die, the old result of the second die
 * becoming the result of the third die, and the old result of the third die
 * becoming the result of the first die. Rolling again repeats the same shifting
 * algorithm.
 * <p>
 * For example, if {@link #setResult(int, int, int)} is called with the result
 * 5, 5, 1, then the next time {@link #roll()} is called, this roller will roll
 * a five, a five and a one on the three dice (in order); then the next time
 * {@link #roll()} is called, this roller will roll a one, a five and a five on
 * the three dice (in order); then the next time {@link #roll()} is called, this
 * roller will roll a five, a one and a five on the three dice (in order); then
 * the next time {@link #roll()} is called, this roller will go back to the
 * initial state and roll a five, a five and a one on the three dice (in order);
 * and so on.
 * <p>
 * While no result has been set yet, this roller rolls only ones (thus obtains
 * 1, 1, 1 after each roll).
 */
public interface CyclicDiceRoller extends DiceRoller {

	/**
	 * Establishes the results that will be obtained by this roller from the next
	 * roll onwards (by cyclic rotation of the given numbers), until this method is
	 * called again.
	 *
	 * @param firstDie  the result to be obtained on the first die on the next roll
	 * @param secondDie the result to be obtained on the second die on the next roll
	 * @param thirdDie  the result to be obtained on the third die on the next roll
	 * @throws IllegalArgumentException iff one of the dice is a number smaller than
	 *                                  1 or greater than 6
	 */
	void setResult(int firstDie, int secondDie, int thirdDie);
}
