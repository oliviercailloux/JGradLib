package io.github.oliviercailloux.g421;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class PredictedDiceRoller implements DiceRoller {

  private final ImmutableList<ImmutableList<Integer>> rolls;
  private int current;

  public PredictedDiceRoller(List<? extends List<Integer>> rolls) {
    this.rolls = rolls.stream().map(ImmutableList::copyOf).collect(ImmutableList.toImmutableList());
    current = -1;
  }

  @Override
  public void roll() {
    ++current;
  }

  @Override
  public int first() throws IllegalStateException {
    return ith(0);
  }

  @Override
  public int second() throws IllegalStateException {
    return ith(1);
  }

  @Override
  public int third() throws IllegalStateException {
    return ith(2);
  }

  private int ith(int i) {
    return rolls.get(current).get(i);
  }
}
