package net.dempsy.util.io;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;

import net.dempsy.util.HexStringUtil;
import net.dempsy.util.MutableInt;
import net.dempsy.utils.test.CloseableRule;

public class TestMegaByteBufferLargeRandomFile {

    private static final int HALF_TEST_BUF_LEN = 333;
    private static final int TEST_BUF_LEN = HALF_TEST_BUF_LEN * 2;
    public static final long STRADDLE_LONG_ADDRESS = 0x0000000100000000L - 5L;

    public static final long FILE_SIZE = 0x000000010000000fL;
    public static final long seed = 8682522807148012L ^ System.nanoTime();
    public static MegaByteBuffer underTest;
    public static long posToTest;
    public static byte[] expected = new byte[TEST_BUF_LEN];
    public static byte[] expectedLongStraddleBytes = new byte[Long.BYTES];
    public static long expectedLongStraddleLong;
    public static File dstFile;
    public static String md5OfSequence;

    @ClassRule public static CloseableRule setupFile = new CloseableRule(() -> {
        try {

            dstFile = new File("target/tmp.dat");
            FileUtils.deleteQuietly(dstFile);
            dstFile.deleteOnExit();

            // First make the file
            {
                System.out.println("Building file ...");
                final Random rand = new Random(seed);
                final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
                try(final RandomAccessFile f = new RandomAccessFile(dstFile, "rws");) {
                    final FileChannel channel = f.getChannel();
                    channel.truncate(FILE_SIZE);

                    final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, FILE_SIZE, channel, FileChannel.MapMode.READ_WRITE);

                    final int numbb = (int)mbb.streamOfByteBuffers().count();
                    System.out.println("Number of byte buffers: " + numbb);
                    final int mid = numbb / 2;
                    // we want the byte count that stradles mid <-> mid+1
                    final long bblen = mbb.streamOfByteBuffers().findFirst().get().capacity();
                    posToTest = (bblen * mid) - HALF_TEST_BUF_LEN;

                    final MutableInt curPos = new MutableInt(0L);
                    mbb.streamOfByteBuffers().forEach(bb -> {
                        while(bb.hasRemaining()) {
                            final byte val = (byte)rand.nextInt();
                            md.update(val);
                            bb.put(val);
                            // pick out the byte array that straddles a couple partitions to use
                            // as an expected value in test asserts
                            if(curPos.val >= posToTest && (curPos.val - posToTest) < TEST_BUF_LEN)
                                expected[(int)(curPos.val - posToTest)] = val;
                            // pick out a long that straddles a couple partitions to use
                            // as an expected value in test asserts
                            if(curPos.val >= STRADDLE_LONG_ADDRESS && (curPos.val - STRADDLE_LONG_ADDRESS) < Long.BYTES)
                                expectedLongStraddleBytes[(int)(curPos.val - STRADDLE_LONG_ADDRESS)] = val;
                            curPos.val++;
                        }
                    });

                    expectedLongStraddleLong = ByteBuffer.wrap(expectedLongStraddleBytes).getLong();

                    mbb.force();
                }
                final byte[] hash = md.digest();
                md5OfSequence = HexStringUtil.bytesToHex(hash);
                System.out.println("DONE! ... MD5 of sequence is " + md5OfSequence);
            }

            final RandomAccessFile f = new RandomAccessFile(dstFile, "r");
            final FileChannel channel = f.getChannel();
            underTest = MegaByteBuffer.allocateMaped(0L, FILE_SIZE, channel, FileChannel.MapMode.READ_ONLY);

            return () -> {
                f.close();
                FileUtils.deleteQuietly(dstFile);
            };
        } catch(final Exception e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void testExactlyTheSame() throws IOException {

        final String md5UsingStream;
        {
            System.out.println("MD5 using stream ...");
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            final long startTime = System.currentTimeMillis();
            try(DigestInputStream dis = new DigestInputStream(new MegaByteBufferInputStream(underTest), md);) {
                while(dis.read() >= 0);
            }
            final byte[] hash = md.digest();
            md5UsingStream = HexStringUtil.bytesToHex(hash);
            System.out.println("Done! MD5 is " + md5UsingStream + " " + (System.currentTimeMillis() - startTime) + " millis");
        }
        assertEquals(md5OfSequence, md5UsingStream);

        final String md5UsingStreamBr;
        {
            System.out.println("MD5 using stream with block reads ...");
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            final long startTime = System.currentTimeMillis();
            final byte[] data = new byte[1024 * 1024];
            try(DigestInputStream dis = new DigestInputStream(new MegaByteBufferInputStream(underTest), md);) {
                while(dis.read(data, 0, 8192) >= 0);
            }
            final byte[] hash = md.digest();
            md5UsingStreamBr = HexStringUtil.bytesToHex(hash);
            System.out.println("Done! MD5 is " + md5UsingStreamBr + " " + (System.currentTimeMillis() - startTime) + " millis");
        }
        assertEquals(md5OfSequence, md5UsingStreamBr);

        final String md5UsingMbb;
        {
            System.out.println("MD5 using MegaByteBuffer ...");
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            final long startTime = System.currentTimeMillis();
            underTest.streamOfByteBuffers()
                .forEach(bb -> md.update(bb));
            final byte[] hash = md.digest();
            md5UsingMbb = HexStringUtil.bytesToHex(hash);
            System.out.println("Done! MD5 is " + md5UsingMbb + " " + (System.currentTimeMillis() - startTime) + " millis");
        }

        assertEquals(md5UsingStream, md5UsingMbb);

        assertEquals(md5OfSequence, md5UsingMbb);
    }

    @Test
    public void testGetStraddledLong() throws Exception {
        assertEquals(expectedLongStraddleLong, underTest.getLong(STRADDLE_LONG_ADDRESS));
    }

    @Test
    public void testBulkReadNoDestOffset() throws IOException {
        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 2];
        underTest.getBytes(posToTest, vals);
        assertArrayEquals(expected, vals);
    }

    @Test
    public void testBulkReadWithDestOffset() throws IOException {

        // absolute bulk read with offset and length
        final int offset = HALF_TEST_BUF_LEN / 2;
        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 3];
        underTest.getBytes(posToTest, vals, offset, HALF_TEST_BUF_LEN * 2);

        for(int i = 0; i < HALF_TEST_BUF_LEN * 2; i++)
            assertEquals(expected[i], vals[i + offset]);
    }

