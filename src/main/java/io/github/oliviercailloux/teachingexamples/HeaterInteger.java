package io.github.oliviercailloux.teachingexamples;

public class HeaterInteger implements CostlyThingWithUnit {
  private int power;
  private double price;
  private Integer identifier;

  public HeaterInteger() {
    power = 1000;
    price = 100;
  }

  public Integer getIdentifier() {
    return identifier;
  }

  public HeaterInteger(int power) {
    this.power = power;
    price = power / 10d;
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
