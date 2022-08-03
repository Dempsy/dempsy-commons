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

import static net.dempsy.util.BinaryUtils.BYTE_MASK;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buf;

    public ByteBufferInputStream(final ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public int read() throws IOException {
        if(!buf.hasRemaining())
            return -1;
        return buf.get() & BYTE_MASK;
    }

    @Override
    public int read(final byte[] bytes, final int off, int len)
        throws IOException {
        if(!buf.hasRemaining())
            return -1;

        len = Math.min(len, buf.remaining());
        buf.get(bytes, off, len);
        return len;
    }

}
