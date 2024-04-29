package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.exercices.additioner.MyAdditioner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AdditionerTests {
  public static MyAdditioner a;

  @BeforeAll
  public static void setUp() {
    a = Additioner.studentInstance;
  }

  @Test
  public void pos() {
    assertEquals(5, a.add(3, 2));
  }
  
  @Test
  public void neg() {
    assertEquals(-2, a.add(-4, 2));
  }
}
