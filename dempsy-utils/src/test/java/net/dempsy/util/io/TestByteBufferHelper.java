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
