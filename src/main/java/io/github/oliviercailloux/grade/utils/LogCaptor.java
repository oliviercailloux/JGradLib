package io.github.oliviercailloux.grade.utils;

import static com.google.common.base.Preconditions.checkState;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.List;
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
		final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		logger = lc.getLogger(loggerName);
		appender = new ListAppender<>();
		appender.setContext(lc);
		logger.addAppender(appender);
		logger.setLevel(Level.ALL);
		appender.start();
	}

	public void doRedirect() {
		final ImmutableSet<Appender<ILoggingEvent>> appenders = ImmutableSet.copyOf(logger.iteratorForAppenders());
		final ImmutableSet<ListAppender<ILoggingEvent>> ours = ImmutableSet.of(appender);
		if (appenders.equals(ours)) {
			return;
		}

		final ImmutableSet<Appender<ILoggingEvent>> notOurs = Sets.difference(appenders, ours).immutableCopy();
		checkState(notOurs.size() == 1, notOurs);
		final Appender<ILoggingEvent> notOurAppender = Iterables.getOnlyElement(notOurs);
		notOurAppender.stop();
		logger.detachAppender(notOurAppender);
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
