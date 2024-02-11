package io.github.oliviercailloux.java_grade.utils;

public class StdOutErrLoggerTests {
  public static void main(String[] args) {
    System.out.println("Hello not redirected");
    try (StdOutErrLogger redirector = StdOutErrLogger.redirect()) {
      System.out.println("Hello redirected");
    }
    System.out.println("Bye bye not redirected");
  }
}
