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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * <p>
 * This is literally a copy of the {@link ByteArrayInputStream} from the runtime library except it's been modified in the following ways:
 * </p>
 * <ul>
 * <li>All synchronization has been removed.</li>
 * <li>You can access the underlying buffer directly using {@link MessageBufferOutput#getBuffer()}.</li>
 * <li>There is a {@link MessageBufferInput#clear()} which causes {@link MessageBufferInput#available()} to return '0'.</li>
 * <li>Has a {@link MessageBufferInput#setPosition(int)} method that will set the current position we will be reading from.</li>
 * <li>Has a {@link MessageBufferInput#getPosition()} which identifies the next byte to be read in..</li>
 * </ul>
 *
 * <p>
 * Remember, with great power comes great responsibility
 * </p>
 */
public class MessageBufferInput extends InputStream {
    /**
     * An array of bytes that was provided by the creator of the stream. Elements <code>buf[0]</code> through <code>buf[count-1]</code> are the only bytes that
     * can ever be read from the stream; element
     * <code>buf[pos]</code> is the next byte to be read.
     */
    protected final byte buf[];

    /**
     * The index one greater than the last valid character in the input stream buffer. This value should always be nonnegative and not larger than the length of
     * <code>buf</code>. It is one greater than the
     * position of the last byte within <code>buf</code> that can ever be read from the input stream buffer.
     */
    protected final int count;

    /**
     * The index of the next character to read from the input stream buffer. This value should always be nonnegative and not larger than the value of
     * <code>count</code>. The next byte to be read from the input
     * stream buffer will be <code>buf[pos]</code>.
     */
    protected int pos;

    /**
     * The currently marked position in the stream. ByteArrayInputStream objects are marked at position zero by default when constructed. They may be marked at
     * another position within the buffer by the
     * <code>mark()</code> method. The current buffer position is set to this point by the <code>reset()</code> method.
     * <p>
     * If no mark has been set, then the value of mark is the offset passed to the constructor (or 0 if the offset was not supplied).
     *
     * @since JDK1.1
     */
    protected int mark = 0;

    /**
     * Creates a <code>MessageBufferInput</code> so that it uses <code>buf</code> as its buffer array. The buffer array is not copied. The initial value of
     * <code>pos</code> is <code>0</code> and the initial
     * value of <code>count</code> is the length of <code>buf</code>.
     *
     * @param buf
     *     the input buffer.
     */
    public MessageBufferInput(final byte buf[]) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }

    /**
     * Creates <code>ByteArrayInputStream</code> that uses <code>buf</code> as its buffer array. The initial value of <code>pos</code> is <code>offset</code>
     * and the initial value of <code>count</code> is the
     * minimum of <code>offset+length</code> and <code>buf.length</code>. The buffer array is not copied. The buffer's mark is set to the specified offset.
     *
     * @param buf
     *     the input buffer.
     * @param offset
     *     the offset in the buffer of the first byte to read.
     * @param length
     *     the maximum number of bytes to read from the buffer.
     */
    public MessageBufferInput(final byte buf[], final int offset, final int length) {
        this.buf = buf;
        this.pos = offset;
        this.count = Math.min(offset + length, buf.length);
        this.mark = offset;
    }

    /**
     * Returns the underlying byte[]
     *
     * @return the current contents of this input stream, as a byte array.
     */
    public byte[] getBuffer() {
        return buf;
    }

    /**
     * Causes the {@link MessageBufferInput#available()} to return 0.
     */
    public void clear() {
        pos = count;
    }

    /**
     * Explicitly set the current position in the buffer.
     */
    public void setPosition(final int newPosition) {
        this.pos = newPosition;
    }

    /**
     * Returns the current position in the buffer where the next byte will be read from.
     */
    public int getPosition() {
        return pos;
    }

    public int getLimit() {
        return count;
    }

    public byte[] readByteArray() {
        final byte[] ret = Arrays.copyOfRange(buf, pos, count);
        pos = count;
        return ret;
    }

    /**
     * Reads the next byte of data from this input stream. The value byte is returned as an <code>int</code> in the range <code>0</code> to <code>255</code>. If
     * no byte is available because the end of the
     * stream has been reached, the value <code>-1</code> is returned.
     * <p>
     * This <code>read</code> method cannot block.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the stream has been reached.
     */
    @Override
    public int read() {
        return (pos < count) ? (buf[pos++] & 0xff) : -1;
    }

    /**
     * Reads up to <code>len</code> bytes of data into an array of bytes from this input stream. If <code>pos</code> equals <code>count</code>, then
     * <code>-1</code> is returned to indicate end of file.
     * Otherwise, the number <code>k</code> of bytes read is equal to the smaller of <code>len</code> and <code>count-pos</code>. If <code>k</code> is positive,
     * then bytes <code>buf[pos]</code> through
     * <code>buf[pos+k-1]</code> are copied into <code>b[off]</code> through <code>b[off+k-1]</code> in the manner performed by <code>System.arraycopy</code>.
     * The value <code>k</code> is added into
     * <code>pos</code> and <code>k</code> is returned.
     * <p>
     * This <code>read</code> method cannot block.
     *
     * @param b
     *     the buffer into which the data is read.
     * @param off
     *     the start offset in the destination array <code>b</code>
     * @param len
     *     the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or <code>-1</code> if there is no more data because the end of the stream has been reached.
     * @exception NullPointerException
     *     If <code>b</code> is <code>null</code>.
     * @exception IndexOutOfBoundsException
     *     If <code>off</code> is negative, <code>len</code> is negative, or <code>len</code> is greater than <code>b.length - off</code>
     */
    @Override
    public int read(final byte b[], final int off, int len) {
        if(b == null) {
            throw new NullPointerException();
        } else if(off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if(pos >= count) {
            return -1;
        }
        if(pos + len > count) {
            len = count - pos;
        }
        if(len <= 0) {
            return 0;
        }
        System.arraycopy(buf, pos, b, off, len);
        pos += len;
        return len;
    }

    public short readShort() throws IndexOutOfBoundsException {
        pos += 2;
        if(pos >= count) {
            pos -= 2;
            throw new IndexOutOfBoundsException();
        }
        return (short)((buf[pos] << 8) & buf[pos + 1]);
    }

    /**
     * Skips <code>n</code> bytes of input from this input stream. Fewer bytes might be skipped if the end of the input stream is reached. The actual number
     * <code>k</code> of bytes to be skipped is equal to the
     * smaller of <code>n</code> and <code>count-pos</code>. The value <code>k</code> is added into <code>pos</code> and <code>k</code> is returned.
     *
     * @param n
     *     the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     */
    @Override
    public long skip(long n) {
        if(pos + n > count) {
            n = count - pos;
        }
        if(n < 0) {
            return 0;
        }
        pos += n;
        return n;
    }

    /**
     * Returns the number of remaining bytes that can be read (or skipped over) from this input stream.
     * <p>
     * The value returned is <code>count&nbsp;- pos</code>, which is the number of bytes remaining to be read from the input buffer.
     *
     * @return the number of remaining bytes that can be read (or skipped over) from this input stream without blocking.
     */
    @Override
    public int available() {
        return count - pos;
    }

    /**
     * Tests if this <code>InputStream</code> supports mark/reset. The <code>markSupported</code> method of <code>ByteArrayInputStream</code> always returns
     * <code>true</code>.
     *
     * @since JDK1.1
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * Set the current marked position in the stream. ByteArrayInputStream objects are marked at position zero by default when constructed. They may be marked
     * at another position within the buffer by this
     * method.
     * <p>
     * If no mark has been set, then the value of the mark is the offset passed to the constructor (or 0 if the offset was not supplied).
     *
     * <p>
     * Note: The <code>readAheadLimit</code> for this class has no meaning.
     *
     * @since JDK1.1
     */
    @Override
    public void mark(final int readAheadLimit) {
        mark = pos;
    }

    /**
     * Resets the buffer to the marked position. The marked position is 0 unless another position was marked or an offset was specified in the constructor.
     */
    @Override
    public void reset() {
        pos = mark;
    }

    /**
     * Closing a {@code ByteArrayInputStream} has no effect. The methods in this class can be called after the stream has been closed without generating an
     * {@code IOException}.
     * <p>
     */
    @Override
    public void close() {}

}
