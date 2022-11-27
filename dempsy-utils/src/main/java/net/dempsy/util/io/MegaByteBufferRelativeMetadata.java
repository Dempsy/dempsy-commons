/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.util.io;

import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.InvalidMarkException;
import java.nio.ReadOnlyBufferException;

/**
 * Much of this code is copied from {@link Buffer} because you can't inherit from it
 * but it already has the mark, position, limit, capacity, flip() functionality
 */
public class MegaByteBufferRelativeMetadata {

    private final MegaByteBuffer underlying;

    // Invariants: mark <= position <= limit <= capacity
    private long mark = -1;
    private long position = 0;
    private long limit;
    private final long capacity;
    private final long offset;

    public MegaByteBufferRelativeMetadata(final MegaByteBuffer underlying) {
        this.underlying = underlying;
        capacity = underlying.capacity();
        limit = capacity;
        offset = 0;
    }

    public MegaByteBufferRelativeMetadata(final MegaByteBuffer underlying, final long offset) {
        this.underlying = underlying;
        capacity = underlying.capacity();
        limit = capacity;
        this.offset = offset;
    }

    /**
     * Returns this buffer's capacity.
     *
     * @return The capacity of this buffer
     */
    public final long capacity() {
        return capacity;
    }

    /**
     * Returns this buffer's position.
     *
     * @return The position of this buffer
     */
    public final long position() {
        return position;
    }

    /**
     * Sets this buffer's position. If the mark is defined and larger than the
     * new position then it is discarded.
     *
     * @param newPosition
     *     The new position value; must be non-negative
     *     and no larger than the current limit
     *
     * @return This buffer
     *
     * @throws IllegalArgumentException
     *     If the preconditions on {@code newPosition} do not hold
     */
    public MegaByteBufferRelativeMetadata position(final int newPosition) {
        if(newPosition > limit | newPosition < 0)
            throw createPositionException(newPosition);
        if(mark > newPosition) mark = -1;
        position = newPosition;
        return this;
    }

    /**
     * Returns this buffer's limit.
     *
     * @return The limit of this buffer
     */
    public final long limit() {
        return limit;
    }

    /**
     * Sets this buffer's limit. If the position is larger than the new limit
     * then it is set to the new limit. If the mark is defined and larger than
     * the new limit then it is discarded.
     *
     * @param newLimit
     *     The new limit value; must be non-negative
     *     and no larger than this buffer's capacity
     *
     * @return This buffer
     *
     * @throws IllegalArgumentException
     *     If the preconditions on {@code newLimit} do not hold
     */
    public MegaByteBufferRelativeMetadata limit(final int newLimit) {
        if(newLimit > capacity | newLimit < 0)
            throw createLimitException(newLimit);
        limit = newLimit;
        if(position > newLimit) position = newLimit;
        if(mark > newLimit) mark = -1;
        return this;
    }

    /**
     * Sets this buffer's mark at its position.
     *
     * @return This buffer
     */
    public MegaByteBufferRelativeMetadata mark() {
        mark = position;
        return this;
    }

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     * <p>
     * Invoking this method neither changes nor discards the mark's
     * value.
     * </p>
     *
     * @return This buffer
     *
     * @throws InvalidMarkException
     *     If the mark has not been set
     */
    public MegaByteBufferRelativeMetadata reset() {
        final long m = mark;
        if(m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
    }

    /**
     * Clears this buffer. The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     * <p>
     * Invoke this method before using a sequence of channel-read or
     * <i>put</i> operations to fill this buffer. For example:
     *
     * <blockquote>
     *
     * <pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data
     * </pre>
     *
     * </blockquote>
     *
     * <p>
     * This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case.
     * </p>
     *
     * @return This buffer
     */
    public MegaByteBufferRelativeMetadata clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }

