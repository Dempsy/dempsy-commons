package net.dempsy.vfs.zip;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import net.dempsy.vfs.CopiedArchiveFileSystem;
import net.dempsy.vfs.internal.LocalArchiveInputStream.FileDetails;

public class CopyZipFileSystem extends CopiedArchiveFileSystem {

    public final static String[] SCHEMES = {"zip","jar"};
    public final static String ENC = "!";

    public CopyZipFileSystem() {
        super(ENC);
    }

    @Override
    public LinkedHashMap<String, FileDetails> extract(final String scheme, final URI archiveUri, final File destinationDirectory) throws IOException {
        return copyArchiveInputStream(archiveUri, makeAis(scheme, archiveUri, new BufferedInputStream(vfs.toPath(archiveUri).read())), destinationDirectory);
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    private ArchiveInputStream makeAis(final String scheme, final URI archiveUri, final InputStream inner) throws IOException {
        return new ZipArchiveInputStream(inner);
    }
}
