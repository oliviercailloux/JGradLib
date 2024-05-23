package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.VerifyException;
import io.github.oliviercailloux.exercices.additioner.MyAdditioner;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.util.Optional;
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
    final Optional<MyAdditioner> my = i.getInstance(MyAdditioner.class);
    assertTrue(my.isPresent(), "Could not instanciate MyAdditioner.");
    a = my.orElseThrow(VerifyException::new);
  }

  @Test
  public void pos() {
    assertEquals(5, a.add(3, 2), "Trying to add 3 and 2.");
  }

  @Test
  public void neg() {
    assertEquals(-2, a.add(-4, 2));
  }
}
