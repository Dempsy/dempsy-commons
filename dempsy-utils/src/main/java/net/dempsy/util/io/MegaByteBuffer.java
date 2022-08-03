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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A normal byte buffer can only hold up to 2 Gig of data since the allocate and
 * allocatDirect methods take an int as the parameter. This class will take a
 * long and manage an array of ByteBuffers.
 * <P>
 * There is an attempt to be somewhat smart about the selection of the underlying
 * implementation. If a normal ByteBuffer would suffice (that is, the size passed
 * into either allocate method is less than Integer.MAX_VALUE) then the implementation
 * will simply wrap a normal ByteBuffer. If it requires special handing (the size
 * if greater than Integer.MAX_VALUE) then the implementation returned will an
 * array of ByteBuffers.
 */
public abstract class MegaByteBuffer {
    /**
     * This is primarily used for testing. When set to true the implementation
     * provided by the allocate methods will always be the one that manages an
     * array of ByteBuffers and never simply a ByteBuffer proxy, even with the
     * size would allow it.
     */
    static boolean forceLongImpl = false;

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public static MegaByteBuffer allocate(final long size) {
        if(forceLongImpl)
            return new Impl(false, size);
        else
            return size > Integer.MAX_VALUE ? new Impl(false, size) : new ProxyByteBuffer(false, size);
    }

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public static MegaByteBuffer allocateDirect(final long size) {
        if(forceLongImpl)
            return new Impl(true, size);
        else
            return size > Integer.MAX_VALUE ? new Impl(true, size) : new ProxyByteBuffer(true, size);
    }

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public static MegaByteBuffer allocateMaped(final long position, final long size, final FileChannel channel, final MapMode mode)
        throws IOException {
        if(forceLongImpl)
            return new Impl(channel, position, size, mode);
        else
            return size > Integer.MAX_VALUE ? new Impl(channel, position, size, mode) : new ProxyByteBuffer(channel, position, size, mode);
    }

    public static MegaByteBuffer wrap(final ByteBuffer b) {
        return forceLongImpl ? new Impl(b) : new ProxyByteBuffer(b);
    }

    public static MegaByteBuffer wrap(final byte[] data) {
        return wrap(ByteBuffer.wrap(data));
    }

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract int getInt(final long bytePosition);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract float getFloat(final long bytePosition);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract double getDouble(final long bytePosition);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract byte get(final long bytePosition);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract long getLong(final long bytePosition);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract short getShort(final long bytePosition);

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
    public abstract byte[] getBytes(final long index, final byte[] buffer);

