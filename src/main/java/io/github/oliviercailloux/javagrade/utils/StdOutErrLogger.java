package io.github.oliviercailloux.javagrade.utils;

public interface StdOutErrLogger extends AutoCloseable {
  public static StdOutErrLogger redirect() {
    final StdOutErrLoggerImpl logger = new StdOutErrLoggerImpl();
    logger.start();
    return logger;
  }

  @Override
  public void close();
}