    /**
     * Flips this buffer. The limit is set to the current position and then
     * the position is set to zero. If the mark is defined then it is
     * discarded.
     *
     * <p>
     * After a sequence of channel-read or <i>put</i> operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * <i>get</i> operations. For example:
     *
     * <blockquote>
     *
     * <pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel
     * </pre>
     *
     * </blockquote>
     *
     * <p>
     * This method is often used in conjunction with the {@link
     * java.nio.ByteBuffer#compact compact} method when transferring data from
     * one place to another.
     * </p>
     *
     * @return This buffer
     */
    public MegaByteBufferRelativeMetadata flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * Rewinds this buffer. The position is set to zero and the mark is
     * discarded.
     *
     * <p>
     * Invoke this method before a sequence of channel-write or <i>get</i>
     * operations, assuming that the limit has already been set
     * appropriately. For example:
     *
     * <blockquote>
     *
     * <pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array
     * </pre>
     *
     * </blockquote>
     *
     * @return This buffer
     */
    public MegaByteBufferRelativeMetadata rewind() {
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * Returns the number of elements between the current position and the
     * limit.
     *
     * @return The number of elements remaining in this buffer
     */
    public final long remaining() {
        final long rem = limit - position;
        return rem > 0 ? rem : 0;
    }

    /**
     * Tells whether there are any elements between the current position and
     * the limit.
     *
     * @return {@code true} if, and only if, there is at least one element
     * remaining in this buffer
     */
    public final boolean hasRemaining() {
        return position < limit;
    }

    /**
     * Relative <i>get</i> method. Reads the byte at this buffer's
     * current position, and then increments the position.
     *
     * @return The byte at the buffer's current position
     *
     * @throws BufferUnderflowException
     *     If the buffer's current position is not smaller than its limit
     */
    public byte get() {
        return underlying.get(ix(nextGetIndex()));
    }

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p>
     * This method transfers bytes from this buffer into the given
     * destination array. If there are fewer bytes remaining in the
     * buffer than are required to satisfy the request, that is, if
     * {@code length}&nbsp;{@code >}&nbsp;{@code remaining()}, then no
     * bytes are transferred and a {@link BufferUnderflowException} is
     * thrown.
     *
     * <p>
     * Otherwise, this method copies {@code length} bytes from this
     * buffer into the given array, starting at the current position of this
     * buffer and at the given offset in the array. The position of this
     * buffer is then incremented by {@code length}.
     *
     * <p>
     * In other words, an invocation of this method of the form
     * <code>src.get(dst,&nbsp;off,&nbsp;len)</code> has exactly the same effect as
     * the loop
     *
     * <pre>
     * {@code
     *     for (int i = off; i < off + len; i++)
     *         dst[i] = src.get();
     * }
     * </pre>
     *
     * except that it first checks that there are sufficient bytes in
     * this buffer and it is potentially much more efficient.
     *
     * @param dst
     *     The array into which bytes are to be written
     *
     * @param dstOffset
     *     The offset within the array of the first byte to be
     *     written; must be non-negative and no larger than
     *     {@code dst.length}
     *
     * @param length
     *     The maximum number of bytes to be written to the given
     *     array; must be non-negative and no larger than
     *     {@code dst.length - offset}
     *
     * @return This buffer
     *
     * @throws BufferUnderflowException
     *     If there are fewer than {@code length} bytes
     *     remaining in this buffer
     *
     * @throws IndexOutOfBoundsException
     *     If the preconditions on the {@code offset} and {@code length}
     *     parameters do not hold
     */
    public MegaByteBufferRelativeMetadata get(final byte[] dst, final int dstOffset, final int length) {
        checkBounds(dstOffset, length, dst.length);
        if(length > remaining())
            throw new BufferUnderflowException();
        underlying.getBytes(ix(nextGetIndex(length)), dst, dstOffset, length);
        return this;
    }

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p>
     * This method transfers bytes from this buffer into the given
     * destination array. An invocation of this method of the form
     * {@code src.get(a)} behaves in exactly the same way as the invocation
     *
     * <pre>
     * src.get(a, 0, a.length)
     * </pre>
     *
     * @param dst
     *     The destination array
     *
     * @return This buffer
     *
     * @throws BufferUnderflowException
     *     If there are fewer than {@code length} bytes
     *     remaining in this buffer
     */
    public MegaByteBufferRelativeMetadata get(final byte[] dst) {
        return get(dst, 0, dst.length);
    }

    /**
     * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p>
     * Writes the given byte into this buffer at the current
     * position, and then increments the position.
     * </p>
     *
     * @param b
     *     The byte to be written
     *
     * @return This buffer
     *
     * @throws BufferOverflowException
     *     If this buffer's current position is not smaller than its limit
     *
     * @throws ReadOnlyBufferException
     *     If this buffer is read-only
     */
    public MegaByteBufferRelativeMetadata put(final byte b) {
        underlying.put(ix(nextGetIndex()), b);
        return this;
    }

    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p>
     * This method transfers bytes into this buffer from the given
     * source array. If there are more bytes to be copied from the array
     * than remain in this buffer, that is, if
     * {@code length}&nbsp;{@code >}&nbsp;{@code remaining()}, then no
     * bytes are transferred and a {@link BufferOverflowException} is
     * thrown.
     *
     * <p>
     * Otherwise, this method copies {@code length} bytes from the
     * given array into this buffer, starting at the given offset in the array
     * and at the current position of this buffer. The position of this buffer
     * is then incremented by {@code length}.
     *
     * <p>
     * In other words, an invocation of this method of the form
     * <code>dst.put(src,&nbsp;off,&nbsp;len)</code> has exactly the same effect as
     * the loop
     *
     * <pre>
     * {@code
     *     for (int i = off; i < off + len; i++)
     *         dst.put(a[i]);
     * }
     * </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param src
     *     The array from which bytes are to be read
     *
     * @param dstOffset
     *     The offset within the array of the first byte to be read;
     *     must be non-negative and no larger than {@code array.length}
     *
     * @param length
     *     The number of bytes to be read from the given array;
     *     must be non-negative and no larger than
     *     {@code array.length - offset}
     *
     * @return This buffer
     *
     * @throws BufferOverflowException
     *     If there is insufficient space in this buffer
     *
     * @throws IndexOutOfBoundsException
     *     If the preconditions on the {@code offset} and {@code length}
     *     parameters do not hold
     *
     * @throws ReadOnlyBufferException
     *     If this buffer is read-only
     */
    public MegaByteBufferRelativeMetadata put(final byte[] src, final int dstOffset, final int length) {
        checkBounds(dstOffset, length, src.length);
        if(length > remaining())
            throw new BufferOverflowException();
        underlying.put(ix(nextGetIndex(length)), src, dstOffset, length);
        return this;
    }

    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p>
     * This method transfers the entire content of the given source
     * byte array into this buffer. An invocation of this method of the
     * form {@code dst.put(a)} behaves in exactly the same way as the
     * invocation
     *
     * <pre>
     * dst.put(a, 0, a.length)
     * </pre>
     *
     * @param src
     *     The source array
     *
     * @return This buffer
     *
     * @throws BufferOverflowException
     *     If there is insufficient space in this buffer
     *
     * @throws ReadOnlyBufferException
     *     If this buffer is read-only
     */
    public final MegaByteBufferRelativeMetadata put(final byte[] src) {
        return put(src, 0, src.length);
    }

    /**
     * Relative <i>get</i> method for reading a long value.
     *
     * <p>
     * Reads the next eight bytes at this buffer's current position,
     * composing them into a long value according to the current byte order,
     * and then increments the position by eight.
     * </p>
     *
     * @return The long value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *     If there are fewer than eight bytes
     *     remaining in this buffer
     */
    public long getLong() {
        return underlying.getLong(nextGetIndex(Long.BYTES));
    }

    private static void checkBounds(final int off, final int len, final int size) { // package-private
        if((off | len | (off + len) | (size - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
    }

    /**
     * Checks the current position against the limit, throwing a {@link
     * BufferUnderflowException} if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return The current position value, before it is incremented
     */
    private final long nextGetIndex() {
        final long p = position;
        if(p >= limit)
            throw new BufferUnderflowException();
        position = p + 1;
        return p;
    }

    private final long nextGetIndex(final int nb) {
        final long p = position;
        if(limit - p < nb)
            throw new BufferUnderflowException();
        position = p + nb;
        return p;
    }

    private long ix(final long i) {
        return i + offset;
    }

    /**
     * Verify that {@code 0 < newLimit <= capacity}
     *
     * @param newLimit
     *     The new limit value
     *
     * @throws IllegalArgumentException
     *     If the specified limit is out of bounds.
     */
    private IllegalArgumentException createLimitException(final int newLimit) {
        String msg = null;

        if(newLimit > capacity) {
            msg = "newLimit > capacity: (" + newLimit + " > " + capacity + ")";
        } else { // assume negative
            assert newLimit < 0: "newLimit expected to be negative";
            msg = "newLimit < 0: (" + newLimit + " < 0)";
        }

        return new IllegalArgumentException(msg);
    }

    /**
     * Verify that {@code 0 < newPosition <= limit}
     *
     * @param newPosition
     *     The new position value
     *
     * @throws IllegalArgumentException
     *     If the specified position is out of bounds.
     */
    private IllegalArgumentException createPositionException(final int newPosition) {
        String msg = null;

        if(newPosition > limit) {
            msg = "newPosition > limit: (" + newPosition + " > " + limit + ")";
        } else { // assume negative
            assert newPosition < 0: "newPosition expected to be negative";
            msg = "newPosition < 0: (" + newPosition + " < 0)";
        }

        return new IllegalArgumentException(msg);
    }
}
