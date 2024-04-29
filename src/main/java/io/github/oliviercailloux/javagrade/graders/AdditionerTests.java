package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.exercices.additioner.MyAdditioner;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AdditionerTests {
  public static Instanciator i;
  public MyAdditioner a;

  @BeforeAll
  public static void setUp() {
    i = Additioner.staticInstanciator;
  }

  @BeforeEach
  public void setUpEach() {
    final TryCatchAll<MyAdditioner> my = i.tryGetInstance(MyAdditioner.class);
    a = my.orThrow(e -> new RuntimeException("Could not instanciate MyAdditioner.", e));
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
