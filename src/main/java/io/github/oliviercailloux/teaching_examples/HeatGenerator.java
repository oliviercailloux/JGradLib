package io.github.oliviercailloux.teaching_examples;

public class HeatGenerator {
	public static void main(String[] args) {
		Heater defaultHeater = new Heater();
		Heater otherHeater = new Heater(2000);

		System.out.println(defaultHeater.pricePerHour());
		System.out.println(otherHeater.pricePerHour());
	}
}
