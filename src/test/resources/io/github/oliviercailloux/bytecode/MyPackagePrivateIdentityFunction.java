package io.github.oliviercailloux.bytecode;

import java.util.function.Function;

class MyPackagePrivateIdentityFunction implements Function<String, String> {

  public static Function<String, String> newInstance() {
    return new MyPackagePrivateIdentityFunction();
  }

  private MyPackagePrivateIdentityFunction() {
    /** Empty private constructor. */
  }

  @Override
  public String apply(String t) {
    return t;
  }

}
