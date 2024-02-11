package io.github.oliviercailloux.teaching_examples;

public class GenericHeater<E> implements CostlyThingWithUnit {
  private int power;
  private double price;
  private E identifier;

  public GenericHeater() {
    power = 1000;
    price = 100;
  }

  public GenericHeater(int power) {
    this.power = power;
    price = power / 10d;
  }

  public E getIdentifier() {
    E localId = identifier;
    return localId;
  }

  public void setIdentifier(E newId) {
    identifier = newId;
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
    E localId = identifier;
    String stringId = localId.toString();

    return "Heater with power " + power + " and with identifier " + stringId;
  }
}
