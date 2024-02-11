package io.github.oliviercailloux.teaching_examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.g421.CyclicDiceRoller;
import org.junit.jupiter.api.Test;

public class MyCyclicTests {

  @Test
  void testRoll() throws Exception {
    CyclicDiceRoller c = new MyCyclic();
    c.roll();
    c.setResult(4, 3, 2);
    c.roll();
    final int first = c.first();
    assertEquals(4, first);
  }

  @Test
  void testSameNumbers() throws Exception {
    CyclicDiceRoller c = new MyCyclic();
    c.roll();
    c.setResult(1, 1, 1);
    c.roll();
    assertEquals(1, c.first());
    c.roll();
    assertEquals(1, c.first());
    c.roll();
    assertEquals(1, c.first());
    c.roll();
    assertEquals(1, c.first());
  }
}
