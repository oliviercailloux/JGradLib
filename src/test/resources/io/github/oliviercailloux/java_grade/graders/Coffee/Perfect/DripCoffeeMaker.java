package io.github.oliviercailloux.samples.coffee;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

/**
 * A <a href=
 * "https://www.startpage.com/sp/search?query=drip+coffee+maker&cat=pics">drip
 * coffee maker</a>. It uses a specific brand of coffee, which makes it able to
 * produce coffee of any strength from 0 to 10. It takes a constant time of 2
 * minutes to produce coffee (of strength higher than zero). The energy required
 * for producing a coffee (of strength higher than zero) is an estimated 83 watt
 * hours.
 *
 * @author Olivier Cailloux
 *
 */
public class DripCoffeeMaker implements CoffeeMachine {

	private static final double MAX_STRENGTH = 10d;
	private static final int SECONDS_FOR_COFFEE = 120;
	private static final double WATT_HOURS_FOR_COFFEE = 83d;
	private int countProduced;
	/**
	 * In [0d, {@link #MAX_STRENGTH}]
	 */
	private double lastStrength;

	public DripCoffeeMaker() {
		countProduced = 0;
		lastStrength = -1d;
	}

	@Override
	public double getMaxStrength() {
		return MAX_STRENGTH;
	}

	@Override
	public int getTimeForCoffee(double strength) {
		checkArgument(strength <= getMaxStrength());
		checkArgument(0d <= strength);
		return strength == 0d ? 0 : SECONDS_FOR_COFFEE;
	}

	@Override
	public void produceCoffee(double strength) {
		checkArgument(strength <= getMaxStrength());
		checkArgument(0d <= strength);
		lastStrength = strength;
		++countProduced;
	}

	@Override
	public int getNumberOfCoffeesProduced() {
		return countProduced;
	}

	@Override
	public double getEnergySpent() throws IllegalStateException {
		checkState(countProduced > 0);
		verify(lastStrength >= 0d);
		return lastStrength == 0d ? 0d : WATT_HOURS_FOR_COFFEE;
	}

}
