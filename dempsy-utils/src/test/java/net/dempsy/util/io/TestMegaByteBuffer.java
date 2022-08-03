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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import org.junit.Test;

import net.dempsy.util.QuietCloseable;

public class TestMegaByteBuffer {

    public static QuietCloseable setConstants(final boolean forceLongImpl, final long shift, final long mask, final int maxBufferSize) {
        MegaByteBuffer.Impl.setBufferSizeConstants(shift, mask, maxBufferSize);
        final boolean originalForceLongImpl = MegaByteBuffer.forceLongImpl;
        MegaByteBuffer.forceLongImpl = forceLongImpl;

        return () -> {
            MegaByteBuffer.Impl.resetBufferSizeConstantsToDefaults();
            MegaByteBuffer.forceLongImpl = originalForceLongImpl;
        };
    }

    @Test
    public void testReading() throws Exception {
        try(var qc = setConstants(true, 7, 0x000000000000007fL, 128);) {
            final int sz = (128 * 5) + 43;
            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(sz);

            for(int i = 0; i < sz; i++)
                buf.put(i, (byte)(i & 0xff));

            final byte[] d = new byte[8192];
            // this is a test that reproduced a BufferUnderflowException.
            buf.getBytes(sz - 30, d, 0, 30);

            for(int i = 0; i < 30; i++)
                assertEquals((byte)((sz - 30 + i) & 0xff), d[i]);
        }
    }

    @Test
    public void testOverlapGet() throws Exception {
        try(var qc = setConstants(true, 3, 0x0000000000000007L, 8);) {

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(20);

            for(byte i = 0; i < 20; i++)
                buf.put(i, i);

            short slastVal = 0x0000;
            for(int i = 0; i < (20 - Short.BYTES); i++) {
                final short cur = buf.getShort(i);
                slastVal = (short)((slastVal << 8) + (i + Short.BYTES - 1));
                assertEquals(slastVal, cur);
            }

            int ilastVal = 0x00000102;
            for(int i = 0; i < (20 - Integer.BYTES); i++) {
                final int cur = buf.getInt(i);
                ilastVal = (ilastVal << 8) + (i + Integer.BYTES - 1);
                assertEquals(ilastVal, cur);
            }

            long llastVal = 0x0000010203040506L;
            for(int i = 0; i < (10 - Long.BYTES); i++) {
                final long cur = buf.getLong(i);
                llastVal = (llastVal << 8) + (i + Long.BYTES - 1);
                assertEquals(llastVal, cur);
            }

            final byte[] barray = buf.getBytes(2, 10);
            int b = 2;
            int index = 0;
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
            assertEquals((byte)b++, barray[index++]);
        }
    }

    @Test
    public void testOverlapPutInt() throws Exception {
        try(var qc = setConstants(true, 3, 0x0000000000000007L, 8);) {

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(20);

            final int toPut = 0x01020304;
            buf.putInt(2, toPut);
            int b = 1;
            int index = 0;
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            buf.putInt(6, toPut);
            b = 1;
            index = 0;
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            b = 1;
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)(b++), buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
            assertEquals((byte)0x00, buf.get(index++));
        }
    }

    @Test
    public void testOverlapPutLong() throws Exception {
        try(var qc = setConstants(true, 3, 0x0000000000000007L, 8);) {

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(20);

            final long toPut = 0x0102030405060708L;
            buf.putLong(2, toPut);
            buf.putLong(10, toPut);
            assertEquals((byte)0x00, buf.get(0));
            assertEquals((byte)0x00, buf.get(1));
            assertEquals((byte)0x01, buf.get(2));
            assertEquals((byte)0x02, buf.get(3));
            assertEquals((byte)0x03, buf.get(4));
            assertEquals((byte)0x04, buf.get(5));
            assertEquals((byte)0x05, buf.get(6));
            assertEquals((byte)0x06, buf.get(7));
            assertEquals((byte)0x07, buf.get(8));
            assertEquals((byte)0x08, buf.get(9));
            assertEquals((byte)0x01, buf.get(10));
            assertEquals((byte)0x02, buf.get(11));
            assertEquals((byte)0x03, buf.get(12));
            assertEquals((byte)0x04, buf.get(13));
            assertEquals((byte)0x05, buf.get(14));
            assertEquals((byte)0x06, buf.get(15));
            assertEquals((byte)0x07, buf.get(16));
            assertEquals((byte)0x08, buf.get(17));
            assertEquals((byte)0x00, buf.get(18));
            assertEquals((byte)0x00, buf.get(19));
        }
    }

    @Test
    public void testStreamWrite() throws Exception {
        try(var qc = setConstants(true, 3, 0x0000000000000007L, 8);) {

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(20);
            for(byte i = 0; i < 20; i++)
                buf.put(i, i);

            // make the output stream.
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
            final DataOutputStream dos = new DataOutputStream(bos);
            ByteBufferHelper.writeStream(dos, buf);
            final byte[] res = bos.toByteArray();
            for(int i = 0; i < 20; i++)
                assertEquals((byte)i, res[i]);
        }

    }

    @Test
    public void testStreamWriteAligned() throws Exception {
        try(var qc = setConstants(true, 3, 0x0000000000000007L, 8);) {

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(24);
            for(byte i = 0; i < 24; i++)
                buf.put(i, i);

            // make the output stream.
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(24);
            final DataOutputStream dos = new DataOutputStream(bos);
            ByteBufferHelper.writeStream(dos, buf);
            final byte[] res = bos.toByteArray();
            for(int i = 0; i < 24; i++)
                assertEquals((byte)i, res[i]);
        }
    }

}
