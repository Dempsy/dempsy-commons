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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * <p>
 * This is literally a copy of the {@link ByteArrayOutputStream} from the runtime library except it's been modified in the following ways:
 * </p>
 *
 * <ul>
 * <li>All synchronization has been removed.</li>
 * <li>The internal buffer's length is cached in a separate variable to avoid repeated dereferencing of the byte[] object</li>
 * <li>You can access the underlying buffer directly using {@link MessageBufferOutput#getBuffer()}.</li>
 * <li>Has a {@link #grow()} method to externally increase the internal buffer size.</li>
 * <li>Has a {@link #replace(byte[])} method that will allow replacing the underlying buffer with a different one.</li>
 * <li>Has a {@link #setPosition(int)} method that will set the current output position.</li>
 * <li>The 'size' method is renamed to {@link MessageBufferOutput#getPosition()} for clarity.</li>
 * </ul>
 *
 * <p>
 * Remember, with great power comes great responsibility
 * </p>
 *
 */
public class MessageBufferOutput extends OutputStream {
    /**
     * The buffer where data is stored.
     */
    protected byte buf[];

    /**
     * The number of valid bytes in the buffer.
     */
    protected int position;

    /**
     * This is a cache for the length of the underlying byte[] to avoid constant dereferencing.
     */
    protected int length;

    /**
     * Creates a new byte array output stream. The buffer capacity is initially 32 bytes, though its size increases if necessary.
     */
    public MessageBufferOutput() {
        this(32);
    }

    /**
     * Creates a new byte array output stream, with a buffer capacity of the specified size, in bytes.
     *
     * @param size
     *     the initial size.
     * @exception IllegalArgumentException
     *     if size is negative.
     */
    public MessageBufferOutput(final int size) {
        if(size < 0) {
            throw new IllegalArgumentException("Negative initial size: "
                + size);
        }
        buf = new byte[size];
        length = buf.length;
    }

    /**
     * Doubles the internal buffer size while preserving the data
     */
    public void grow() {
        buf = Arrays.copyOf(buf, length << 1);
        length = buf.length;
    }

    /**
     * Doubles the internal buffer size, or sets to the capacity provided if that's higher, while preserving the data
     */
    public void grow(final int newcap) {
        buf = Arrays.copyOf(buf, Math.max(length << 1, newcap));
        length = buf.length;
    }

    /**
     * Completely replace the underlying buffer.
     */
    public void replace(final byte[] buffer) {
        this.buf = buffer;
        this.length = buffer.length;
    }

    /**
     * Explicitly set the current position in the buffer.
     */
    public void setPosition(final int newPosition) {
        this.position = newPosition;
    }

    /**
     * Creates a newly allocated byte array. Its size is the current size of this output stream and the valid contents of the buffer have been copied into it.
     *
     * @return the current contents of this output stream, as a byte array.
     * @see java.io.ByteArrayOutputStream#size()
     */
    public synchronized byte toByteArray()[] {
        return Arrays.copyOf(buf, position);
    }

    /**
     * Writes the specified byte to this byte array output stream.
     *
     * @param b
     *     the byte to be written.
     */
    @Override
    public void write(final int b) {
        final int newcount = position + 1;
        if(newcount > length) {
            buf = Arrays.copyOf(buf, Math.max(length << 1, newcount));
            length = buf.length;
        }
        buf[position] = (byte)b;
        position = newcount;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array starting at offset <code>off</code> to this byte array output stream.
     *
     * @param b
     *     the data.
     * @param off
     *     the start offset in the data.
     * @param len
     *     the number of bytes to write.
     */
    @Override
    public void write(final byte b[], final int off, final int len) {
        if((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if(len == 0) {
            return;
        }
        final int newcount = position + len;
        if(newcount > length) {
            buf = Arrays.copyOf(buf, Math.max(length << 1, newcount));
            length = buf.length;
        }
        System.arraycopy(b, off, buf, position, len);
        position = newcount;
    }

    public void writeShort(final short x) {
        final int newcount = position + 2; // sizeof short in bytes
        if(newcount > length) {
            buf = Arrays.copyOf(buf, Math.max(length << 1, newcount));
            length = buf.length;
        }
        // standard java bigendian interpretation of a short
        buf[position++] = (byte)(x >> 8);
        buf[position++] = (byte)x;
    }

    /**
     * Writes an <code>int</code> to the underlying buffer as four
     * bytes, high byte first.
     */
    public void writeInt(final int v) throws IOException {
        final int newcount = position + 4; // sizeof int in bytes
        if(newcount > length) {
            buf = Arrays.copyOf(buf, Math.max(length << 1, newcount));
            length = buf.length;
        }
        buf[position++] = (byte)((v >>> 24) & 0xFF);
        buf[position++] = (byte)((v >>> 16) & 0xFF);
        buf[position++] = (byte)((v >>> 8) & 0xFF);
        buf[position++] = (byte)((v >>> 0) & 0xFF);
    }

    /**
     * Writes the complete contents of this byte array output stream to the specified output stream argument, as if by calling the output stream's write method
     * using <code>out.write(buf, 0, count)</code>.
     *
     * @param out
     *     the output stream to which to write the data.
     * @exception IOException
     *     if an I/O error occurs.
     */
    public void writeTo(final OutputStream out) throws IOException {
        out.write(buf, 0, position);
    }

    /**
     * Resets the <code>count</code> field of this byte array output stream to zero, so that all currently accumulated output in the output stream is discarded.
     * The output stream can be used again, reusing the
     * already allocated buffer space.
     */
    public void reset() {
        position = 0;
    }

    /**
     * Returns the underlying byte[]
     *
     * @return the current contents of this output stream, as a byte array.
     */
    public byte getBuffer()[] {
        return buf;
    }

    /**
     * Returns the current position in the buffer where the next byte will be written to.
     *
     * @return the value of the <code>count</code> field, which is the number of valid bytes in this output stream.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Converts the buffer's contents into a string decoding bytes using the platform's default character set. The length of the new {@code String} is a
     * function of the character set, and hence may not be
     * equal to the size of the buffer.
     *
     * <p>
     * This method always replaces malformed-input and unmappable-character sequences with the default replacement string for the platform's default character
     * set. The
     * {@linkplain java.nio.charset.CharsetDecoder} class should be used when more control over the decoding process is required.
     *
     * @return String decoded from the buffer's contents.
     * @since JDK1.1
     */
    @Override
    public String toString() {
        return new String(buf, 0, position);
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using the specified {@link java.nio.charset.Charset} name. The length of the new
     * {@code String} is a function of the charset, and hence
     * may not be equal to the length of the byte array.
     *
     * <p>
     * This method always replaces malformed-input and unmappable-character sequences with this charset's default replacement string. The
     * {@link java.nio.charset.CharsetDecoder} class should be used when more
     * control over the decoding process is required.
     *
     * @param charsetName
     *     the name of a supported {@linkplain java.nio.charset.Charset} <code>charset</code>
     * @return String decoded from the buffer's contents.
     * @exception UnsupportedEncodingException
     *     If the named charset is not supported
     * @since JDK1.1
     */
    public String toString(final String charsetName) throws UnsupportedEncodingException {
        return new String(buf, 0, position, charsetName);
    }

    /**
     * Closing a {@code ByteArrayOutputStream} has no effect. The methods in this class can be called after the stream has been closed without generating an
     * {@code IOException}.
     * <p>
     *
     */
    @Override
    public void close() {}

}
