package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

public class XzFileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"xz"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}

    @Override
    protected InputStream wrap(final InputStream is) throws IOException {
        return new XZCompressorInputStream(is);
    }

    @Override
    protected OutputStream wrap(final OutputStream os) throws IOException {
        return new XZCompressorOutputStream(os);
    }
}
