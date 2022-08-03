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
import java.nio.ByteBuffer;

import org.junit.Test;

public class TestByteBufferHelper {

    @Test
    public void testStreamWrite() throws Exception {
        final ByteBuffer buf = ByteBuffer.allocateDirect(20);
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

    @Test
    public void testStreamWriteAligned() throws Exception {
        final ByteBuffer buf = ByteBuffer.allocateDirect(24);
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
