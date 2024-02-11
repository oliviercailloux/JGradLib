package io.github.oliviercailloux.teachingexamples;

public class CostManager {

  /**
   * Computes the total cost of buying and using both costly things for 10 hours.
   *
   * @param h1
   * @param h2
   * @return
   */
  public static double cost(CostlyThing... things) {
    double cost = 0;
    for (CostlyThing c : things) {
      cost += c.price() + c.pricePerHour() * 10;
    }
    return cost;
  }
}
