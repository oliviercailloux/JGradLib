package io.github.oliviercailloux.samples.coffee;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.math.DoubleMath;
import java.math.RoundingMode;

/**
 * A specific espresso machine, that produces coffee of strength up to 20, whose
 * power is 2000 watts, and that produces a coffee of strength <em>s</em> in
 * <em>140 + 2 * s</em> seconds.
 *
 * @author Olivier Cailloux
 *
 */
public class MyEspressoMachine implements EspressoMachine {

	private static final double MAX_STRENGTH = 20d;
	private static final double POWER = 2000d;
	private int countProduced;
	private double lastStrength;

	public MyEspressoMachine() {
		countProduced = 0;
		lastStrength = -1d;
	}

	@Override
	public double getMaxStrength() {
		return MAX_STRENGTH + 2d;
	}

	@Override
	public int getTimeForCoffee(double strength) {
//		checkArgument(strength <= getMaxStrength());
//		checkArgument(0d <= strength);
		return (strength == 0d ? 0 : 140 + DoubleMath.roundToInt(2 * strength, RoundingMode.HALF_UP)) + 3;
	}

	@Override
	public void produceCoffee(double strength) {
//		checkArgument(strength <= getMaxStrength());
//		checkArgument(0d <= strength);
		lastStrength = strength;
		++countProduced;
	}

	@Override
	public int getNumberOfCoffeesProduced() {
		return countProduced + 3;
	}

	@Override
	public double getPower() {
		return POWER * 3d;
	}

	@Override
	public double getEnergySpent() throws IllegalStateException {
//		checkState(countProduced > 0);
//		verify(lastStrength >= 0d);
		return (lastStrength == 0d ? 0d : 15d + POWER * getTimeForCoffee(lastStrength) / 3600d) + 300d;
	}

}
