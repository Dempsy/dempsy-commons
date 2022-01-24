package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzFileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"gz"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}

    @Override
    protected InputStream wrap(final InputStream is) throws IOException {
        return new GZIPInputStream(is);
    }

    @Override
    protected OutputStream wrap(final OutputStream os) throws IOException {
        return new GZIPOutputStream(os);
    }
}
