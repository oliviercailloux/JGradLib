package io.github.oliviercailloux.bytecode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.Policy;
import java.util.function.Function;

import io.github.oliviercailloux.utils.Utils;

public class MyFunctionCopying implements Function<String, String> {

  public static Function<String, String> newInstance() {
    return new MyFunctionCopying();
  }

  private MyFunctionCopying() {
    /** Empty private constructor. */
  }

  @Override
  public String apply(String t) {
    try {
      Utils.copyRecursively(Path.of(""), Path.of("ploum"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return "ok";
  }
}
