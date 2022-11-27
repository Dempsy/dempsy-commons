package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

public class ZCompressedFileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"Z"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    protected InputStream wrap(final Path path, final InputStream is) throws IOException {
        final InputStream ret;
        try {
            ret = new ZCompressorInputStream(is);
        } catch(final IllegalArgumentException | ArrayIndexOutOfBoundsException iae) {
            // Apache's ZCompressorInputStream throws an IllegalArgumentException
            // if the stream isn't actually Z compressed. This should be an IOException
            // for us to handle correctly.
            throw new IOException("The stream doesn't appear to be Z compressed.", iae);
        }
        return ret;
    }

    @Override
    protected OutputStream wrap(final Path path, final OutputStream os) throws IOException {
        return new ZstdCompressorOutputStream(os);
    }

}
