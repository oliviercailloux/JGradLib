package io.github.oliviercailloux.g421;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.util.Random;

public class RandomDiceRoller implements DiceRoller {

  private final Random random;
  private ImmutableList<Integer> rolled;

  public RandomDiceRoller() {
    random = new Random();
    rolled = null;
  }

  @Override
  public void roll() {
    rolled = random.ints(1, 7).limit(3).boxed().collect(ImmutableList.toImmutableList());
  }

  @Override
  public int first() throws IllegalStateException {
    return iTh(0);
  }

  @Override
  public int second() throws IllegalStateException {
    return iTh(1);
  }

  @Override
  public int third() throws IllegalStateException {
    return iTh(2);
  }

  private int iTh(int i) {
    checkState(rolled != null);
    return rolled.get(i);
  }
}
