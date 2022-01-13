package net.dempsy.util.io;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import net.dempsy.util.BinaryUtils;
import net.dempsy.util.HexStringUtil;

public class TestMegaByteBuffer {

    @Test
    public void testOverlapGet() throws Throwable {
        final boolean origForceLongImpl = MegaByteBuffer.forceLongImpl;
        try {
            MegaByteBuffer.Impl.setBufferSizeConstants(3, 0x0000000000000007L, 8);
            MegaByteBuffer.forceLongImpl = true;

            final MegaByteBuffer buf = MegaByteBuffer.allocateDirect(20);

            for(byte i = 0; i < 20; i++)
                buf.put(i, i);

            short slastVal = 0x0000;
            for(int i = 0; i < (20 - BinaryUtils.SIZEOF_SHORT); i++) {
                final short cur = buf.getShort(i);
                slastVal = (short)((slastVal << 8) + (i + BinaryUtils.SIZEOF_SHORT - 1));
                assertEquals(slastVal, cur);
            }

            int ilastVal = 0x00000102;
            for(int i = 0; i < (20 - BinaryUtils.SIZEOF_INT); i++) {
                final int cur = buf.getInt(i);
                ilastVal = (ilastVal << 8) + (i + BinaryUtils.SIZEOF_INT - 1);
                assertEquals(ilastVal, cur);
            }

            long llastVal = 0x0000010203040506L;
            for(int i = 0; i < (10 - BinaryUtils.SIZEOF_LONG); i++) {
                final long cur = buf.getLong(i);
                llastVal = (llastVal << 8) + (i + BinaryUtils.SIZEOF_LONG - 1);
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
        } finally {
            MegaByteBuffer.forceLongImpl = origForceLongImpl;
            MegaByteBuffer.Impl.setBufferSizeConstants(30, 0x000000003fffffffL, (Integer.MAX_VALUE >> 1) + 1);
        }
    }

    @Test
    public void testOverlapPutInt() throws Throwable {
        final boolean origForceLongImpl = MegaByteBuffer.forceLongImpl;
        try {
            MegaByteBuffer.Impl.setBufferSizeConstants(3, 0x0000000000000007L, 8);
            MegaByteBuffer.forceLongImpl = true;

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
        } finally {
            MegaByteBuffer.forceLongImpl = origForceLongImpl;
            MegaByteBuffer.Impl.setBufferSizeConstants(30, 0x000000003fffffffL, (Integer.MAX_VALUE >> 1) + 1);
        }
    }

    @Test
    public void testOverlapPutLong() throws Throwable {
        final boolean origForceLongImpl = MegaByteBuffer.forceLongImpl;
        try {
            MegaByteBuffer.Impl.setBufferSizeConstants(3, 0x0000000000000007L, 8);
            MegaByteBuffer.forceLongImpl = true;

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
        } finally {
            MegaByteBuffer.forceLongImpl = origForceLongImpl;
            MegaByteBuffer.Impl.setBufferSizeConstants(30, 0x000000003fffffffL, (Integer.MAX_VALUE >> 1) + 1);
        }
    }

    @Test
    public void testStreamWrite() throws Throwable {
        final boolean origForceLongImpl = MegaByteBuffer.forceLongImpl;
        try {
            MegaByteBuffer.Impl.setBufferSizeConstants(3, 0x0000000000000007L, 8);
            MegaByteBuffer.forceLongImpl = true;

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
        } finally {
            MegaByteBuffer.forceLongImpl = origForceLongImpl;
            MegaByteBuffer.Impl.setBufferSizeConstants(30, 0x000000003fffffffL, (Integer.MAX_VALUE >> 1) + 1);
        }

    }

    @Test
    public void testLargeBuffer() throws Exception {
        final long fileSize = 0x000000010000000fL;

        final File dstFile = new File("target/tmp.dat");
        FileUtils.deleteQuietly(dstFile);
        dstFile.deleteOnExit();

        try(final RandomAccessFile f = new RandomAccessFile(dstFile, "rws");) {
            final FileChannel channel = f.getChannel();
            channel.truncate(fileSize);

            final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, fileSize, channel, FileChannel.MapMode.READ_WRITE);

            mbb.getLong(0x0000000100000004L);
        }
    }

    @Test
    public void testLargeBufferMmapFileRandomData() throws Exception {
        final long fileSize = 0x000000010000000fL;

        final File dstFile = new File("target/tmp.dat");
        FileUtils.deleteQuietly(dstFile);
        dstFile.deleteOnExit();

        /**
         * First make the file
         */
        {
            System.out.println("Building file ...");
            final Random rand = new Random();
            try(final RandomAccessFile f = new RandomAccessFile(dstFile, "rws");) {
                final FileChannel channel = f.getChannel();
                channel.truncate(fileSize);

                final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, fileSize, channel, FileChannel.MapMode.READ_WRITE);

                mbb.streamOfByteBuffers()
                    .forEach(bb -> {
                        while(bb.hasRemaining())
                            bb.put((byte)rand.nextInt());
                    });
            }
            System.out.println("DONE!");
        }

        final String md5UsingStream;
        {
            System.out.println("MD5 using stream ...");
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            try(DigestInputStream dis = new DigestInputStream(new BufferedInputStream(new FileInputStream(dstFile)), md);) {
                while(dis.read() >= 0);
            }
            final byte[] hash = md.digest();
            md5UsingStream = HexStringUtil.bytesToHex(hash);
            System.out.println("Done! MD5 is " + md5UsingStream);
        }
        final String md5UsingMbb;
        {
            System.out.println("MD5 using MegaByteBuffer ...");
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            try(final RandomAccessFile f = new RandomAccessFile(dstFile, "r");) {
                final FileChannel channel = f.getChannel();

                final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, fileSize, channel, FileChannel.MapMode.READ_ONLY);

                mbb.streamOfByteBuffers()
                    .forEach(bb -> md.update(bb));
            }
            final byte[] hash = md.digest();
            md5UsingMbb = HexStringUtil.bytesToHex(hash);
            System.out.println("Done! MD5 is " + md5UsingMbb);
        }

        assertEquals(md5UsingStream, md5UsingMbb);
    }

    @Test
    public void testStreamWriteAligned() throws Throwable {
        final boolean origForceLongImpl = MegaByteBuffer.forceLongImpl;
        try {
            MegaByteBuffer.Impl.setBufferSizeConstants(3, 0x0000000000000007L, 8);
            MegaByteBuffer.forceLongImpl = true;

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
        } finally {
            MegaByteBuffer.forceLongImpl = origForceLongImpl;
            MegaByteBuffer.Impl.setBufferSizeConstants(30, 0x000000003fffffffL, (Integer.MAX_VALUE >> 1) + 1);
        }

    }

}
