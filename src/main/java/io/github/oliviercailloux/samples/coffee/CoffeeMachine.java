package io.github.oliviercailloux.samples.coffee;

/**
 * <p>
 * A machine that produces coffee of various strength. A strength is a
 * non-negative value. A value of zero for the strength represents no drink at
 * all.
 * </p>
 *
 * <p>
 * Note that this interface is very naïve. It is in general impossible to
 * associate a quantity of energy spent for producing a given coffee, for
 * example, because it depends on how long the machine stays on after having
 * served the coffee.
 * </p>
 * <p>
 * All coffee machines must have a parameter-less static factory method named
 * <code>newInstance</code> to create instances.
 * </p>
 *
 * @author Olivier Cailloux
 *
 */
public interface CoffeeMachine {
	/**
	 * Returns the maximal strength that this machine is able to produce.
	 *
	 * @return a non-negative, finite value.
	 */
	public double getMaxStrength();

	/**
	 * Returns the time in seconds required for a coffee.
	 *
	 * @param strength a non-negative value, at most the {@link #getMaxStrength()}
	 *                 value.
	 * @return a non-negative value, zero iff the given strength is zero.
	 */
	public int getTimeForCoffee(double strength);

	/**
	 * <p>
	 * Produces a coffee of the given strength.
	 * </p>
	 * <p>
	 * (This increments the number of coffees produced, it does not wait for
	 * anything and thus does not
	 * <a href="https://en.wikipedia.org/wiki/Blocking_(computing)">block</a> the
	 * program.)
	 * </p>
	 *
	 * @param strength a non-negative value, at most the {@link #getMaxStrength()}
	 *                 value.
	 */
	public void produceCoffee(double strength);

	/**
	 * Returns the number of coffees that this machine has produced since the
	 * beginning of its life. Coffees of strength zero are included.
	 *
	 * @return a non-negative number.
	 */
	public int getNumberOfCoffeesProduced();

	/**
	 * Returns the energy spent, in watt times hours, for producing the last coffee.
	 *
	 * @return a non-negative value, zero iff the last coffee produced had strength
	 *         zero.
	 * @throws IllegalStateException iff no coffee has ever been produced by this
	 *                               machine.
	 * @see #produceCoffee(double)
	 */
	public double getEnergySpent() throws IllegalStateException;
}
