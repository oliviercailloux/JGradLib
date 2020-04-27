package io.github.oliviercailloux.samples.coffee;

public interface EspressoMachine extends CoffeeMachine {
	/**
	 * Returns the number of watts that this machine consumes while producing a
	 * coffee.
	 *
	 * @return a strictly positive finite number.
	 */
	public double getPower();

	/**
	 * {@inheritDoc} This quantity, if not zero, is computed as the power of this
	 * machine times the time spent for producing the last coffee (converted for the
	 * right units), plus an arbitrary amount of 15 watt hours for the start and
	 * shutdown procedures.
	 */
	@Override
	double getEnergySpent() throws IllegalStateException;
}
