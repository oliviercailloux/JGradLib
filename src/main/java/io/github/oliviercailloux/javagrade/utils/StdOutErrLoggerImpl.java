package io.github.oliviercailloux.javagrade.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class StdOutErrLoggerImpl implements StdOutErrLogger {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(StdOutErrLoggerImpl.class);

  /**
   * Slightly improved from
   * https://svn.apache.org/viewvc/logging/log4j/trunk/contribs/JimMoore/LoggingOutputStream.java?view=co,
   * found thanks to https://stackoverflow.com/a/11187462 (I didn’t check the writing algorithm).
   */
  private static class LoggingOutputStream extends OutputStream {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final int DEFAULT_BUFFER_LENGTH = 2048;
    private Logger delegate;
    private Level level;
    private byte[] buffer;

    /**
     * The number of valid bytes in the buffer. This value is always in the range <tt>0</tt> through
     * <tt>buf.length</tt>; elements <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid
     * byte data.
     */
    private int count;

    /**
     * Remembers the size of the buffer for speed.
     */
    private int bufferLength;

    private boolean hasBeenClosed = false;

    private LoggingOutputStream(Logger delegate, Level level) {
      this.delegate = checkNotNull(delegate);
      this.level = checkNotNull(level);
      count = 0;
      bufferLength = DEFAULT_BUFFER_LENGTH;
      buffer = new byte[DEFAULT_BUFFER_LENGTH];
      hasBeenClosed = false;
    }

    @Override
    public void write(int b) throws IOException {
      checkState(!hasBeenClosed);

      /* don't log nulls */
      if (b == 0) {
        return;
      }

      /* would this be writing past the buffer? */
      if (count == bufferLength) {
        /* grow the buffer */
        final int newBufLength = bufferLength + DEFAULT_BUFFER_LENGTH;
        final byte[] newBuf = new byte[newBufLength];

        System.arraycopy(buffer, 0, newBuf, 0, bufferLength);

        buffer = newBuf;
        bufferLength = newBufLength;
      }

      buffer[count] = (byte) b;
      count++;
    }

    @Override
    public void flush() {
      checkState(!hasBeenClosed);

      if (count == 0) {
        return;
      }

      /* don't print out blank lines; flushing from PrintStream puts out these */
      if (count == LINE_SEPARATOR.length()) {
        if (((char) buffer[0]) == LINE_SEPARATOR.charAt(0)
            && ((count == 1) || /* <- Unix & Mac, -> Windows */
                ((count == 2) && ((char) buffer[1]) == LINE_SEPARATOR.charAt(1)))) {
          reset();
          return;
        }
      }

      final byte[] theBytes = new byte[count];

      System.arraycopy(buffer, 0, theBytes, 0, count);

      final String content = new String(theBytes);
      switch (level) {
        case ERROR:
          delegate.error(content);
          break;
        case WARN:
          delegate.warn(content);
          break;
        case INFO:
          delegate.info(content);
          break;
        case DEBUG:
          delegate.debug(content);
          break;
        case TRACE:
          delegate.trace(content);
          break;
        default:
          throw new VerifyException("Unknown level");
      }

      reset();
    }

    private void reset() {
      // not resetting the buffer -- assuming that if it grew that it
      // will likely grow similarly again
      count = 0;
    }

    @Override
    public void close() {
      if (!hasBeenClosed) {
        flush();
        hasBeenClosed = true;
      }
    }
  }

  private ImmutableList<StdOutErrLoggerImpl.LoggingOutputStream> loggingOutputStreamsToClose;
  private ImmutableList<PrintStream> printStreamsToClose;
  private PrintStream outOrig;

  private PrintStream errOrig;
  private boolean closed;

  StdOutErrLoggerImpl() {
    loggingOutputStreamsToClose = null;
    printStreamsToClose = null;
    outOrig = null;
    errOrig = null;
    closed = false;
  }

  @SuppressWarnings("resource")
  void start() {
    checkState(loggingOutputStreamsToClose == null);

    final StdOutErrLoggerImpl.LoggingOutputStream out =
        new LoggingOutputStream(LOGGER, Level.DEBUG);
    final StdOutErrLoggerImpl.LoggingOutputStream err =
        new LoggingOutputStream(LOGGER, Level.DEBUG);
    loggingOutputStreamsToClose = ImmutableList.of(out, err);
    final PrintStream outPs = new PrintStream(out);
    final PrintStream errPs = new PrintStream(err);
    printStreamsToClose = ImmutableList.of(outPs, errPs);

    outOrig = System.out;
    errOrig = System.err;

    System.setOut(outPs);
    System.setErr(errPs);
  }

  @Override
  public void close() {
    if (!closed) {
      System.setOut(outOrig);
      System.setErr(errOrig);
      closed = true;
    }

    for (PrintStream toClose : printStreamsToClose) {
      toClose.close();
    }
    for (StdOutErrLoggerImpl.LoggingOutputStream toClose : loggingOutputStreamsToClose) {
      toClose.close();
    }
  }
}
