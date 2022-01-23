package net.dempsy.vfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class TarFileSystem extends EncArchiveFileSystem {

    private final static String[] SCHEMES = {"tar"};
    private final static String ENC = "!";

    public TarFileSystem() {
        super(SCHEMES[0], ENC);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(final InputStream inner) throws IOException {
        return new TarArchiveInputStream(new BufferedInputStream(inner));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}

}
