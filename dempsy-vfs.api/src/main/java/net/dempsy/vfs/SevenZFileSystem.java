package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

public class SevenZFileSystem extends EncArchiveFileSystem {

    public final static String[] SCHEMES = {"sevenz","rar"};
    public final static String ENC = "!";

    public SevenZFileSystem() {
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
