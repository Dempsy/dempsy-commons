package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public class Bz2FileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"bz2","bz"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}

    @Override
    protected InputStream wrap(final InputStream is) throws IOException {
        return new BZip2CompressorInputStream(is);
    }

    @Override
    protected OutputStream wrap(final OutputStream os) throws IOException {
        return new BZip2CompressorOutputStream(os);
    }

}
