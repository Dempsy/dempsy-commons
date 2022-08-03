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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteBufferHelper {
    /**
     * <p>
     * This method will copy 'fromSize' bytes of data from the 'from' buffer
     * starting at the 'fromIndex,' to the 'dest' buffer starting at 'toIndex.'
     * </p>
     *
     * <p>
     * Note: this can modify the 'mark' of either or both buffers but it
     * explicitly preserves the 'position' and therefore acts like an absolute
     * bulk copy
     * </p>
     *
     * @param from
     *     is the buffer to copy the data from
     * @param fromIndex
     *     is the position in the from buffer to copy the data from
     * @param size
     *     is the number of bytes to copy
     * @param dest
     *     is the buffer to copy the data to
     * @param toIndex
     *     is the position in the buffer to copy the data to.
     */
    public static void copy(final ByteBuffer from, final int fromIndex, final int size, final ByteBuffer dest, final int toIndex) {
        final byte[] buf = new byte[size];

        // need an absolute bulk get
        int oldPosition = from.position();
        from.position(fromIndex);
        from.get(buf);
        from.position(oldPosition);

        // need an absolute bulk put
        oldPosition = dest.position();
        dest.position(toIndex);
        dest.put(buf);
        dest.position(oldPosition);
    }

    /**
     * This is an <em>absolute</em> get from the ByteBuffer. It will fill the
     * byte array provided.
     *
     * @param from
     *     is the ByteBuffer to retrieve the bytes from
     * @param index
     *     is where to retrieve the bytes from
     * @param buffer
     *     is the byte array to fill.
     * @return
     */
    public static byte[] getBytes(final ByteBuffer from, final int index, final byte[] buffer) {
        final int oldPosition = from.position();
        from.position(index);
        from.get(buffer);
        from.position(oldPosition);
        return buffer;
    }

    /**
     * This is an <em>absolute</em> get from the ByteBuffer. It will fill the
     * byte array provided.
     *
     * @param from
     *     is the ByteBuffer to retrieve the bytes from
     * @param index
     *     is where to retrieve the bytes from
     * @param buffer
     *     is the byte array to fill.
     * @return
     */
    public static byte[] getBytes(final ByteBuffer from, final int index, final byte[] buffer, final int offset, final int length) {
        final int oldPosition = from.position();
        from.position(index);
        from.get(buffer, offset, length);
        from.position(oldPosition);
        return buffer;
    }

    /**
     * This is an <em>absolute</em> get from the ByteBuffer.
     *
     * @param from
     *     is the ByteBuffer to retrieve the bytes from
     * @param index
     *     is where to retrieve the bytes from
     * @param size
     *     is the size of the byte array to create and fill.
     * @return
     */
    public static byte[] getBytes(final ByteBuffer from, final int index, final int size) {
        final byte[] ret = new byte[size];
        return getBytes(from, index, ret);
    }

    /**
     * This method will read the data input stream into the byte buffer for the
     * given number of bytes. It relies on the internal relative buffer
     * bookkeeping.
     *
     * @param dis
     *     is the DataInputStream to read the bytes from
     * @param towrite
     *     is the ByteBuffer to write bytes into
     * @param byteCount
     *     is the number of bytes to read off the stream and into the
     *     ByteBuffer
     * @return the number of bytes read.
     * @throws IOException
     *     if there's an IOException (duh!)
     */
    public static long readStream(final DataInputStream dis, final MegaByteBufferRelativeMetadata towrite, final long byteCount) throws IOException {
        return readStream(dis,

            (buf, offset, numBytes) -> towrite.put(buf, offset, numBytes)

            , byteCount);
    }

    /**
     * This method will read the data input stream into the byte buffer for the
     * given number of bytes. It relies on the internal relative buffer
     * bookkeeping.
     *
     * @param dis
     *     is the DataInputStream to read the bytes from
     * @param towrite
     *     is the ByteBuffer to write bytes into
     * @param byteCount
     *     is the number of bytes to read off the stream and into the
     *     ByteBuffer
     * @return the number of bytes read.
     * @throws IOException
     *     if there's an IOException (duh!)
     */
    public static long readStream(final DataInputStream dis, final ByteBuffer towrite, final long byteCount) throws IOException {
        // return readStream(new ByteBufferDiscrim(), dis, towrite, byteCount);

        return readStream(dis,

            (buf, offset, numBytes) -> towrite.put(buf, offset, numBytes)

            , byteCount);

    }

    private static final int TMP_BUF_SIZE = 8 * 1024;

    /**
     * write the entire byte buffer to the DataOutputStream and return the
     * number of bytes written.
     */
    public static int writeStream(final DataOutputStream dos, final ByteBuffer towrite) throws IOException {
        int numBytesWritten = 0;
        final int bytesToWrite = towrite.capacity();
        final int bytesToWriteAsLongs = bytesToWrite & (~0x07);
        for(int i = 0; i < bytesToWriteAsLongs; i += Long.BYTES) {
            dos.writeLong(towrite.getLong(i));
            numBytesWritten += Long.BYTES;
        }
        for(int i = numBytesWritten; i < bytesToWrite; i++) {
            dos.write(towrite.get(i));
            numBytesWritten++;
        }
        return numBytesWritten;
    }

    /**
     * write the entire byte buffer to the DataOutputStream and return the
     * number of bytes written.
     */
    public static long writeStream(final DataOutputStream dos, final MegaByteBuffer towrite) throws IOException {
        long numBytesWritten = 0;
        final long bytesToWrite = towrite.capacity();
        final long bytesToWriteAsLongs = bytesToWrite & (~0x07L);
        for(long i = 0; i < bytesToWriteAsLongs; i += Long.BYTES) {
            dos.writeLong(towrite.getLong(i));
            numBytesWritten += Long.BYTES;
        }
        for(long i = numBytesWritten; i < bytesToWrite; i++) {
            dos.write(towrite.get(i));
            numBytesWritten++;
        }
        return numBytesWritten;
    }

    /**
     * This is an absolute put method for a portion of a byte array - something
     * missing from the ByteBuffer API.
     */
    public static void put(final ByteBuffer buf, final int bytePosition, final byte[] bytes, final int startFromBuf, final int byteCount) {
        final ByteBuffer bb = ByteBuffer.wrap(bytes);
        final long endByte = bytePosition + (long)byteCount;
        int j = 0;
        int i;
        for(i = bytePosition; i < (endByte - Long.BYTES); i += Long.BYTES, j += Long.BYTES) {
            final long val = bb.getLong(j);
            buf.putLong(i, val);
        }

        // we need to finish the remainder
        for(; i < endByte; i++, j++)
            buf.put(i, bytes[j]);
    }

    // public static InputStream makeInputStream(final ByteBuffer bb) {
    //
    // }

    @FunctionalInterface
    private static interface BufferPut {
        void put(byte[] buf, int offset, int numBytes);
    }

    private static long readStream(final DataInputStream is, final BufferPut towrite, final long byteCount) throws IOException {
        final byte[] buf = new byte[TMP_BUF_SIZE];
        long totalBytesRead = 0;
        int bytesRead = 0;
        for(long i = 0; i < byteCount; i += bytesRead) {
            final int bytesToRead = ((byteCount - i) > TMP_BUF_SIZE) ? TMP_BUF_SIZE : (int)(byteCount - i);
            bytesRead = is.read(buf, 0, bytesToRead);

            if(bytesRead == -1) return totalBytesRead;

            towrite.put(buf, 0, bytesRead);
            totalBytesRead += bytesRead;

        }

        return totalBytesRead;
    }

}
