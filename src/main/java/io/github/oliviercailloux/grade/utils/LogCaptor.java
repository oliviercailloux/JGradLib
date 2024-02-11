package io.github.oliviercailloux.grade.utils;

import static com.google.common.base.Preconditions.checkState;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogCaptor implements AutoCloseable {
  public static LogCaptor capturing(String loggerName) {
    return new LogCaptor(loggerName);
  }

  public static LogCaptor redirecting(String loggerName) {
    final LogCaptor captor = new LogCaptor(loggerName);
    captor.doRedirect();
    return captor;
  }

  private final ch.qos.logback.classic.Logger logger;
  private final ListAppender<ILoggingEvent> appender;

  private LogCaptor(String loggerName) {
    logger = getLogger(loggerName);
    appender = new ListAppender<>();
    appender.setContext(getLoggerContext());
    logger.addAppender(appender);
    logger.setLevel(Level.ALL);
    appender.start();
  }

  private LoggerContext getLoggerContext() {
    return (LoggerContext) LoggerFactory.getILoggerFactory();
  }

  private ch.qos.logback.classic.Logger getLogger(String loggerName) {
    return getLoggerContext().getLogger(loggerName);
  }

  private ch.qos.logback.classic.Logger getRootLogger() {
    return getLogger(Logger.ROOT_LOGGER_NAME);
  }

  @SuppressWarnings("unused")
  private Appender<ILoggingEvent> getRootConsoleAppender() {
    final ImmutableSet<Appender<ILoggingEvent>> appenders =
        ImmutableSet.copyOf(getRootLogger().iteratorForAppenders());
    checkState(appenders.size() == 2);
    final Appender<ILoggingEvent> consoleAppender = appenders.stream()
        .filter(a -> a instanceof ConsoleAppender).collect(MoreCollectors.onlyElement());
    return consoleAppender;
  }

  public void doRedirect() {
    logger.setAdditive(false);
  }

  /**
   * Reads through!
   */
  public List<ILoggingEvent> getEvents() {
    return appender.list;
  }

  @Override
  public void close() {
    appender.stop();
    logger.detachAppender(appender);
  }
}
