package net.dempsy.vfs.xz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import net.dempsy.vfs.CompressedFileSystem;
import net.dempsy.vfs.Path;

public class XzFileSystem extends CompressedFileSystem {

    private final static String[] SCHEMES = {"xz"};

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    protected InputStream wrap(final Path path, final InputStream is) throws IOException {
        return new XZCompressorInputStream(is);
    }

    @Override
    protected OutputStream wrap(final Path path, final OutputStream os) throws IOException {
        return new XZCompressorOutputStream(os);
    }
}
