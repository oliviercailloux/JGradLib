package io.github.oliviercailloux.teaching_examples;

import java.io.IOException;

public class CostComputer {
	public static void main(String[] args) throws Exception {
		GenericHeater<String> g = new GenericHeater<>();

		GenericHeater<Integer> g2 = new GenericHeater<>();

		String id1 = g.getIdentifier();

		Integer id2 = g2.getIdentifier();

		CostlyThing defaultHeater;
		defaultHeater = new Heater();
		defaultHeater.toString();

		defaultHeater = new Elephant(true);

		Heater otherHeater = new Heater(2000);

		Elephant greedyElephant = new Elephant(true);

		boolean a = true;

		if (a) {
			throw new IOException();
		}

		CostManager.cost();
		CostManager.cost(defaultHeater);
		CostManager.cost(defaultHeater, otherHeater);
		CostManager.cost(defaultHeater, greedyElephant);
	}

}