    /**
     * This is an <em>absolute</em> get from the ByteBuffer.
     *
     * @param from
     *     is the ByteBuffer to retrieve the bytes from
     * @param index
     *     is where to retrieve the bytes from
     * @param buffer
     *     is the byte array to fill.
     * @param offset
     *     is the offset into the destination array to start writing to
     * @param length
     *     is the number of bytes to write into the destination array
     * @return
     */
    public abstract byte[] getBytes(final long index, final byte[] buffer, int offset, int length);

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
    public byte[] getBytes(final long index, final int size) {
        final byte[] ret = new byte[size];
        return getBytes(index, ret);
    }

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void putInt(final long bytePosition, final int toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void putShort(final long bytePosition, final short toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void putFloat(final long bytePosition, final float toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void putDouble(final long bytePosition, final double toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void putLong(final long bytePosition, final long toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract void put(final long bytePosition, final byte toPut);

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Notice that the method deals in
     * 'long' where the ByteBuffer version deals with 'int'.
     */
    public abstract long capacity();

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Not every MegaByteBuffer supports
     * this method. Only MegaByteBuffer backed by a simple ByteBuffer that also
     * supports this method.
     */
    public abstract boolean hasArray();

    /**
     * This method is the analog for the ByteBuffer method of the same name. See
     * that javadoc for a detailed description. Not every MegaByteBuffer supports
     * this method. Only MegaByteBuffer backed by a simple ByteBuffer that also
     * supports this method. You should check it with hasArray().
     */
    public abstract byte[] array();

    /**
     * This method is ALMOST an analog for the ByteBuffer method of the same
     * name. See that javadoc for a detailed description. Notice that the method
     * deals in 'long' where the ByteBuffer version deals with 'int'. The
     * different here is that this is an ABSOLUTE put method.
     */
    public abstract void put(final long bytePosition, final byte[] bytes, final int startFromBuf, final int byteCount);

    /**
     * You can use this method to tell whether or not the underlying ByteBuffer
     * is read only
     */
    public abstract boolean isReadOnly();

    /**
     * Call force on any underlying implementation that happens to be backed by
     * a MappedByteBuffer
     */
    public abstract void force();

    /**
     * Return the data as an ordered non-overlapping stream of ByteBuffers
     */
    public abstract Stream<ByteBuffer> streamOfByteBuffers();

    static final class ProxyByteBuffer extends MegaByteBuffer {
        private final ByteBuffer underlying;
        private final boolean isMemoryMapped;

        private ProxyByteBuffer(final boolean useDirectBuffer, final long size) {
            underlying = useDirectBuffer ? allocateDirectBuffer((int)size) : ByteBuffer.allocate((int)size);
            isMemoryMapped = false;
        }

        private ProxyByteBuffer(final FileChannel channel, final long position, final long size, final MapMode mode) throws IOException {
            underlying = channel.map(mode, position, size);
            isMemoryMapped = true;
        }

        private ProxyByteBuffer(final ByteBuffer b) {
            underlying = b;
            isMemoryMapped = (b instanceof MappedByteBuffer);
        }

        @Override
        public final byte[] array() {
            return underlying.array();
        }

        @Override
        public final long capacity() {
            return underlying.capacity();
        }

        @Override
        public final int getInt(final long bytePosition) {
            return underlying.getInt((int)bytePosition);
        }

        @Override
        public final float getFloat(final long bytePosition) {
            return underlying.getFloat((int)bytePosition);
        }

        @Override
        public final double getDouble(final long bytePosition) {
            return underlying.getDouble((int)bytePosition);
        }

        @Override
        public final short getShort(final long bytePosition) {
            return underlying.getShort((int)bytePosition);
        }

        @Override
        public final long getLong(final long bytePosition) {
            return underlying.getLong((int)bytePosition);
        }

        @Override
        public final byte get(final long bytePosition) {
            return underlying.get((int)bytePosition);
        }

        @Override
        public final boolean hasArray() {
            return underlying.hasArray();
        }

        @Override
        public final void put(final long bytePosition, final byte[] bytes, final int startFromBuf, final int byteCount) {
            final int oldPos = underlying.position();
            underlying.position((int)bytePosition);
            underlying.put(bytes, startFromBuf, byteCount);
            underlying.position(oldPos);
        }

        @Override
        public final void putInt(final long bytePosition, final int toPut) {
            underlying.putInt((int)bytePosition, toPut);
        }

        @Override
        public final void putShort(final long bytePosition, final short toPut) {
            underlying.putShort((int)bytePosition, toPut);
        }

        @Override
        public final void putFloat(final long bytePosition, final float toPut) {
            underlying.putFloat((int)bytePosition, toPut);
        }

        @Override
        public final void putDouble(final long bytePosition, final double toPut) {
            underlying.putDouble((int)bytePosition, toPut);
        }

        @Override
        public final void putLong(final long bytePosition, final long toPut) {
            underlying.putLong((int)bytePosition, toPut);
        }

        @Override
        public final void put(final long bytePosition, final byte toPut) {
            underlying.put((int)bytePosition, toPut);
        }

        @Override
        public final void force() {
            if(isMemoryMapped) ((MappedByteBuffer)underlying).force();
        }

        @Override
        public final boolean isReadOnly() {
            return underlying.isReadOnly();
        }

        @Override
        public final byte[] getBytes(final long index, final byte[] buffer) {
            return ByteBufferHelper.getBytes(underlying, (int)index, buffer);
        }

        @Override
        public final byte[] getBytes(final long index, final byte[] buffer, final int offset, final int length) {
            return ByteBufferHelper.getBytes(underlying, (int)index, buffer, offset, length);
        }

        @Override
        public Stream<ByteBuffer> streamOfByteBuffers() {
            return Stream.of(underlying);
        }
    }

    static final class Impl extends MegaByteBuffer {
        private static long shifting = 30;
        private static long mask = 0x000000003fffffffL;
        private static int maxIndividualBufSize = (Integer.MAX_VALUE >> 1) + 1;

        /**
         * The defaults should be left, this is only for testing.
         *
         * @param shift
         * @param mask
         * @param maxBufferSize
         */
        public static void setBufferSizeConstants(final long shift, final long mask, final int maxBufferSize) {
            MegaByteBuffer.Impl.shifting = shift;
            MegaByteBuffer.Impl.mask = mask;
            MegaByteBuffer.Impl.maxIndividualBufSize = maxBufferSize;

            // verify the constraints are consistent
            if(Integer.bitCount(maxBufferSize) != 1)
                throw new IllegalStateException("Max individual buffer size must be a non-zero power of 2");
            if(mask + 1 != maxBufferSize)
                throw new IllegalStateException("Max is inconsistent with maxBufferSize.");
            int shch = 0;
            for(shch = 0; (maxBufferSize >>> shch) != 1; shch++);
            if(shch != shift)
                throw new IllegalStateException("The shift is incorrect.");
        }

        public static void resetBufferSizeConstantsToDefaults() {
            shifting = 30;
            mask = 0x000000003fffffffL;
            maxIndividualBufSize = (Integer.MAX_VALUE >> 1) + 1;
        }

        final ByteBuffer[] byteBuffers;
        final long capacity;

        private Impl(final ByteBuffer b) {
            byteBuffers = new ByteBuffer[] {b};
            capacity = b.capacity();
        }

        private Impl(final FileChannel channel, final long position, final long size, final MapMode mode) throws IOException {
            capacity = size;

            final int numByteBuffers = (int)((capacity >> shifting) + 1L);
            byteBuffers = new ByteBuffer[numByteBuffers];
            long curpos = position;
            for(int i = 0; i < numByteBuffers - 1; i++) {
                byteBuffers[i] = channel.map(mode, curpos, maxIndividualBufSize);
                curpos += maxIndividualBufSize;
            }

            byteBuffers[numByteBuffers - 1] = channel.map(mode, curpos, (int)(capacity & mask));
        }

        private Impl(final boolean useDirectBuffer, final long size) {
            capacity = size;

            final int numByteBuffers = (int)((capacity >> shifting) + 1L);
            byteBuffers = new ByteBuffer[numByteBuffers];
            for(int i = 0; i < numByteBuffers - 1; i++) {
                // byteBuffers[i] = useDirectBuffer ?
                // ByteBuffer.allocateDirect(maxIndividualBufSize) :
                // ByteBuffer.allocate(maxIndividualBufSize);
                byteBuffers[i] = useDirectBuffer ? allocateDirectBuffer(maxIndividualBufSize) : ByteBuffer.allocate(maxIndividualBufSize);
            }

            byteBuffers[numByteBuffers - 1] = useDirectBuffer ? allocateDirectBuffer((int)(capacity & mask))
                : ByteBuffer
                    .allocate((int)(capacity & mask));

        }

        @Override
        public final byte[] array() {
            throw new UnsupportedOperationException();
        }

        @Override
        public final long capacity() {
            return capacity;
        }

        @Override
        public final int getInt(final long bytePosition) {
            // if the position is within BinaryUtils.SIZEOF_INT from the end
            // then we need to do extra work.
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < Integer.BYTES) ? smartGetInt(bytePosition)
                : byteBuffers[(int)(bytePosition >> shifting)].getInt(subIndex);
        }

        @Override
        public final float getFloat(final long bytePosition) {
            // if the position is within BinaryUtils.SIZEOF_FLOAT from the end then
            // we need to do extra work.
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < Float.BYTES) ? smartGetFloat(bytePosition)
                : byteBuffers[(int)(bytePosition >> shifting)].getFloat(subIndex);
        }

        @Override
        public final double getDouble(final long bytePosition) {
            // if the position is within BinaryUtils.SIZEOF_FLOAT from the end then
            // we need to do extra work.
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < Double.BYTES) ? smartGetDouble(bytePosition)
                : byteBuffers[(int)(bytePosition >> shifting)].getDouble(subIndex);
        }

        @Override
        public final short getShort(final long bytePosition) {
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < Short.BYTES) ? smartGetShort(bytePosition)
                : byteBuffers[(int)(bytePosition >> shifting)].getShort(subIndex);
        }

        @Override
        public final long getLong(final long bytePosition) {
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < Long.BYTES) ? smartGetLong(bytePosition)
                : byteBuffers[(int)(bytePosition >> shifting)].getLong((int)(bytePosition & mask));
        }

        @Override
        public final byte get(final long bytePosition) {
            return byteBuffers[(int)(bytePosition >> shifting)].get((int)(bytePosition & mask));
        }

        @Override
        public final boolean hasArray() {
            return false;
        }

        @Override
        public final void put(final long index, final byte b) {
            byteBuffers[(int)(index >> shifting)].put((int)(index & mask), b);
        }

        @Override
        public final void put(final long bytePosition, final byte[] bytes, final int startFromBuf, final int byteCount) {
            final ByteBuffer bb = ByteBuffer.wrap(bytes);
            final long endByte = bytePosition + byteCount;
            int j = 0;
            long i;
            for(i = bytePosition; i < (endByte - Long.BYTES); i += Long.BYTES, j += Long.BYTES) {
                final long val = bb.getLong(j);
                putLong(i, val);
            }

            // we need to finish the remainder
            for(; i < endByte; i++, j++)
                put(i, bytes[j]);
        }

        @Override
        public final void putInt(final long bytePosition, final int toPut) {
            final int subIndex = (int)(bytePosition & mask);
            if(maxIndividualBufSize - subIndex < Integer.BYTES)
                smartPutInt(bytePosition, toPut);
            else
                byteBuffers[(int)(bytePosition >> shifting)].putInt(subIndex, toPut);
        }

        @Override
        public final void putShort(final long bytePosition, final short toPut) {
            final int subIndex = (int)(bytePosition & mask);
            if(maxIndividualBufSize - subIndex < Short.BYTES)
                smartPutShort(bytePosition, toPut);
            else
                byteBuffers[(int)(bytePosition >> shifting)].putShort(subIndex, toPut);
        }

        @Override
        public final void putFloat(final long bytePosition, final float toPut) {
            final int subIndex = (int)(bytePosition & mask);
            if(maxIndividualBufSize - subIndex < Float.BYTES)
                smartPutFloat(bytePosition, toPut);
            else
                byteBuffers[(int)(bytePosition >> shifting)].putFloat(subIndex, toPut);
        }

        @Override
        public final void putDouble(final long bytePosition, final double toPut) {
            final int subIndex = (int)(bytePosition & mask);
            if(maxIndividualBufSize - subIndex < Double.BYTES)
                smartPutDouble(bytePosition, toPut);
            else
                byteBuffers[(int)(bytePosition >> shifting)].putDouble(subIndex, toPut);
        }

        @Override
        public final void putLong(final long bytePosition, final long toPut) {
            final int subIndex = (int)(bytePosition & mask);
            if(maxIndividualBufSize - subIndex < Long.BYTES)
                smartPutLong(bytePosition, toPut);
            else
                byteBuffers[(int)(bytePosition >> shifting)].putLong(subIndex, toPut);
        }

        @Override
        public final void force() {
            for(final ByteBuffer bb: byteBuffers) {
                if(bb instanceof MappedByteBuffer) ((MappedByteBuffer)bb).force();
            }
        }

        @Override
        public final boolean isReadOnly() {
            return byteBuffers.length > 0 ? byteBuffers[0].isReadOnly() : false;
        }

        @Override
        public final byte[] getBytes(final long bytePosition, final byte[] buffer) {
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < buffer.length) ? getFilledHolder(bytePosition, buffer.length, buffer)
                : ByteBufferHelper.getBytes(byteBuffers[(int)(bytePosition >> shifting)], subIndex, buffer);
        }