    @Test
    public void testReadingFromVeryEnd() throws Exception {
        final byte[] data = new byte[8192];
        underTest.getBytes(FILE_SIZE - 10, data, 0, 10);
    }

    @Test
    public void testRelativeBulkReadNoDestOffset() throws IOException {
        // relative bulk read no offset fixed length
        final MegaByteBufferRelativeMetadata rmbb = new MegaByteBufferRelativeMetadata(underTest);
        // forward relative to pos using relative gets
        {
            while(rmbb.position() < posToTest - Long.BYTES) {
                rmbb.getLong();
            }
            while(rmbb.position() < posToTest)
                rmbb.get();
        }

        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 2];
        rmbb.get(vals);
        assertArrayEquals(expected, vals);
    }

    @Test
    public void testRelativeBulkReadWithDestOffset() throws IOException {
        // relative bulk read with offset fixed length
        final MegaByteBufferRelativeMetadata rmbb = new MegaByteBufferRelativeMetadata(underTest);
        // forward relative to pos using relative gets
        {
            while(rmbb.position() < posToTest - Long.BYTES) {
                rmbb.getLong();
            }
            while(rmbb.position() < posToTest)
                rmbb.get();
        }

        final int offset = HALF_TEST_BUF_LEN / 2;
        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 3];
        rmbb.get(vals, offset, HALF_TEST_BUF_LEN * 2);

        for(int i = 0; i < HALF_TEST_BUF_LEN * 2; i++)
            assertEquals(expected[i], vals[i + offset]);
    }

    @Test
    public void testRelativeBulkReadWithOffsetNoDestOffset() throws IOException {

        // relative bulk read reader offset, no dest offset fixed length
        final MegaByteBufferRelativeMetadata rmbb = new MegaByteBufferRelativeMetadata(underTest, posToTest - Long.BYTES);
        rmbb.getLong();

        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 2];
        rmbb.get(vals);
        assertArrayEquals(expected, vals);
    }

    @Test
    public void testRelativeBulkReadWithOffsetWithDestOffset() throws IOException {
        // relative bulk read reader offset with dest offset fixed length
        final MegaByteBufferRelativeMetadata rmbb = new MegaByteBufferRelativeMetadata(underTest, posToTest - Long.BYTES);
        rmbb.getLong();

        final int offset = HALF_TEST_BUF_LEN / 2;
        final byte[] vals = new byte[HALF_TEST_BUF_LEN * 3];
        rmbb.get(vals, offset, HALF_TEST_BUF_LEN * 2);

        for(int i = 0; i < HALF_TEST_BUF_LEN * 2; i++)
            assertEquals(expected[i], vals[i + offset]);
    }
}
