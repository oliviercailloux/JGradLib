package io.github.oliviercailloux.teaching_examples;

import io.github.oliviercailloux.g421.CyclicDiceRoller;

public class MyCyclic implements CyclicDiceRoller {

	public static void main(String[] args) {
		CyclicDiceRoller c = new MyCyclic();
		c.roll();
		c.setResult(4, 3, 2);
		c.roll();
		System.out.println("First: " + c.first());
		c.roll();
		System.out.println("First: " + c.first());
		c.roll();
		System.out.println("First: " + c.first());
		c.roll();
		System.out.println("First: " + c.first());
	}

	private int firstDie;
	private int secondDie;
	private int thirdDie;

	private int resultFirstDie;
	private int resultSecondDie;
	private int resultThirdDie;

	public MyCyclic() {
		firstDie = 0;
		secondDie = 0;
		thirdDie = 0;

		resultFirstDie = 1;
		resultSecondDie = 1;
		resultThirdDie = 1;
	}

	@Override
	public void roll() {
		firstDie = resultFirstDie;
		secondDie = resultSecondDie;
		thirdDie = resultThirdDie;
		shift();
	}

	private void shift() {
		int originalFirstDie = resultFirstDie;
		resultFirstDie = resultThirdDie;
		resultThirdDie = resultSecondDie;
		resultSecondDie = originalFirstDie;
	}

	@Override
	public int first() {
		if (firstDie == 0) {
			throw new IllegalStateException();
		}
		return firstDie;
	}

	@Override
	public int second() {
		if (secondDie == 0) {
			throw new IllegalStateException();
		}
		return secondDie;
	}

	@Override
	public int third() {
		if (thirdDie == 0) {
			throw new IllegalStateException();
		}
		return thirdDie;
	}

	@Override
	public void setResult(int firstDie, int secondDie, int thirdDie) {
		checkDie(firstDie);
		checkDie(secondDie);
		checkDie(thirdDie);

		resultFirstDie = firstDie;
		resultSecondDie = secondDie;
		resultThirdDie = thirdDie;
	}

	private void checkDie(int die) {
		if (die < 1 || die > 6) {
			throw new IllegalArgumentException();
		}
	}

}