        @Override
        public final byte[] getBytes(final long bytePosition, final byte[] buffer, final int offset, final int length) {
            final int subIndex = (int)(bytePosition & mask);
            return (maxIndividualBufSize - subIndex < length) ? getFilledHolder(bytePosition, length, buffer, offset)
                // : ByteBufferHelper.getBytes(byteBuffers[(int)(bytePosition >> shifting)], subIndex, buffer);
                : ByteBufferHelper.getBytes(byteBuffers[(int)(bytePosition >> shifting)], subIndex, buffer, offset, length);

        }

        @Override
        public Stream<ByteBuffer> streamOfByteBuffers() {
            return Arrays.stream(byteBuffers);
        }

        private final byte[] getFilledHolder(final long bytePosition, final int numBytes, final byte[] pholder) {
            final byte[] holder = pholder == null ? new byte[numBytes] : pholder;
            final int curBuf = (int)(bytePosition >> shifting);
            ByteBuffer buf = byteBuffers[curBuf];
            final int subIndex = (int)(bytePosition & mask);
            int oldPosition = buf.position();
            buf.position(subIndex);
            final int numBytesCurBuf = maxIndividualBufSize - subIndex;
            buf.get(holder, 0, numBytesCurBuf);
            buf.position(oldPosition);
            buf = byteBuffers[curBuf + 1];
            oldPosition = buf.position();
            buf.position(0);
            buf.get(holder, numBytesCurBuf, numBytes - numBytesCurBuf);
            buf.position(oldPosition);
            return holder;
        }

