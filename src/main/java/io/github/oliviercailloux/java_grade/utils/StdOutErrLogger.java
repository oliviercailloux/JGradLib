package io.github.oliviercailloux.java_grade.utils;

public interface StdOutErrLogger extends AutoCloseable {
  public static StdOutErrLogger redirect() {
    final StdOutErrLoggerImpl logger = new StdOutErrLoggerImpl();
    logger.start();
    return logger;
  }

  @Override
  public void close();
}
