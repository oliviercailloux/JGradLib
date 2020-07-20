package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * From https://stackoverflow.com/a/11187462, with some modifications.
 *
 * This is really backwards: this class is an outputstream, but it encodes the
 * bytes it is provided with, to pass them to a (character-based) logger. I
 * think that this absurdity is a consequence of having to provide a
 * PrintStream: a PrintStream is both a character and a byte-based stream, thus,
 * requires some encoding or decoding.
 */
class LoggingOutputStream extends OutputStream {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutputStream.class);

	public static PrintStream newInstance(Logger logger) {
		return new PrintStream(new LoggingOutputStream(logger, false), true, StandardCharsets.UTF_8);
	}

	private Logger log;
	private boolean isError;

	/**
	 * Used to maintain the contract of {@link #close()}.
	 */
	private boolean hasBeenClosed = false;

	/**
	 * The internal buffer where data is stored.
	 */
	private byte[] buf;

	/**
	 * The number of valid bytes in the buffer. This value is always in the range
	 * <tt>0</tt> through <tt>buf.length</tt>; elements <tt>buf[0]</tt> through
	 * <tt>buf[count-1]</tt> contain valid byte data.
	 */
	private int count;

	/**
	 * Remembers the size of the buffer for speed.
	 */
	private int bufLength;

	/**
	 * The default number of bytes in the buffer: 2048
	 */
	public static final int DEFAULT_BUFFER_LENGTH = 2048;

	private LoggingOutputStream(Logger log, boolean isError) {
		this.log = checkNotNull(log);
		this.isError = isError;
		bufLength = DEFAULT_BUFFER_LENGTH;
		buf = new byte[DEFAULT_BUFFER_LENGTH];
		count = 0;
	}

	/**
	 * Closes this output stream and releases any system resources associated with
	 * this stream. The general contract of <code>close</code> is that it closes the
	 * output stream. A closed stream cannot perform output operations and cannot be
	 * reopened.
	 */
	@Override
	public void close() {
		flush();
		hasBeenClosed = true;
	}

	@Override
	public void write(int b) throws IOException {
		if (hasBeenClosed) {
			throw new IOException("The stream has been closed.");
		}

		/** Grow the buffer if attempting to write past the buffer. */
		if (count == bufLength) {
			final int newBufLength = bufLength * 2;
			final byte[] newBuf = new byte[newBufLength];

			System.arraycopy(buf, 0, newBuf, 0, bufLength);

			buf = newBuf;
			bufLength = newBufLength;
		}

		buf[count] = (byte) b;
		count++;
	}

	@Override
	public void flush() {
		if (count == 0) {
			return;
		}

		final byte[] theBytes = new byte[count];

		System.arraycopy(buf, 0, theBytes, 0, count);

		final String buffer = new String(theBytes, StandardCharsets.UTF_8);
		/**
		 * Oddly enough, PrintStream seems to flush twice, in automatic mode: once just
		 * before the EOL, and once just after. We wait for the call just after.
		 */
		if (!buffer.endsWith(System.lineSeparator())) {
			return;
		}
		/**
		 * Now we remove the EOL, because the call to the logger already includes it.
		 */
		final String content = buffer.substring(0, buffer.length() - System.lineSeparator().length());
		if (isError) {
			log.error(content);
		} else {
			log.debug(content);
		}

		reset();
	}

	private void reset() {
		// not resetting the buffer -- assuming that if it grew that it
		// will likely grow similarly again
		count = 0;
	}
}