        private final byte[] getFilledHolder(final long bytePosition, final int numBytes, final byte[] pholder, final int offset) {
            final byte[] holder = pholder == null ? new byte[numBytes + offset] : pholder;
            final int curBuf = (int)(bytePosition >> shifting);
            ByteBuffer buf = byteBuffers[curBuf];
            final int subIndex = (int)(bytePosition & mask);
            int oldPosition = buf.position();
            buf.position(subIndex);
            final int numBytesCurBuf = maxIndividualBufSize - subIndex;
            buf.get(holder, offset, numBytesCurBuf);
            buf.position(oldPosition);
            buf = byteBuffers[curBuf + 1];
            oldPosition = buf.position();
            buf.position(0);
            buf.get(holder, numBytesCurBuf + offset, numBytes - numBytesCurBuf);
            buf.position(oldPosition);
            return holder;
        }

        private final long smartGetLong(final long bytePosition) {
            final byte[] holder = getFilledHolder(bytePosition, Long.BYTES, null);
            long ret = ((holder[0]) & 0xffL) << 56;
            ret |= ((holder[1]) & 0xffL) << 48;
            ret |= ((holder[2]) & 0xffL) << 40;
            ret |= ((holder[3]) & 0xffL) << 32;
            ret |= ((holder[4]) & 0xffL) << 24;
            ret |= ((holder[5]) & 0xffL) << 16;
            ret |= ((holder[6]) & 0xffL) << 8;
            ret |= ((holder[7]) & 0xffL);
            return ret;
        }

