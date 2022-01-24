package net.dempsy.vfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

public class ZipFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"zip"};
    public final static String ENC = "XX";

    public ZipFileSystem() {
        super(ENC);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(final String scheme, final InputStream inner) throws IOException {
        return new ZipArchiveInputStream(new BufferedInputStream(inner));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}
}
