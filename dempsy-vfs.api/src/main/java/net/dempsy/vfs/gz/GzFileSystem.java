package net.dempsy.vfs.gz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.dempsy.vfs.CompressedFileSystem;

public class GzFileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"gz"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    protected InputStream wrap(final InputStream is) throws IOException {
        return new GZIPInputStream(is);
    }

    @Override
    protected OutputStream wrap(final OutputStream os) throws IOException {
        return new GZIPOutputStream(os);
    }
}
