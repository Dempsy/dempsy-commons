package net.dempsy.util.io;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Test;

import net.dempsy.util.MutableInt;
import net.dempsy.utils.test.CloseableRule;

public class TestMegaByteBufferInputStream {

    public static File srcFile;
    public static final long FILE_SIZE = 0x000000008000000fL;
    public static final long seed = 8682522807148012L ^ System.nanoTime();

    public static MegaByteBuffer underTest;

    @ClassRule public static CloseableRule setupFile = new CloseableRule(() -> {
        try {

            srcFile = new File("target/tmp.dat");
            FileUtils.deleteQuietly(srcFile);
            srcFile.deleteOnExit();

            // First make the file
            {
                System.out.println("Building file ...");
                final Random rand = new Random(seed);
                try(final RandomAccessFile f = new RandomAccessFile(srcFile, "rws");) {
                    final FileChannel channel = f.getChannel();
                    channel.truncate(FILE_SIZE);

                    final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, FILE_SIZE, channel, FileChannel.MapMode.READ_WRITE);

                    final int numbb = (int)mbb.streamOfByteBuffers().count();
                    System.out.println("Number of byte buffers: " + numbb);

                    final MutableInt curPos = new MutableInt(0L);
                    mbb.streamOfByteBuffers().forEach(bb -> {
                        while(bb.hasRemaining()) {
                            final byte val = (byte)rand.nextInt();
                            bb.put(val);
                            curPos.val++;
                        }
                    });

                    mbb.force();
                }
                System.out.println("DONE!");
            }

            final RandomAccessFile f = new RandomAccessFile(srcFile, "r");
            final FileChannel channel = f.getChannel();
            underTest = MegaByteBuffer.allocateMaped(0L, FILE_SIZE, channel, FileChannel.MapMode.READ_ONLY);

            return () -> {
                f.close();
                FileUtils.deleteQuietly(srcFile);
            };
        } catch(final Exception e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void testExactlyTheSame() throws IOException {

        final File dstFile = new File("target/tmpdst.dat");
        FileUtils.deleteQuietly(dstFile);
        dstFile.deleteOnExit();

        try(final RandomAccessFile raf = new RandomAccessFile(srcFile, "r");) {
            final FileChannel channel = raf.getChannel();
            final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, srcFile.length(), channel, FileChannel.MapMode.READ_ONLY);

            try(var is = new MegaByteBufferInputStream(mbb);
                var os = new BufferedOutputStream(new FileOutputStream(dstFile));) {
                IOUtils.copy(is, os);
            }
        }

        // now compare the files byte by byte
        final byte[] src = new byte[1024 * 1024];
        final byte[] dst = new byte[1024 * 1024];
        try(InputStream srcIs = new BufferedInputStream(new FileInputStream(srcFile));
            InputStream dstIs = new BufferedInputStream(new FileInputStream(dstFile));) {

            while(true) {
                final int rs = srcIs.read(src);
                final int rd = dstIs.read(dst);

                assertEquals(rs, rd);
                assertEquals(0, Arrays.compare(src, dst));
                if(rs < 0)
                    break;
            }
        }
    }

}
