/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.oliviercailloux.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>
 * Copied from <a href=
 * "https://gitbox.apache.org/repos/asf?p=commons-compress.git;a=blob;f=src/main/java/org/apache/commons/compress/utils/SeekableInMemoryByteChannel.java;h=fd083c4a2784a4960cc5b3765968bcbda8e51360;hb=refs/heads/master">apache</a>.
 * Special licensing terms apply. Modified to render read-only and send
 * exceptions when the channel is closed, as demanded by the contract of
 * {@link SeekableByteChannel}.
 * </p>
 *
 * A {@link SeekableByteChannel} implementation that wraps a byte[].
 *
 * <p>
 * When this channel is used for writing an internal buffer grows to accommodate
 * incoming data. The natural size limit is the value of
 * {@link Integer#MAX_VALUE} and it is not possible to {@link #position(long)
 * set the position} or {@link #truncate truncate} to a value bigger than that.
 * Internal buffer can be accessed via
 * {@link SeekableInMemoryByteChannel#array()}.
 * </p>
 *
 * @since 1.13
 * @NotThreadSafe
 */
public class SeekableInMemoryByteChannel implements SeekableByteChannel {

	private byte[] data;
	private final AtomicBoolean closed = new AtomicBoolean();
	private int position, size;

	/**
	 * Constructor taking a byte array.
	 *
	 * <p>
	 * This constructor is intended to be used with pre-allocated buffer or when
	 * reading from a given byte array.
	 * </p>
	 *
	 * @param data input data or pre-allocated array.
	 */
	public SeekableInMemoryByteChannel(byte[] data) {
		this.data = data;
		size = data.length;
	}

	/**
	 * Parameterless constructor - allocates internal buffer by itself.
	 */
	public SeekableInMemoryByteChannel() {
		this(new byte[0]);
	}

	/**
	 * Constructor taking a size of storage to be allocated.
	 *
	 * <p>
	 * Creates a channel and allocates internal storage of a given size.
	 * </p>
	 *
	 * @param size size of internal buffer to allocate, in bytes.
	 */
	public SeekableInMemoryByteChannel(int size) {
		this(new byte[size]);
	}

	/**
	 * Returns this channel's position.
	 *
	 */
	@Override
	public long position() throws IOException {
		ensureOpen();
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		throw new NonWritableChannelException();
	}

	/**
	 * Returns the current size of entity to which this channel is connected.
	 *
	 * <p>
	 * This method violates the contract of {@link SeekableByteChannel#size} as it
	 * will not throw any exception when invoked on a closed channel. Instead it
	 * will return the size the channel had when close has been called.
	 * </p>
	 */
	@Override
	public long size() {
		return size;
	}

	/**
	 * Truncates the entity, to which this channel is connected, to the given size.
	 *
	 * <p>
	 * This method violates the contract of {@link SeekableByteChannel#truncate} as
	 * it will not throw any exception when invoked on a closed channel.
	 * </p>
	 */
	@Override
	public SeekableByteChannel truncate(long newSize) {
		throw new NonWritableChannelException();
	}

	@Override
	public int read(ByteBuffer buf) throws IOException {
		ensureOpen();
		int wanted = buf.remaining();
		int possible = size - position;
		if (possible <= 0) {
			return -1;
		}
		if (wanted > possible) {
			wanted = possible;
		}
		buf.put(data, position, wanted);
		position += wanted;
		return wanted;
	}

	@Override
	public void close() {
		closed.set(true);
	}

	@Override
	public boolean isOpen() {
		return !closed.get();
	}

	@Override
	public int write(ByteBuffer b) throws IOException {
		throw new NonWritableChannelException();
	}

	/**
	 * Obtains the array backing this channel.
	 *
	 * <p>
	 * NOTE: The returned buffer is not aligned with containing data, use
	 * {@link #size()} to obtain the size of data stored in the buffer.
	 * </p>
	 *
	 * @return internal byte array.
	 */
	public byte[] array() {
		return data;
	}

	private void ensureOpen() throws ClosedChannelException {
		if (!isOpen()) {
			throw new ClosedChannelException();
		}
	}

}