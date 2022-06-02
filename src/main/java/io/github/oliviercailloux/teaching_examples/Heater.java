package io.github.oliviercailloux.teaching_examples;

public class Heater implements CostlyThingWithUnit {
	private int power;
	private double price;
	private String identifier;

	public Heater() {
		power = 1000;
		price = 100;
	}

	public Heater(int power) {
		this.power = power;
		price = power / 10d;
	}

	public String getIdentifier() {
		return identifier;
	}

	public int power() {
		return power;
	}

	@Override
	public double pricePerHour() {
		double kW = kW();
		return kW * 0.10d;
	}

	private double kW() {
		return power / 1000d;
	}

	@Override
	public double price() {
		return price;
	}

	@Override
	public String unit() {
		return "Euros";
	}

	@Override
	public String toString() {
		return "Heater with power " + power;
	}
}
