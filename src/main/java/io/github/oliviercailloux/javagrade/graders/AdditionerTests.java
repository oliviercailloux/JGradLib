package io.github.oliviercailloux.javagrade.graders;

import io.github.oliviercailloux.exercices.additioner.MyAdditioner;

public class AdditionerTests {
  public MyAdditioner a;

  public boolean testAdd() {
    return a.add(1, 2) == 3;
  }
}
