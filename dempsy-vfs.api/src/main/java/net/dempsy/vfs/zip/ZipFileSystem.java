package net.dempsy.vfs.zip;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import net.dempsy.vfs.EncArchiveFileSystem;

public class ZipFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"zip","jar"};
    public final static String ENC = "!";

    public ZipFileSystem() {
        super(ENC);
    }

    @Override
    protected ArchiveInputStream createArchiveInputStream(final String scheme, final InputStream inner) throws IOException {
        return new ZipArchiveInputStream(inner);
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }
}
