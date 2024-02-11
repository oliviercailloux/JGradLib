package io.github.oliviercailloux.teachingexamples;

public class Elephant implements CostlyThing {
  private boolean greedy;

  public Elephant(boolean greedy) {
    this.greedy = greedy;
  }

  @Override
  public double price() {
    return 10000d;
  }

  /**
   * 1 unit per hour if the elephant is greedy.
   */
  @Override
  public double pricePerHour() {
    double factor = greedy ? 2d : 1d;
    return 1d * factor;
  }
}
