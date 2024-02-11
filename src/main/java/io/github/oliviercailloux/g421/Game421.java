package io.github.oliviercailloux.g421;

/**
 * A simulator of a very simple variant of the <a href="https://fr.wikipedia.org/wiki/421_(jeu)">421
 * dice game</a>. In this variant, we simply attempt to roll the dice in hope of obtaining 421,
 * meaning, 4 on the first die, 2 on the second die and 1 on the third die.
 */
public interface Game421 {
	/**
	 * Sets the dice roller to use for subsequent tries of this game.
	 *
	 * @param roller the roller.
	 */
	public void setRoller(DiceRoller roller);

	/**
	 * Attempts to obtain 421 by throwing the (triplet of) dice at most the indicated number of times.
	 *
	 * @param nbTries the number of attempts after which to stop trying, if 421 has not been obtained
	 *        yet.
	 * @return whether 421 was obtained using that number of attempts or lower.
	 * @throws IllegalArgumentException iff {@code nbTries} is negative
	 * @throws IllegalStateException iff no dice roller has been set yet
	 */
	public boolean tryGet421(int nbTries) throws IllegalArgumentException, IllegalStateException;
}