        private final int smartGetInt(final long bytePosition) {
            final byte[] holder = getFilledHolder(bytePosition, Integer.BYTES, null);
            int ret = (holder[0] & 0xff) << 24;
            ret |= (holder[1] & 0xff) << 16;
            ret |= (holder[2] & 0xff) << 8;
            ret |= (holder[3] & 0xff);
            return ret;
        }

        private final float smartGetFloat(final long bytePosition) {
            return Float.intBitsToFloat(smartGetInt(bytePosition));
        }

        private final double smartGetDouble(final long bytePosition) {
            return Double.longBitsToDouble(smartGetLong(bytePosition));
        }

        private final short smartGetShort(final long bytePosition) {
            final byte[] holder = getFilledHolder(bytePosition, Short.BYTES, null);
            short ret = (short)((holder[0] & 0xff) << 8);
            ret |= (holder[1] & 0xff);
            return ret;
        }

        private final void smartPutLong(final long bytePosition, final long toPut) {
            final byte[] holder = new byte[Long.BYTES];
            holder[7] = (byte)(toPut & 0xff);
            holder[6] = (byte)((toPut >>> 8) & 0xff);
            holder[5] = (byte)((toPut >>> 16) & 0xff);
            holder[4] = (byte)((toPut >>> 24) & 0xff);
            holder[3] = (byte)((toPut >>> 32) & 0xff);
            holder[2] = (byte)((toPut >>> 40) & 0xff);
            holder[1] = (byte)((toPut >>> 48) & 0xff);
            holder[0] = (byte)((toPut >>> 56) & 0xff);
            for(long cur = 0; cur < Long.BYTES; cur++)
                put(cur + bytePosition, holder[(int)cur]);
        }

        private final void smartPutInt(final long bytePosition, final int toPut) {
            final byte[] holder = new byte[Integer.BYTES];
            holder[3] = (byte)(toPut & 0xff);
            holder[2] = (byte)((toPut >>> 8) & 0xff);
            holder[1] = (byte)((toPut >>> 16) & 0xff);
            holder[0] = (byte)((toPut >>> 24) & 0xff);
            for(long cur = 0; cur < Integer.BYTES; cur++)
                put(cur + bytePosition, holder[(int)cur]);
        }

        private final void smartPutShort(final long bytePosition, final short toPut) {
            final byte[] holder = new byte[Short.BYTES];
            holder[1] = (byte)(toPut & 0xff);
            holder[0] = (byte)((toPut >>> 8) & 0xff);
            for(long cur = 0; cur < Short.BYTES; cur++)
                put(cur + bytePosition, holder[(int)cur]);
        }

        private final void smartPutFloat(final long bytePosition, final float toPut) {
            smartPutInt(bytePosition, Float.floatToIntBits(toPut));
        }

        private final void smartPutDouble(final long bytePosition, final double toPut) {
            smartPutLong(bytePosition, Double.doubleToLongBits(toPut));
        }
    }

    /**
     * Allocate direct buffer. If
     *
     * @param size
     * @return
     */
    private static ByteBuffer allocateDirectBuffer(final int size) {
        return ByteBuffer.allocateDirect(size);
    }

}
