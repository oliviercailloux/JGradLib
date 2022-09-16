package io.github.oliviercailloux.teaching_examples;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Heater implements CostlyThingWithUnit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Heater.class);

	private int power;
	private double price;
	private String identifier;

	public static Heater standard(int power, String identifier) {
		return new Heater(power, power / 10d, identifier);
	}

	public static Heater LG() {
		return new Heater(2000, 180d, "LG");
	}

	@Override
	public boolean equals(Object o2) {
		if (o2 instanceof Heater) {
			Heater h2 = (Heater) o2;
			boolean equalPower = this.power == h2.power;
			boolean equalPrice = this.price == h2.price;
			boolean equalIds = this.identifier == h2.identifier;
			return equalPower && equalPrice && equalIds;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(power, price, identifier);
	}

	private Heater(int power, double price, String identifier) {
		LOGGER.info("Creating heater {}: {}, {}.", identifier, power, price);
		this.power = power;
		this.price = price;
		this.identifier = identifier;
//		price = power / 10d;
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
