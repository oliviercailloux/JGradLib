package io.github.oliviercailloux.samples.coffee;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.math.RoundingMode;

import com.google.common.math.DoubleMath;

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
    return MAX_STRENGTH;
  }

  @Override
  public int getTimeForCoffee(double strength) {
    checkArgument(strength <= getMaxStrength());
    checkArgument(0d <= strength);
    return strength == 0d ? 0 : 140 + DoubleMath.roundToInt(2 * strength, RoundingMode.HALF_UP);
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
  public double getPower() {
    return POWER;
  }

  @Override
  public double getEnergySpent() throws IllegalStateException {
    checkState(countProduced > 0);
    verify(lastStrength >= 0d);
    return lastStrength == 0d ? 0d : 15d + POWER * getTimeForCoffee(lastStrength) / 3600d;
  }

}
