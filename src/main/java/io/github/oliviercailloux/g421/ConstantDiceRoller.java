package io.github.oliviercailloux.g421;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class ConstantDiceRoller implements DiceRoller {

	private final ImmutableList<Integer> rolled;
	private boolean hasBeenRolled;

	public ConstantDiceRoller(List<Integer> rolled) {
		this.rolled = ImmutableList.copyOf(rolled);
		checkArgument(rolled.size() == 3);
		hasBeenRolled = false;
	}

	@Override
	public void roll() {
		hasBeenRolled = true;
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
		checkState(hasBeenRolled);
		return rolled.get(i);
	}
}
