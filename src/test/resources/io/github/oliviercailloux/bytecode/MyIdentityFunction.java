package io.github.oliviercailloux.bytecode;

import java.util.function.Function;

public class MyIdentityFunction implements Function<String, String> {

  public static Function<String, String> newInstance() {
    return new MyIdentityFunction();
  }

  private MyIdentityFunction() {
    /** Empty private constructor. */
  }

  @Override
  public String apply(String t) {
    return t;
  }

}
