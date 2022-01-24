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
    public void close() throws IOException {}

    @Override
    protected InputStream wrap(final InputStream is) throws IOException {
        return new ZCompressorInputStream(is);
    }

    @Override
    protected OutputStream wrap(final OutputStream os) throws IOException {
        return new ZstdCompressorOutputStream(os);
    }

}
