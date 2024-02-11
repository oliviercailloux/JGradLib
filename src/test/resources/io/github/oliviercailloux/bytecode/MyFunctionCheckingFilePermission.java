package io.github.oliviercailloux.bytecode;

import java.io.FilePermission;
import java.security.AccessController;
import java.util.function.Function;

public class MyFunctionCheckingFilePermission implements Function<String, String> {

  public static Function<String, String> newInstance() {
    return new MyFunctionCheckingFilePermission();
  }

  private MyFunctionCheckingFilePermission() {
    /** Empty private constructor. */
  }

  @Override
  public String apply(String t) {
    AccessController.checkPermission(new FilePermission("/-", "read"));
    return "ok";
  }

}
